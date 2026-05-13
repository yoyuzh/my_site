package com.yoyuzh.files.webdav.internal.protocol;

import com.yoyuzh.files.webdav.internal.application.WebDavPrincipal;
import com.yoyuzh.files.webdav.internal.application.WebDavReadResult;
import com.yoyuzh.files.webdav.internal.application.WebDavResourceStore;
import com.yoyuzh.files.webdav.internal.application.WebDavStoredResource;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

class WebDavProtocolDispatcherTest {

    private final WebDavPrincipal principal = new WebDavPrincipal(7L, "alice", 1024L, 512L);

    @Test
    void optionsShouldAdvertiseSupportedWebDavLevels() throws Exception {
        WebDavProtocolDispatcher dispatcher = new WebDavProtocolDispatcher(new FakeStore());
        MockHttpServletResponse response = new MockHttpServletResponse();

        dispatcher.dispatch(principal, request("OPTIONS", "/dav"), response);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getHeader("DAV")).isEqualTo("1,2");
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
    void propfindShouldReturnEmptyNotFoundWithoutErrorDispatchBody() throws Exception {
        WebDavProtocolDispatcher dispatcher = new WebDavProtocolDispatcher(new FakeStore());
        MockHttpServletResponse response = new MockHttpServletResponse();

        dispatcher.dispatch(principal, request("PROPFIND", "/dav/desktop.ini"), response);

        assertThat(response.getStatus()).isEqualTo(404);
        assertThat(response.getErrorMessage()).isNull();
        assertThat(response.isCommitted()).isFalse();
        assertThat(response.getContentAsString()).isEmpty();
    }

    @Test
    void proppatchShouldBeMethodNotAllowed() throws Exception {
        WebDavProtocolDispatcher dispatcher = new WebDavProtocolDispatcher(new FakeStore());
        MockHttpServletResponse response = new MockHttpServletResponse();

        dispatcher.dispatch(principal, request("PROPPATCH", "/dav/Docs/a.txt"), response);

        assertThat(response.getStatus()).isEqualTo(405);
    }

    @Test
    void lockShouldNotBlockOtherUsersWithSameLogicalPath() throws Exception {
        FakeStore store = new FakeStore();
        store.resources.add(resource("/Docs/a.txt", false));
        WebDavProtocolDispatcher dispatcher = new WebDavProtocolDispatcher(store);
        MockHttpServletResponse lockResponse = new MockHttpServletResponse();

        dispatcher.dispatch(principal, request("LOCK", "/dav/Docs/a.txt"), lockResponse);

        String lockToken = lockResponse.getHeader("Lock-Token");
        assertThat(lockResponse.getStatus()).isEqualTo(201);
        assertThat(lockToken).startsWith("<urn:uuid:");

        MockHttpServletRequest putRequest = request("PUT", "/dav/Docs/a.txt");
        putRequest.setContent("changed".getBytes(UTF_8));
        MockHttpServletResponse otherUserPut = new MockHttpServletResponse();
        dispatcher.dispatch(new WebDavPrincipal(8L, "bob", 1024L, 512L), putRequest, otherUserPut);

        assertThat(otherUserPut.getStatus()).isEqualTo(204);

        MockHttpServletRequest unlockRequest = request("UNLOCK", "/dav/Docs/a.txt");
        unlockRequest.addHeader("Lock-Token", lockToken);
        MockHttpServletResponse unlockResponse = new MockHttpServletResponse();
        dispatcher.dispatch(principal, unlockRequest, unlockResponse);

        assertThat(unlockResponse.getStatus()).isEqualTo(204);
    }

