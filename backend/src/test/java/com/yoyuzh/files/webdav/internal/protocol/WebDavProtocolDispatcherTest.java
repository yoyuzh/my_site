package com.yoyuzh.files.webdav.internal.protocol;

import com.yoyuzh.files.webdav.internal.application.WebDavPrincipal;
import com.yoyuzh.files.webdav.internal.application.WebDavReadResult;
import com.yoyuzh.files.webdav.internal.application.WebDavResourceStore;
import com.yoyuzh.files.webdav.internal.application.WebDavStoredResource;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

class WebDavProtocolDispatcherTest {

    private final WebDavPrincipal principal = new WebDavPrincipal(7L, "alice", 1024L, 512L);

    @Test
    void optionsShouldAdvertiseLevelThreeWebDavMethods() throws Exception {
        WebDavProtocolDispatcher dispatcher = new WebDavProtocolDispatcher(new FakeStore());
        MockHttpServletResponse response = new MockHttpServletResponse();

        dispatcher.dispatch(principal, request("OPTIONS", "/dav"), response);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getHeader("DAV")).isEqualTo("1,2,3");
        assertThat(response.getHeader("MS-Author-Via")).isEqualTo("DAV");
        assertThat(response.getHeader("Allow")).contains("PROPFIND", "COPY", "LOCK", "UNLOCK");
        assertThat(response.getHeader("Allow")).doesNotContain("PROPPATCH");
    }

    @Test
    void copyShouldValidateDestinationAndDelegateToStore() throws Exception {
        FakeStore store = new FakeStore();
        store.resources.add(resource("/Docs/a.txt", false));
        WebDavProtocolDispatcher dispatcher = new WebDavProtocolDispatcher(store);
        MockHttpServletRequest request = request("COPY", "/dav/Docs/a.txt");
        request.addHeader("Destination", "http://localhost/dav/Archive/a.txt");
        request.addHeader("Overwrite", "F");
        MockHttpServletResponse response = new MockHttpServletResponse();

        dispatcher.dispatch(principal, request, response);

        assertThat(response.getStatus()).isEqualTo(201);
        assertThat(store.copiedFrom).isEqualTo("/Docs/a.txt");
        assertThat(store.copiedTo).isEqualTo("/Archive/a.txt");
        assertThat(store.copiedOverwrite).isFalse();
    }

    @Test
    void shouldRejectParentTraversalPaths() throws Exception {
        WebDavProtocolDispatcher dispatcher = new WebDavProtocolDispatcher(new FakeStore());
        MockHttpServletResponse response = new MockHttpServletResponse();

        dispatcher.dispatch(principal, request("PROPFIND", "/dav/../secret"), response);

        assertThat(response.getStatus()).isEqualTo(400);
    }

    @Test
    void shouldRejectDestinationWithDifferentPort() throws Exception {
        FakeStore store = new FakeStore();
        store.resources.add(resource("/Docs/a.txt", false));
        WebDavProtocolDispatcher dispatcher = new WebDavProtocolDispatcher(store);
        MockHttpServletRequest request = request("COPY", "/dav/Docs/a.txt");
        request.addHeader("Destination", "http://localhost:8080/dav/Archive/a.txt");
        MockHttpServletResponse response = new MockHttpServletResponse();

        dispatcher.dispatch(principal, request, response);

        assertThat(response.getStatus()).isEqualTo(403);
    }

    @Test
    void propfindShouldRejectUnsupportedInfinityDepth() throws Exception {
        FakeStore store = new FakeStore();
        store.resources.add(resource("/Docs", true));
        WebDavProtocolDispatcher dispatcher = new WebDavProtocolDispatcher(store);
        MockHttpServletRequest request = request("PROPFIND", "/dav/Docs");
        request.addHeader("Depth", "infinity");
        MockHttpServletResponse response = new MockHttpServletResponse();

        dispatcher.dispatch(principal, request, response);

        assertThat(response.getStatus()).isEqualTo(501);
    }

    @Test
    void proppatchShouldBeMethodNotAllowed() throws Exception {
        WebDavProtocolDispatcher dispatcher = new WebDavProtocolDispatcher(new FakeStore());
        MockHttpServletResponse response = new MockHttpServletResponse();

        dispatcher.dispatch(principal, request("PROPPATCH", "/dav/Docs/a.txt"), response);

        assertThat(response.getStatus()).isEqualTo(405);
    }

    @Test
    void lockShouldBlockOtherUsersAndUnlockWithToken() throws Exception {
        FakeStore store = new FakeStore();
        store.resources.add(resource("/Docs/a.txt", false));
        WebDavProtocolDispatcher dispatcher = new WebDavProtocolDispatcher(store);
        MockHttpServletResponse lockResponse = new MockHttpServletResponse();

        dispatcher.dispatch(principal, request("LOCK", "/dav/Docs/a.txt"), lockResponse);

        String lockToken = lockResponse.getHeader("Lock-Token");
        assertThat(lockResponse.getStatus()).isEqualTo(200);
        assertThat(lockToken).startsWith("<urn:uuid:");

        MockHttpServletRequest putRequest = request("PUT", "/dav/Docs/a.txt");
        putRequest.setContent("changed".getBytes(UTF_8));
        MockHttpServletResponse blockedPut = new MockHttpServletResponse();
        dispatcher.dispatch(new WebDavPrincipal(8L, "bob", 1024L, 512L), putRequest, blockedPut);

        assertThat(blockedPut.getStatus()).isEqualTo(423);

        MockHttpServletRequest unlockRequest = request("UNLOCK", "/dav/Docs/a.txt");
        unlockRequest.addHeader("Lock-Token", lockToken);
        MockHttpServletResponse unlockResponse = new MockHttpServletResponse();
        dispatcher.dispatch(principal, unlockRequest, unlockResponse);

        assertThat(unlockResponse.getStatus()).isEqualTo(204);
    }

    @Test
    void lockShouldReturnActiveLockDiscovery() throws Exception {
        WebDavProtocolDispatcher dispatcher = new WebDavProtocolDispatcher(new FakeStore());
        MockHttpServletResponse response = new MockHttpServletResponse();

        dispatcher.dispatch(principal, request("LOCK", "/dav/Docs/a.txt"), response);

        String lockToken = response.getHeader("Lock-Token");
        assertThat(response.getContentAsString())
                .contains("<D:activelock>")
                .contains("<D:locktoken><D:href>" + lockToken.substring(1, lockToken.length() - 1) + "</D:href></D:locktoken>")
                .contains("<D:timeout>Second-300</D:timeout>");
    }

    @Test
    void lockOwnerShouldWriteWithoutSubmittingIfHeader() throws Exception {
        FakeStore store = new FakeStore();
        store.resources.add(resource("/Docs/a.txt", false));
        WebDavProtocolDispatcher dispatcher = new WebDavProtocolDispatcher(store);
        MockHttpServletResponse lockResponse = new MockHttpServletResponse();
        dispatcher.dispatch(principal, request("LOCK", "/dav/Docs/a.txt"), lockResponse);

        MockHttpServletRequest putRequest = request("PUT", "/dav/Docs/a.txt");
        putRequest.setContent("changed".getBytes(UTF_8));
        MockHttpServletResponse putResponse = new MockHttpServletResponse();
        dispatcher.dispatch(principal, putRequest, putResponse);

        assertThat(putResponse.getStatus()).isEqualTo(204);
    }

    @Test
    void putShouldRejectUnknownContentLength() throws Exception {
        WebDavProtocolDispatcher dispatcher = new WebDavProtocolDispatcher(new FakeStore());
        MockHttpServletRequest putRequest = request("PUT", "/dav/Docs/chunked.txt");
        putRequest.setContent("changed".getBytes(UTF_8));
        putRequest.addHeader("Transfer-Encoding", "chunked");
        MockHttpServletResponse response = new MockHttpServletResponse();

        dispatcher.dispatch(principal, putRequest, response);

        assertThat(response.getStatus()).isEqualTo(411);
    }

    @Test
    void propfindShouldPercentEncodeHrefPaths() throws Exception {
        FakeStore store = new FakeStore();
        store.resources.add(resource("/资料/a b.txt", false));
        WebDavProtocolDispatcher dispatcher = new WebDavProtocolDispatcher(store);
        MockHttpServletRequest propfindRequest = request("PROPFIND", "/dav/%E8%B5%84%E6%96%99/a%20b.txt");
        MockHttpServletResponse response = new MockHttpServletResponse();

        dispatcher.dispatch(principal, propfindRequest, response);

        assertThat(response.getStatus()).isEqualTo(207);
        assertThat(response.getContentAsString()).contains("/dav/%E8%B5%84%E6%96%99/a%20b.txt");
    }

    private MockHttpServletRequest request(String method, String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        request.setScheme("http");
        request.setServerName("localhost");
        request.setServerPort(80);
        request.setContextPath("");
        request.setServletPath("/dav");
        request.setPathInfo(uri.length() == 4 ? null : uri.substring(4));
        request.setRequestURI(uri);
        return request;
    }

    private static WebDavStoredResource resource(String path, boolean directory) {
        return new WebDavStoredResource(
                path,
                path.substring(path.lastIndexOf('/') + 1),
                directory,
                directory ? 0L : 5L,
                directory ? "directory" : "text/plain",
                Instant.parse("2026-05-12T12:00:00Z"),
                Instant.parse("2026-05-12T12:00:00Z"),
                '"' + path + '"'
        );
    }

    private static final class FakeStore implements WebDavResourceStore {

        private final List<WebDavStoredResource> resources = new ArrayList<>();
        private String copiedFrom;
        private String copiedTo;
        private boolean copiedOverwrite;

        @Override
        public Optional<WebDavStoredResource> find(WebDavPrincipal principal, String path) {
            return resources.stream().filter(resource -> resource.path().equals(path)).findFirst();
        }

        @Override
        public List<WebDavStoredResource> list(WebDavPrincipal principal, String directoryPath) {
            return resources.stream()
                    .filter(resource -> !resource.path().equals(directoryPath))
                    .toList();
        }

        @Override
        public WebDavReadResult read(WebDavPrincipal principal, String path) {
            return new WebDavReadResult("text/plain", new ByteArrayInputStream("hello".getBytes(UTF_8)), 5L);
        }

        @Override
        public void write(WebDavPrincipal principal, String path, String contentType, long size, InputStream content, boolean overwrite) {
        }

        @Override
        public void createDirectory(WebDavPrincipal principal, String path) {
        }

        @Override
        public void copy(WebDavPrincipal principal, String fromPath, String toPath, boolean overwrite) {
            copiedFrom = fromPath;
            copiedTo = toPath;
            copiedOverwrite = overwrite;
        }

        @Override
        public void move(WebDavPrincipal principal, String fromPath, String toPath, boolean overwrite) {
        }

        @Override
        public void delete(WebDavPrincipal principal, String path) {
        }
    }
}
