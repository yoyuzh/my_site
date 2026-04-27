package com.yoyuzh.transfer.internal.infra;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class DefaultQbittorrentClient implements QbittorrentClient {

    private static final String CRLF = "\r\n";

    private final DownloaderProperties properties;
    private final ObjectMapper objectMapper;
    private final Transport transport;
    private volatile String sessionCookie;

    @Autowired
    public DefaultQbittorrentClient(DownloaderProperties properties, ObjectMapper objectMapper) {
        this(properties, objectMapper, new HttpTransport());
    }

    DefaultQbittorrentClient(DownloaderProperties properties, ObjectMapper objectMapper, Transport transport) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.transport = transport;
    }

    @Override
    public String submitMagnet(String sourceValue, String downloadNodeId) {
        return submit(buildMagnetParts(sourceValue));
    }

    @Override
    public String submitTorrent(String torrentFilename, byte[] torrentContent, String downloadNodeId) {
        return submit(buildTorrentParts(torrentFilename, torrentContent));
    }

    @Override
    public TorrentStatus queryTorrent(String hash) {
        validateConfiguration();
        TransportResponse response = sendAuthenticated(
                "GET",
                buildUrl("/api/v2/torrents/info?hashes=" + urlEncode(hash)),
                new byte[0],
                Map.of()
        );
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("qBittorrent 查询任务失败: HTTP " + response.statusCode() + " " + response.body());
        }
        try {
            JsonNode root = objectMapper.readTree(response.body());
            if (!root.isArray() || root.size() == 0) {
                throw new IllegalStateException("qBittorrent 查询任务失败: 未找到下载任务");
            }
            JsonNode torrent = root.get(0);
            return new TorrentStatus(
                    torrent.path("hash").asText(),
                    torrent.path("state").asText(),
                    torrent.path("progress").asDouble(0.0d),
                    emptyToNull(torrent.path("content_path").asText()),
                    emptyToNull(torrent.path("save_path").asText())
            );
        } catch (IOException ex) {
            throw new IllegalStateException("解析 qBittorrent 响应失败", ex);
        }
    }

    @Override
    public List<TorrentFile> listFiles(String hash) {
        validateConfiguration();
        TransportResponse response = sendAuthenticated(
                "GET",
                buildUrl("/api/v2/torrents/files?hash=" + urlEncode(hash)),
                new byte[0],
                Map.of()
        );
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("qBittorrent 查询文件列表失败: HTTP " + response.statusCode() + " " + response.body());
        }
        try {
            JsonNode root = objectMapper.readTree(response.body());
            if (!root.isArray()) {
                throw new IllegalStateException("qBittorrent 查询文件列表失败: 响应不是数组");
            }
            java.util.ArrayList<TorrentFile> files = new java.util.ArrayList<>();
            for (JsonNode file : root) {
                files.add(new TorrentFile(
                        String.valueOf(file.path("index").asInt()),
                        file.path("name").asText(),
                        file.path("size").asLong(0L),
                        file.path("priority").asInt(0)
                ));
            }
            return files;
        } catch (IOException ex) {
            throw new IllegalStateException("解析 qBittorrent 响应失败", ex);
        }
    }

    @Override
    public void updateFileSelection(String hash, List<String> selectedFileKeys, List<String> unselectedFileKeys) {
        validateConfiguration();
        if (selectedFileKeys != null && !selectedFileKeys.isEmpty()) {
            postForm("/api/v2/torrents/filePrio",
                    "hash=" + urlEncode(hash)
                            + "&id=" + urlEncode(String.join("|", selectedFileKeys))
                            + "&priority=1");
        }
        if (unselectedFileKeys != null && !unselectedFileKeys.isEmpty()) {
            postForm("/api/v2/torrents/filePrio",
                    "hash=" + urlEncode(hash)
                            + "&id=" + urlEncode(String.join("|", unselectedFileKeys))
                            + "&priority=0");
        }
    }

    @Override
    public void delete(String hash, boolean deleteFiles) {
        validateConfiguration();
        postForm("/api/v2/torrents/delete",
                "hashes=" + urlEncode(hash)
                        + "&deleteFiles=" + deleteFiles);
    }

    private String submit(Map<String, Part> parts) {
        validateConfiguration();
        String tag = resolveTag();
        String boundary = "----CodexQb" + UUID.randomUUID().toString().replace("-", "");
        Map<String, Part> requestParts = new LinkedHashMap<>(parts);
        requestParts.put("tags", Part.text(tag));
        if (StringUtils.hasText(properties.getQbittorrent().getSavePath())) {
            requestParts.put("savepath", Part.text(properties.getQbittorrent().getSavePath().trim()));
        }
        if (StringUtils.hasText(properties.getQbittorrent().getCategory())) {
            requestParts.put("category", Part.text(properties.getQbittorrent().getCategory().trim()));
        }
        byte[] body = MultipartBodyBuilder.build(boundary, requestParts);
        TransportResponse response = sendAuthenticated(
                "POST",
                buildUrl("/api/v2/torrents/add"),
                body,
                Map.of("Content-Type", "multipart/form-data; boundary=" + boundary)
        );
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("qBittorrent 提交任务失败: HTTP " + response.statusCode() + " " + response.body());
        }
        return lookupHashByTag(tag);
    }

    private String lookupHashByTag(String tag) {
        TransportResponse response = sendAuthenticated(
                "GET",
                buildUrl("/api/v2/torrents/info?tag=" + urlEncode(tag)),
                new byte[0],
                Map.of()
        );
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("qBittorrent 查询任务失败: HTTP " + response.statusCode() + " " + response.body());
        }
        try {
            JsonNode root = objectMapper.readTree(response.body());
            if (!root.isArray() || root.isEmpty()) {
                throw new IllegalStateException("qBittorrent 查询任务失败: 未找到刚提交的下载任务");
            }
            String hash = root.get(0).path("hash").asText();
            if (!StringUtils.hasText(hash)) {
                throw new IllegalStateException("qBittorrent 查询任务失败: 响应缺少 hash");
            }
            return hash;
        } catch (IOException ex) {
            throw new IllegalStateException("解析 qBittorrent 响应失败", ex);
        }
    }

    private String login() {
        if (StringUtils.hasText(sessionCookie)) {
            return sessionCookie;
        }
        synchronized (this) {
            if (StringUtils.hasText(sessionCookie)) {
                return sessionCookie;
            }
            sessionCookie = performLogin();
            return sessionCookie;
        }
    }

    private String performLogin() {
        String requestBody = "username=" + urlEncode(properties.getQbittorrent().getUsername().trim())
                + "&password=" + urlEncode(properties.getQbittorrent().getPassword().trim());
        TransportResponse response = send(
                "POST",
                buildUrl("/api/v2/auth/login"),
                requestBody.getBytes(StandardCharsets.UTF_8),
                Map.of("Content-Type", "application/x-www-form-urlencoded")
        );
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("qBittorrent 登录失败: HTTP " + response.statusCode() + " " + response.body());
        }
        List<String> cookies = response.headers().get("Set-Cookie");
        if (cookies == null || cookies.isEmpty()) {
            throw new IllegalStateException("qBittorrent 登录失败: 响应缺少会话 Cookie");
        }
        return cookies.get(0).split(";", 2)[0];
    }

    private void invalidateCookie() {
        sessionCookie = null;
    }

    private Map<String, Part> buildMagnetParts(String sourceValue) {
        Map<String, Part> parts = new LinkedHashMap<>();
        parts.put("urls", Part.text(sourceValue));
        return parts;
    }

    private Map<String, Part> buildTorrentParts(String torrentFilename, byte[] torrentContent) {
        if (torrentContent == null || torrentContent.length == 0) {
            throw new IllegalArgumentException("torrent 文件内容不能为空");
        }
        Map<String, Part> parts = new LinkedHashMap<>();
        parts.put("torrents", Part.file(
                torrentFilename == null || torrentFilename.isBlank() ? "upload.torrent" : torrentFilename,
                "application/x-bittorrent",
                torrentContent
        ));
        return parts;
    }

    private TransportResponse send(String method, String url, byte[] body, Map<String, String> headers) {
        try {
            return transport.send(method, url, body, headers);
        } catch (IOException ex) {
            throw new IllegalStateException("请求 qBittorrent 失败", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("请求 qBittorrent 被中断", ex);
        }
    }

    private void validateConfiguration() {
        if (!StringUtils.hasText(properties.getQbittorrent().getBaseUrl())) {
            throw new IllegalStateException("qBittorrent baseUrl 未配置");
        }
        if (!StringUtils.hasText(properties.getQbittorrent().getUsername())
                || !StringUtils.hasText(properties.getQbittorrent().getPassword())) {
            throw new IllegalStateException("qBittorrent 用户名或密码未配置");
        }
    }

    private String buildUrl(String apiPath) {
        return properties.getQbittorrent().getBaseUrl().trim().replaceAll("/+$", "") + apiPath;
    }

    private String resolveTag() {
        String prefix = StringUtils.hasText(properties.getQbittorrent().getTagPrefix())
                ? properties.getQbittorrent().getTagPrefix().trim()
                : "remote-download-";
        return prefix + UUID.randomUUID().toString().replace("-", "");
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String emptyToNull(String value) {
        return StringUtils.hasText(value) ? value : null;
    }

    private void postForm(String apiPath, String cookie, String requestBody) {
        throw new UnsupportedOperationException("Use postForm(apiPath, requestBody)");
    }

    private void postForm(String apiPath, String requestBody) {
        byte[] requestBodyBytes = requestBody.getBytes(StandardCharsets.UTF_8);
        TransportResponse response = sendAuthenticated(
                "POST",
                buildUrl(apiPath),
                requestBodyBytes,
                Map.of("Content-Type", "application/x-www-form-urlencoded")
        );
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("qBittorrent 请求失败: HTTP " + response.statusCode() + " " + response.body());
        }
    }

    private TransportResponse sendAuthenticated(String method, String url, byte[] body, Map<String, String> headers) {
        TransportResponse response = send(method, url, body, withCookie(headers, login()));
        if (!isUnauthorized(response.statusCode())) {
            return response;
        }
        synchronized (this) {
            invalidateCookie();
            response = send(method, url, body, withCookie(headers, login()));
        }
        return response;
    }

    private Map<String, String> withCookie(Map<String, String> headers, String cookie) {
        Map<String, String> requestHeaders = new LinkedHashMap<>(headers);
        requestHeaders.put("Cookie", cookie);
        return requestHeaders;
    }

    private boolean isUnauthorized(int statusCode) {
        return statusCode == 401 || statusCode == 403;
    }

    interface Transport {
        TransportResponse send(String method, String url, byte[] body, Map<String, String> headers) throws IOException, InterruptedException;
    }

    record TransportResponse(int statusCode, String body, Map<String, List<String>> headers) {
    }

    private record Part(String filename, String contentType, byte[] content) {
        static Part text(String value) {
            return new Part(null, "text/plain; charset=UTF-8", value.getBytes(StandardCharsets.UTF_8));
        }

        static Part file(String filename, String contentType, byte[] content) {
            return new Part(filename, contentType, content);
        }
    }

    private static final class MultipartBodyBuilder {
        private MultipartBodyBuilder() {
        }

        private static byte[] build(String boundary, Map<String, Part> parts) {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            for (Map.Entry<String, Part> entry : parts.entrySet()) {
                writeUtf8(outputStream, "--" + boundary + CRLF);
                writeUtf8(outputStream, "Content-Disposition: form-data; name=\"" + entry.getKey() + '"');
                Part part = entry.getValue();
                if (part.filename() != null) {
                    writeUtf8(outputStream, "; filename=\"" + part.filename() + '"');
                }
                writeUtf8(outputStream, CRLF);
                writeUtf8(outputStream, "Content-Type: " + part.contentType() + CRLF + CRLF);
                outputStream.writeBytes(part.content());
                writeUtf8(outputStream, CRLF);
            }
            writeUtf8(outputStream, "--" + boundary + "--" + CRLF);
            return outputStream.toByteArray();
        }

        private static void writeUtf8(ByteArrayOutputStream outputStream, String value) {
            outputStream.writeBytes(value.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static final class HttpTransport implements Transport {
        private final HttpClient httpClient = DownloaderHttpClientFactory.create();

        @Override
        public TransportResponse send(String method, String url, byte[] body, Map<String, String> headers) throws IOException, InterruptedException {
            HttpRequest.BodyPublisher publisher = body.length == 0
                    ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofByteArray(body);
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url)).method(method, publisher);
            headers.forEach(builder::header);
            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return new TransportResponse(response.statusCode(), response.body(), response.headers().map());
        }
    }
}
