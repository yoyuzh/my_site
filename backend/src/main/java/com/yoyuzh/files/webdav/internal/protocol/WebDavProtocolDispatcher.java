package com.yoyuzh.files.webdav.internal.protocol;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import com.yoyuzh.files.webdav.api.WebDavProtocolGateway;
import com.yoyuzh.files.webdav.api.WebDavRequestPrincipal;
import com.yoyuzh.files.webdav.internal.application.WebDavPrincipal;
import com.yoyuzh.files.webdav.internal.application.WebDavReadResult;
import com.yoyuzh.files.webdav.internal.application.WebDavResourceStore;
import com.yoyuzh.files.webdav.internal.application.WebDavStoredResource;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Apache Tomcat WebdavServlet inspired dispatcher, adapted to the project's
 * workspace-backed resource store instead of Tomcat WebResourceRoot.
 *
 * Original reference: org.apache.catalina.servlets.WebdavServlet from Apache
 * Tomcat 10.1.x, licensed under Apache License 2.0.
 */
@Component
public class WebDavProtocolDispatcher implements WebDavProtocolGateway {

    private static final String DAV_HEADER = "DAV";
    private static final String ALLOW_HEADER = "Allow";
    private static final String LOCK_TOKEN_HEADER = "Lock-Token";
    private static final String OVERWRITE_HEADER = "Overwrite";
    private static final String DESTINATION_HEADER = "Destination";
    private static final String IF_HEADER = "If";
    private static final String LOCK_SCHEME = "urn:uuid:";
    private static final String ALLOW_METHODS = "OPTIONS, GET, HEAD, PUT, DELETE, PROPFIND, MKCOL, COPY, MOVE, LOCK, UNLOCK";
    private static final int SC_MULTI_STATUS = 207;
    private static final int SC_LOCKED = 423;
    private static final int SC_TOO_MANY_REQUESTS = 429;
    private static final int MAX_LOCKS_PER_USER = 100;
    private static final long LOCK_TIMEOUT_SECONDS = 300L;
    private static final long MAX_DRAIN_BYTES = 10L * 1024L * 1024L;
    private static final String XML_CONTENT_TYPE = MediaType.APPLICATION_XML_VALUE + ";charset=UTF-8";

    private final WebDavResourceStore resourceStore;
    private final Map<LockKey, LockInfo> locks = new ConcurrentHashMap<>();

    public WebDavProtocolDispatcher(WebDavResourceStore resourceStore) {
        this.resourceStore = resourceStore;
    }

    @Override
    public void dispatch(WebDavRequestPrincipal principal,
                         HttpServletRequest request,
                         HttpServletResponse response) throws IOException, ServletException {
        dispatch(toProtocolPrincipal(principal), request, response);
    }