    @Test
    void lockRefreshShouldReturnOk() throws Exception {
        WebDavProtocolDispatcher dispatcher = new WebDavProtocolDispatcher(new FakeStore());
        MockHttpServletResponse lockResponse = new MockHttpServletResponse();
        dispatcher.dispatch(principal, request("LOCK", "/dav/Docs/a.txt"), lockResponse);
        MockHttpServletResponse refreshResponse = new MockHttpServletResponse();

        dispatcher.dispatch(principal, request("LOCK", "/dav/Docs/a.txt"), refreshResponse);

        assertThat(refreshResponse.getStatus()).isEqualTo(200);
        assertThat(refreshResponse.getHeader("Lock-Token")).isEqualTo(lockResponse.getHeader("Lock-Token"));

        MockHttpServletRequest unlockRequest = request("UNLOCK", "/dav/Docs/a.txt");
        unlockRequest.addHeader("Lock-Token", lockResponse.getHeader("Lock-Token"));
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
    void putShouldAcceptChunkedTransferAndForwardCountedSize() throws Exception {
        FakeStore store = new FakeStore();
        WebDavProtocolDispatcher dispatcher = new WebDavProtocolDispatcher(store);
        MockHttpServletRequest putRequest = unknownLengthRequest("PUT", "/dav/Docs/chunked.txt", "changed");
        putRequest.addHeader("Transfer-Encoding", "chunked");
        MockHttpServletResponse response = new MockHttpServletResponse();

        dispatcher.dispatch(principal, putRequest, response);

        assertThat(response.getStatus()).isEqualTo(201);
        assertThat(store.writtenPath).isEqualTo("/Docs/chunked.txt");
        assertThat(store.writtenSize).isEqualTo(7L);
    }

    @Test
    void unknownLengthPutShouldRecheckExistingResourceAfterBodyIsCounted() throws Exception {
        ExistingAtWriteTimeStore store = new ExistingAtWriteTimeStore();
        WebDavProtocolDispatcher dispatcher = new WebDavProtocolDispatcher(store);
        MockHttpServletRequest putRequest = unknownLengthRequest("PUT", "/dav/Docs/race.txt", "changed");
        MockHttpServletResponse response = new MockHttpServletResponse();

        dispatcher.dispatch(principal, putRequest, response);

        assertThat(response.getStatus()).isEqualTo(204);
        assertThat(store.writtenPath).isEqualTo("/Docs/race.txt");
    }

    @Test
    void putShouldRejectKnownContentLengthAboveMaxUploadSize() throws Exception {
        FakeStore store = new FakeStore();
        WebDavProtocolDispatcher dispatcher = new WebDavProtocolDispatcher(store);
        MockHttpServletRequest putRequest = request("PUT", "/dav/Docs/large.txt");
        putRequest.setContent("too-large".getBytes(UTF_8));
        MockHttpServletResponse response = new MockHttpServletResponse();

        dispatcher.dispatch(new WebDavPrincipal(7L, "alice", 1024L, 4L), putRequest, response);

        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(store.writtenPath).isNull();
    }

    @Test
    void putShouldDrainUnknownLengthBodyAfterUploadLimitExceeded() throws Exception {
        FakeStore store = new FakeStore();
        WebDavProtocolDispatcher dispatcher = new WebDavProtocolDispatcher(store);
        byte[] body = new byte[9000];
        Arrays.fill(body, (byte) 'x');
        CountingUnknownLengthRequest putRequest = new CountingUnknownLengthRequest("PUT", "/dav/Docs/large.bin", body);
        MockHttpServletResponse response = new MockHttpServletResponse();

        dispatcher.dispatch(new WebDavPrincipal(7L, "alice", 1024L, 4L), putRequest, response);

        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(putRequest.bytesRead()).isEqualTo(body.length);
        assertThat(store.writtenPath).isNull();
    }

    @Test
    void putShouldLimitDrainAfterUploadLimitExceeded() throws Exception {
        FakeStore store = new FakeStore();
        WebDavProtocolDispatcher dispatcher = new WebDavProtocolDispatcher(store);
        byte[] body = new byte[11 * 1024 * 1024];
        Arrays.fill(body, (byte) 'x');
        CountingUnknownLengthRequest putRequest = new CountingUnknownLengthRequest("PUT", "/dav/Docs/huge.bin", body);
        MockHttpServletResponse response = new MockHttpServletResponse();

        dispatcher.dispatch(new WebDavPrincipal(7L, "alice", 1024L, 4L), putRequest, response);

        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(putRequest.bytesRead()).isLessThan(body.length);
        assertThat(store.writtenPath).isNull();
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

    @Test
    void propfindShouldWriteUtf8XmlDisplayNames() throws Exception {
        FakeStore store = new FakeStore();
        store.resources.add(resource("/", true));
        store.resources.add(resource("/资料", true));
        WebDavProtocolDispatcher dispatcher = new WebDavProtocolDispatcher(store);
        MockHttpServletRequest propfindRequest = request("PROPFIND", "/dav");
        propfindRequest.addHeader("Depth", "1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        dispatcher.dispatch(principal, propfindRequest, response);

        assertThat(response.getStatus()).isEqualTo(207);
        assertThat(response.getContentType()).isEqualTo("application/xml;charset=UTF-8");
        assertThat(response.getContentAsString(UTF_8)).contains("<D:displayname>资料</D:displayname>");
    }

    @Test
    void propfindShouldUseWindowsCompatibleHttpDateWithTwoDigitDay() throws Exception {
        FakeStore store = new FakeStore();
        store.resources.add(new WebDavStoredResource(
                "/early.txt",
                "early.txt",
                false,
                5L,
                "text/plain",
                Instant.parse("2026-05-07T03:42:02Z"),
                Instant.parse("2026-05-07T03:42:02Z"),
                "\"early\""
        ));
        WebDavProtocolDispatcher dispatcher = new WebDavProtocolDispatcher(store);
        MockHttpServletResponse response = new MockHttpServletResponse();

        dispatcher.dispatch(principal, request("PROPFIND", "/dav/early.txt"), response);

        assertThat(response.getStatus()).isEqualTo(207);
        assertThat(response.getContentAsString(UTF_8))
                .contains("<D:creationdate>2026-05-07T03:42:02Z</D:creationdate>")
                .contains("<D:getlastmodified>Thu, 07 May 2026 03:42:02 GMT</D:getlastmodified>")
                .doesNotContain("Thu, 7 May 2026");
    }

    @Test
    void propfindShouldIncludeWindowsCompatibleResourceProperties() throws Exception {
        FakeStore store = new FakeStore();
        store.resources.add(resource("/", true));
        store.resources.add(resource("/Docs/a.txt", false));
        WebDavProtocolDispatcher dispatcher = new WebDavProtocolDispatcher(store);
        MockHttpServletRequest propfindRequest = request("PROPFIND", "/dav");
        propfindRequest.addHeader("Depth", "1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        dispatcher.dispatch(principal, propfindRequest, response);

        assertThat(response.getStatus()).isEqualTo(207);
        assertThat(response.getContentLength()).isGreaterThan(0);
        assertThat(response.getContentAsString(UTF_8))
                .contains("<D:getcontentlength>0</D:getcontentlength>")
                .contains("<D:getetag>")
                .contains("<D:getcontenttype>httpd/unix-directory</D:getcontenttype>")
                .contains("<D:supportedlock>")
                .contains("<D:lockentry>");
    }

    @Test
    void propfindShouldExposeStableRootMetadata() throws Exception {
        FakeStore store = new FakeStore();
        store.resources.add(new WebDavStoredResource(
                "/",
                "",
                true,
                0L,
                "directory",
                Instant.parse("1970-01-01T00:00:00Z"),
                Instant.parse("1970-01-01T00:00:00Z"),
                "\"null-0\""
        ));
        WebDavProtocolDispatcher dispatcher = new WebDavProtocolDispatcher(store);
        MockHttpServletResponse response = new MockHttpServletResponse();

        dispatcher.dispatch(principal, request("PROPFIND", "/dav"), response);

        assertThat(response.getStatus()).isEqualTo(207);
        assertThat(response.getContentAsString(UTF_8))
                .contains("<D:displayname>dav</D:displayname>")
                .contains("<D:getetag>&quot;root-7&quot;</D:getetag>")
                .doesNotContain("<D:displayname></D:displayname>")
                .doesNotContain("&quot;null-0&quot;");
    }

    @Test
    void propfindShouldUseDavBaseHrefForNestedResources() throws Exception {
        FakeStore store = new FakeStore();
        store.resources.add(resource("/资料", true));
        store.resources.add(resource("/资料/电路图.pdf", false));
        WebDavProtocolDispatcher dispatcher = new WebDavProtocolDispatcher(store);
        MockHttpServletRequest propfindRequest = request("PROPFIND", "/dav/%E8%B5%84%E6%96%99");
        propfindRequest.setServletPath("/dav/%E8%B5%84%E6%96%99");
        propfindRequest.addHeader("Depth", "1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        dispatcher.dispatch(principal, propfindRequest, response);

        assertThat(response.getStatus()).isEqualTo(207);
        assertThat(response.getContentAsString(UTF_8))
                .contains("<D:href>/dav/%E8%B5%84%E6%96%99/</D:href>")
                .contains("<D:href>/dav/%E8%B5%84%E6%96%99/%E7%94%B5%E8%B7%AF%E5%9B%BE.pdf</D:href>")
                .doesNotContain("/dav/%E8%B5%84%E6%96%99/%E8%B5%84%E6%96%99");
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

    private MockHttpServletRequest unknownLengthRequest(String method, String uri, String body) {
        byte[] content = body.getBytes(StandardCharsets.UTF_8);
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri) {
            @Override
            public long getContentLengthLong() {
                return -1L;
            }
        };
        request.setContent(content);
        request.setScheme("http");
        request.setServerName("localhost");
        request.setServerPort(80);
        request.setContextPath("");
        request.setServletPath("/dav");
        request.setPathInfo(uri.length() == 4 ? null : uri.substring(4));
        request.setRequestURI(uri);
        return request;
    }

    private static void applyDavRequestFields(MockHttpServletRequest request, String uri) {
        request.setScheme("http");
        request.setServerName("localhost");
        request.setServerPort(80);
        request.setContextPath("");
        request.setServletPath("/dav");
        request.setPathInfo(uri.length() == 4 ? null : uri.substring(4));
        request.setRequestURI(uri);
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

    private static class FakeStore implements WebDavResourceStore {

        private final List<WebDavStoredResource> resources = new ArrayList<>();
        private String copiedFrom;
        private String copiedTo;
        private boolean copiedOverwrite;
        String writtenPath;
        private long writtenSize;

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
            writtenPath = path;
            writtenSize = size;
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

    private static final class ExistingAtWriteTimeStore extends FakeStore {

        @Override
        public Optional<WebDavStoredResource> find(WebDavPrincipal principal, String path) {
            return Optional.of(resource(path, false));
        }
    }

    private static final class CountingUnknownLengthRequest extends MockHttpServletRequest {

        private final CountingServletInputStream stream;

        private CountingUnknownLengthRequest(String method, String uri, byte[] body) {
            super(method, uri);
            this.stream = new CountingServletInputStream(body);
            applyDavRequestFields(this, uri);
        }

        @Override
        public long getContentLengthLong() {
            return -1L;
        }

        @Override
        public ServletInputStream getInputStream() {
            return stream;
        }

        private int bytesRead() {
            return stream.bytesRead();
        }
    }

    private static final class CountingServletInputStream extends ServletInputStream {

        private final ByteArrayInputStream delegate;
        private int bytesRead;

        private CountingServletInputStream(byte[] body) {
            this.delegate = new ByteArrayInputStream(body);
        }

        @Override
        public int read() {
            int value = delegate.read();
            if (value != -1) {
                bytesRead++;
            }
            return value;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) {
            int read = delegate.read(buffer, offset, length);
            if (read > 0) {
                bytesRead += read;
            }
            return read;
        }

        @Override
        public boolean isFinished() {
            return delegate.available() == 0;
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setReadListener(ReadListener readListener) {
        }

        private int bytesRead() {
            return bytesRead;
        }
    }
}