    public void dispatch(WebDavPrincipal principal,
                         HttpServletRequest request,
                         HttpServletResponse response) throws IOException, ServletException {
        String method = request.getMethod();
        String path = relativePath(request);
        if (path == null) {
            fail(response, HttpServletResponse.SC_BAD_REQUEST);
            return;
        }
        switch (method) {
            case "OPTIONS" -> options(response);
            case "PROPFIND" -> propfind(principal, path, request, response);
            case "GET" -> get(principal, path, response, true);
            case "HEAD" -> get(principal, path, response, false);
            case "PUT" -> put(principal, path, request, response);
            case "MKCOL" -> mkcol(principal, path, request, response);
            case "DELETE" -> delete(principal, path, request, response);
            case "COPY" -> copy(principal, path, request, response);
            case "MOVE" -> move(principal, path, request, response);
            case "LOCK" -> lock(principal, path, response);
            case "UNLOCK" -> unlock(principal, path, request, response);
            default -> {
                response.setHeader(ALLOW_HEADER, ALLOW_METHODS);
                fail(response, HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            }
        }
    }

    private void options(HttpServletResponse response) {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setHeader(DAV_HEADER, "1,2");
        response.setHeader(ALLOW_HEADER, ALLOW_METHODS);
        response.setHeader("MS-Author-Via", "DAV");
    }

    private void propfind(WebDavPrincipal principal,
                          String path,
                          HttpServletRequest request,
                          HttpServletResponse response) throws IOException {
        String depth = request.getHeader("Depth");
        if ("infinity".equalsIgnoreCase(depth)) {
            fail(response, HttpServletResponse.SC_NOT_IMPLEMENTED);
            return;
        }
        if (depth != null && !"0".equals(depth) && !"1".equals(depth)) {
            fail(response, HttpServletResponse.SC_BAD_REQUEST);
            return;
        }
        Optional<WebDavStoredResource> resource = resourceStore.find(principal, path);
        if (resource.isEmpty()) {
            fail(response, HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        response.setStatus(SC_MULTI_STATUS);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(XML_CONTENT_TYPE);
        WebDavXmlResponseWriter writer = new WebDavXmlResponseWriter(response.getWriter());
        writer.startMultistatus();
        writer.writeResponse(resource.get(), hrefFor(request, resource.get()));
        if ("1".equals(depth) && resource.get().directory()) {
            for (WebDavStoredResource child : resourceStore.list(principal, path)) {
                writer.writeResponse(child, hrefFor(request, child));
            }
        }
        writer.endMultistatus();
    }

    private void get(WebDavPrincipal principal,
                     String path,
                     HttpServletResponse response,
                     boolean includeBody) throws IOException {
        Optional<WebDavStoredResource> resource = resourceStore.find(principal, path);
        if (resource.isEmpty()) {
            fail(response, HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        if (resource.get().directory()) {
            fail(response, HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return;
        }
        WebDavReadResult result = resourceStore.read(principal, path);
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType(result.contentType() == null ? MediaType.APPLICATION_OCTET_STREAM_VALUE : result.contentType());
        response.setContentLengthLong(result.contentLength());
        response.setHeader("ETag", resource.get().etag());
        response.setHeader("Last-Modified", httpDate(resource.get().lastModifiedAt()));
        if (includeBody) {
            try (var content = result.content()) {
                content.transferTo(response.getOutputStream());
            }
        }
    }

    private void put(WebDavPrincipal principal,
                     String path,
                     HttpServletRequest request,
                     HttpServletResponse response) throws IOException {
        if (isLocked(path, principal, request)) {
            fail(response, SC_LOCKED);
            return;
        }
        long contentLength = request.getContentLengthLong();
        if (contentLength > principal.maxUploadSizeBytes()) {
            fail(response, HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
            return;
        }
        if (contentLength < 0) {
            writeUnknownLengthPut(principal, path, request, response);
            return;
        }
        boolean existed = resourceStore.find(principal, path).isPresent();
        resourceStore.write(
                principal,
                path,
                request.getContentType(),
                contentLength,
                request.getInputStream(),
                true
        );
        response.setStatus(existed ? HttpServletResponse.SC_NO_CONTENT : HttpServletResponse.SC_CREATED);
    }

    private void writeUnknownLengthPut(WebDavPrincipal principal,
                                       String path,
                                       HttpServletRequest request,
                                       HttpServletResponse response) throws IOException {
        Path tempFile = Files.createTempFile("webdav-put-", ".bin");
        long size = 0L;
        try {
            try (InputStream content = request.getInputStream();
                 var output = Files.newOutputStream(tempFile)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = content.read(buffer)) != -1) {
                    size += read;
                    if (size > principal.maxUploadSizeBytes()) {
                        drain(content, buffer);
                        fail(response, HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
                        return;
                    }
                    output.write(buffer, 0, read);
                }
            }
            boolean existed = resourceStore.find(principal, path).isPresent();
            try (InputStream countedContent = Files.newInputStream(tempFile)) {
                resourceStore.write(
                        principal,
                        path,
                        request.getContentType(),
                        size,
                        countedContent,
                        true
                );
            }
            response.setStatus(existed ? HttpServletResponse.SC_NO_CONTENT : HttpServletResponse.SC_CREATED);
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    private void mkcol(WebDavPrincipal principal,
                       String path,
                       HttpServletRequest request,
                       HttpServletResponse response) throws IOException {
        if (isLocked(path, principal, request)) {
            fail(response, SC_LOCKED);
            return;
        }
        if (request.getContentLengthLong() > 0) {
            fail(response, HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE);
            return;
        }
        if (resourceStore.find(principal, path).isPresent()) {
            fail(response, HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return;
        }
        resourceStore.createDirectory(principal, path);
        response.setStatus(HttpServletResponse.SC_CREATED);
    }

    private void delete(WebDavPrincipal principal,
                        String path,
                        HttpServletRequest request,
                        HttpServletResponse response) throws IOException {
        if (isLocked(path, principal, request)) {
            fail(response, SC_LOCKED);
            return;
        }
        if (resourceStore.find(principal, path).isEmpty()) {
            fail(response, HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        resourceStore.delete(principal, path);
        response.setStatus(HttpServletResponse.SC_NO_CONTENT);
    }

    private void copy(WebDavPrincipal principal,
                      String path,
                      HttpServletRequest request,
                      HttpServletResponse response) throws IOException {
        String destinationPath = parseDestinationPath(request, response);
        if (destinationPath == null) {
            return;
        }
        if (resourceStore.find(principal, path).isEmpty()) {
            fail(response, HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        if (isLocked(destinationPath, principal, request)) {
            fail(response, SC_LOCKED);
            return;
        }
        boolean overwrite = overwrite(request);
        if (!overwrite && resourceStore.find(principal, destinationPath).isPresent()) {
            fail(response, HttpServletResponse.SC_PRECONDITION_FAILED);
            return;
        }
        boolean existed = resourceStore.find(principal, destinationPath).isPresent();
        resourceStore.copy(principal, path, destinationPath, overwrite);
        response.setStatus(existed ? HttpServletResponse.SC_NO_CONTENT : HttpServletResponse.SC_CREATED);
    }

    private void drain(InputStream content, byte[] buffer) throws IOException {
        long drained = 0L;
        int read;
        while ((read = content.read(buffer)) != -1) {
            drained += read;
            if (drained >= MAX_DRAIN_BYTES) {
                return;
            }
            // Consume the remaining oversized request body so the connector can reuse the connection cleanly.
        }
    }

    private void move(WebDavPrincipal principal,
                      String path,
                      HttpServletRequest request,
                      HttpServletResponse response) throws IOException {
        String destinationPath = parseDestinationPath(request, response);
        if (destinationPath == null) {
            return;
        }
        if (isLocked(path, principal, request) || isLocked(destinationPath, principal, request)) {
            fail(response, SC_LOCKED);
            return;
        }
        if (resourceStore.find(principal, path).isEmpty()) {
            fail(response, HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        boolean overwrite = overwrite(request);
        if (!overwrite && resourceStore.find(principal, destinationPath).isPresent()) {
            fail(response, HttpServletResponse.SC_PRECONDITION_FAILED);
            return;
        }
        boolean existed = resourceStore.find(principal, destinationPath).isPresent();
        resourceStore.move(principal, path, destinationPath, overwrite);
        response.setStatus(existed ? HttpServletResponse.SC_NO_CONTENT : HttpServletResponse.SC_CREATED);
    }

    private void lock(WebDavPrincipal principal, String path, HttpServletResponse response) throws IOException {
        removeExpiredLocks();
        LockKey lockKey = LockKey.from(principal, path);
        if (lockCountFor(principal) >= MAX_LOCKS_PER_USER && !locks.containsKey(lockKey)) {
            fail(response, SC_TOO_MANY_REQUESTS);
            return;
        }
        String token = UUID.randomUUID().toString();
        LockInfo lock = new LockInfo(path, principal.userId(), token, Instant.now().plusSeconds(LOCK_TIMEOUT_SECONDS));
        LockInfo previous = locks.putIfAbsent(lockKey, lock);
        if (previous != null) {
            lock = new LockInfo(path, principal.userId(), previous.token(), Instant.now().plusSeconds(LOCK_TIMEOUT_SECONDS));
            locks.put(lockKey, lock);
        }
        response.setStatus(previous == null ? HttpServletResponse.SC_CREATED : HttpServletResponse.SC_OK);
        response.setHeader(LOCK_TOKEN_HEADER, "<" + LOCK_SCHEME + lock.token() + ">");
        response.setHeader("Timeout", "Second-" + LOCK_TIMEOUT_SECONDS);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(XML_CONTENT_TYPE);
        response.getWriter().write(lockDiscoveryXml(principal, lock));
    }

    private void unlock(WebDavPrincipal principal,
                        String path,
                        HttpServletRequest request,
                        HttpServletResponse response) throws IOException {
        LockKey lockKey = LockKey.from(principal, path);
        LockInfo lock = locks.get(lockKey);
        if (lock == null || lock.expired() || !lock.ownedBy(principal) || !tokenMatches(lock, request.getHeader(LOCK_TOKEN_HEADER))) {
            if (lock != null && lock.expired()) {
                locks.remove(lockKey);
            }
            fail(response, HttpServletResponse.SC_PRECONDITION_FAILED);
            return;
        }
        locks.remove(lockKey);
        response.setStatus(HttpServletResponse.SC_NO_CONTENT);
    }

    private boolean isLocked(String path, WebDavPrincipal principal, HttpServletRequest request) {
        LockKey lockKey = LockKey.from(principal, path);
        LockInfo lock = locks.get(lockKey);
        if (lock == null) {
            return false;
        }
        if (lock.expired()) {
            locks.remove(lockKey);
            return false;
        }
        if (!lock.ownedBy(principal)) {
            return true;
        }
        return false;
    }

    private boolean tokenMatches(LockInfo lock, String headerValue) {
        return headerValue != null && headerValue.contains(LOCK_SCHEME + lock.token());
    }

    private boolean overwrite(HttpServletRequest request) {
        return !"F".equalsIgnoreCase(request.getHeader(OVERWRITE_HEADER));
    }

    private String parseDestinationPath(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String destinationHeader = request.getHeader(DESTINATION_HEADER);
        if (destinationHeader == null || destinationHeader.isBlank()) {
            fail(response, HttpServletResponse.SC_BAD_REQUEST);
            return null;
        }
        URI uri;
        try {
            uri = new URI(destinationHeader);
        } catch (URISyntaxException ex) {
            fail(response, HttpServletResponse.SC_BAD_REQUEST);
            return null;
        }
        if (uri.isAbsolute() && (!request.getScheme().equals(uri.getScheme()) || !request.getServerName().equals(uri.getHost()))) {
            fail(response, HttpServletResponse.SC_FORBIDDEN);
            return null;
        }
        if (uri.isAbsolute() && !portMatches(request, uri)) {
            fail(response, HttpServletResponse.SC_FORBIDDEN);
            return null;
        }
        String destinationPath = uri.getPath();
        String davPrefix = davPrefix(destinationPath);
        if (davPrefix == null) {
            fail(response, HttpServletResponse.SC_FORBIDDEN);
            return null;
        }
        String normalized = normalizeDavPath(destinationPath.substring(davPrefix.length()));
        if (normalized == null) {
            fail(response, HttpServletResponse.SC_BAD_REQUEST);
        }
        return normalized;
    }

    private void fail(HttpServletResponse response, int status) {
        response.setStatus(status);
    }

    private String relativePath(HttpServletRequest request) {
        String pathInfo = request.getPathInfo();
        if (pathInfo == null || pathInfo.isBlank()) {
            String requestUri = request.getRequestURI();
            String davPrefix = davPrefix(requestUri);
            if (davPrefix == null) {
                return null;
            }
            pathInfo = requestUri.length() <= davPrefix.length() ? "/" : requestUri.substring(davPrefix.length());
        }
        return normalizeDavPath(decodePath(pathInfo));
    }

    private String normalizeDavPath(String path) {
        if (path == null || path.isBlank() || "/".equals(path)) {
            return "/";
        }
        String normalized = path.startsWith("/") ? path : "/" + path;
        for (String segment : normalized.split("/")) {
            if (".".equals(segment) || "..".equals(segment)) {
                return null;
            }
        }
        return normalized.length() > 1 && normalized.endsWith("/")
                ? normalized.substring(0, normalized.length() - 1)
                : normalized;
    }

    private String hrefFor(HttpServletRequest request, WebDavStoredResource resource) {
        String requestPrefix = davPrefix(request.getRequestURI());
        String prefix = request.getContextPath() + (requestPrefix == null ? "/dav" : requestPrefix);
        String href = "/".equals(resource.path()) ? prefix + "/" : prefix + encodePath(resource.path());
        return resource.directory() && !href.endsWith("/") ? href + "/" : href;
    }

    private String davPrefix(String requestPath) {
        if (requestPath == null) {
            return null;
        }
        if ("/dav".equals(requestPath) || requestPath.startsWith("/dav/")) {
            return "/dav";
        }
        if ("/api/dav".equals(requestPath) || requestPath.startsWith("/api/dav/")) {
            return "/api/dav";
        }
        return null;
    }

    private String httpDate(Instant instant) {
        return DateTimeFormatter.RFC_1123_DATE_TIME.format(instant.atOffset(ZoneOffset.UTC));
    }

    private boolean portMatches(HttpServletRequest request, URI uri) {
        int destinationPort = uri.getPort();
        int requestPort = request.getServerPort();
        if (destinationPort == -1) {
            return ("http".equals(request.getScheme()) && requestPort == 80)
                    || ("https".equals(request.getScheme()) && requestPort == 443);
        }
        return destinationPort == requestPort;
    }

    private String decodePath(String path) {
        if (path == null) {
            return null;
        }
        return URLDecoder.decode(path.replace("+", "%2B"), StandardCharsets.UTF_8);
    }

    private String encodePath(String path) {
        if (path == null || path.isBlank() || "/".equals(path)) {
            return "/";
        }
        String[] segments = path.split("/", -1);
        StringBuilder encoded = new StringBuilder();
        for (int i = 0; i < segments.length; i++) {
            if (i > 0) {
                encoded.append('/');
            }
            encoded.append(URLEncoder.encode(segments[i], StandardCharsets.UTF_8).replace("+", "%20"));
        }
        return encoded.toString();
    }

    private String lockDiscoveryXml(WebDavPrincipal principal, LockInfo lock) {
        String token = LOCK_SCHEME + lock.token();
        return """
                <?xml version="1.0" encoding="utf-8" ?>
                <D:prop xmlns:D="DAV:">
                  <D:lockdiscovery>
                    <D:activelock>
                      <D:locktype><D:write/></D:locktype>
                      <D:lockscope><D:exclusive/></D:lockscope>
                      <D:depth>infinity</D:depth>
                      <D:owner>%s</D:owner>
                      <D:timeout>Second-%d</D:timeout>
                      <D:locktoken><D:href>%s</D:href></D:locktoken>
                    </D:activelock>
                  </D:lockdiscovery>
                </D:prop>
                """.formatted(escapeXml(principal.username()), LOCK_TIMEOUT_SECONDS, token);
    }

    private String escapeXml(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    private int lockCountFor(WebDavPrincipal principal) {
        removeExpiredLocks();
        return (int) locks.values().stream()
                .filter(lock -> lock.ownedBy(principal))
                .count();
    }

    @Scheduled(fixedRate = 60_000L)
    void removeExpiredLocks() {
        locks.entrySet().removeIf(entry -> entry.getValue().expired());
    }

    private WebDavPrincipal toProtocolPrincipal(WebDavRequestPrincipal principal) {
        return new WebDavPrincipal(
                principal.userId(),
                principal.username(),
                principal.storageQuotaBytes(),
                principal.maxUploadSizeBytes()
        );
    }

    private record LockInfo(String path, Long ownerUserId, String token, Instant expiresAt) {
        private boolean ownedBy(WebDavPrincipal principal) {
            return ownerUserId.equals(principal.userId());
        }

        private boolean expired() {
            return Instant.now().isAfter(expiresAt);
        }
    }

    private record LockKey(Long userId, String path) {
        private static LockKey from(WebDavPrincipal principal, String path) {
            return new LockKey(principal.userId(), path);
        }
    }
}
