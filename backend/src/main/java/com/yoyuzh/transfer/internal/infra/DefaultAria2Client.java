package com.yoyuzh.transfer.internal.infra;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class DefaultAria2Client implements Aria2Client {

    private final DownloaderProperties properties;
    private final ObjectMapper objectMapper;
    private final Transport transport;

    @Autowired
    public DefaultAria2Client(DownloaderProperties properties, ObjectMapper objectMapper) {
        this(properties, objectMapper, new HttpTransport());
    }

    DefaultAria2Client(DownloaderProperties properties, ObjectMapper objectMapper, Transport transport) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.transport = transport;
    }

    @Override
    public String submitHttp(String sourceValue, String downloadNodeId) {
        validateConfiguration();
        String requestBody = buildSubmitBody(sourceValue, downloadNodeId);
        TransportResponse response = post(requestBody);
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("aria2 提交失败: HTTP " + response.statusCode() + " " + response.body());
        }

        try {
            JsonNode root = objectMapper.readTree(response.body());
            if (root.hasNonNull("error")) {
                throw new IllegalStateException("aria2 提交失败: " + root.path("error").path("message").asText("unknown"));
            }
            String gid = root.path("result").asText();
            if (!StringUtils.hasText(gid)) {
                throw new IllegalStateException("aria2 提交失败: 响应缺少 gid");
            }
            return gid;
        } catch (IOException ex) {
            throw new IllegalStateException("解析 aria2 响应失败", ex);
        }
    }

    @Override
    public TaskStatus queryStatus(String gid) {
        validateConfiguration();
        TransportResponse response = post(buildStatusBody(gid));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("aria2 查询任务失败: HTTP " + response.statusCode() + " " + response.body());
        }
        try {
            JsonNode root = objectMapper.readTree(response.body());
            if (root.hasNonNull("error")) {
                throw new IllegalStateException("aria2 查询任务失败: " + root.path("error").path("message").asText("unknown"));
            }
            JsonNode result = root.path("result");
            return new TaskStatus(
                    gid,
                    result.path("status").asText(),
                    parseLong(result.path("totalLength").asText()),
                    parseLong(result.path("completedLength").asText()),
                    resolveOutputPath(result),
                    emptyToNull(result.path("errorCode").asText()),
                    emptyToNull(result.path("errorMessage").asText())
            );
        } catch (IOException ex) {
            throw new IllegalStateException("解析 aria2 响应失败", ex);
        }
    }

    @Override
    public void cancel(String gid) {
        validateConfiguration();
        TransportResponse response = post(buildCancelBody(gid));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("aria2 取消任务失败: HTTP " + response.statusCode() + " " + response.body());
        }
    }

    private TransportResponse post(String requestBody) {
        try {
            return transport.post(
                    normalizeBaseUrl(properties.getAria2().getBaseUrl()),
                    requestBody.getBytes(StandardCharsets.UTF_8),
                    Map.of("Content-Type", "application/json")
            );
        } catch (IOException ex) {
            throw new IllegalStateException("请求 aria2 失败", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("请求 aria2 被中断", ex);
        }
    }

    private String buildSubmitBody(String sourceValue, String downloadNodeId) {
        List<Object> params = new ArrayList<>();
        if (StringUtils.hasText(properties.getAria2().getSecret())) {
            params.add("token:" + properties.getAria2().getSecret().trim());
        }
        params.add(List.of(sourceValue));
        Map<String, Object> options = new LinkedHashMap<>();
        if (StringUtils.hasText(properties.getAria2().getDownloadDir())) {
            options.put("dir", properties.getAria2().getDownloadDir().trim());
        }
        options.put("user-agent", "Mozilla/5.0");
        options.put("disable-ipv6", "true");
        options.put("connect-timeout", "15");
        options.put("timeout", "60");
        options.put("max-tries", "2");
        options.put("retry-wait", "5");
        if (!options.isEmpty()) {
            params.add(options);
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("jsonrpc", "2.0");
        payload.put("id", "remote-download");
        payload.put("method", "aria2.addUri");
        payload.put("params", params);
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (IOException ex) {
            throw new IllegalStateException("构建 aria2 请求失败", ex);
        }
    }

    private String buildStatusBody(String gid) {
        List<Object> params = new ArrayList<>();
        if (StringUtils.hasText(properties.getAria2().getSecret())) {
            params.add("token:" + properties.getAria2().getSecret().trim());
        }
        params.add(gid);
        params.add(List.of("status", "totalLength", "completedLength", "dir", "files", "errorCode", "errorMessage"));
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("jsonrpc", "2.0");
        payload.put("id", "remote-download");
        payload.put("method", "aria2.tellStatus");
        payload.put("params", params);
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (IOException ex) {
            throw new IllegalStateException("构建 aria2 请求失败", ex);
        }
    }

    private String buildCancelBody(String gid) {
        List<Object> params = new ArrayList<>();
        if (StringUtils.hasText(properties.getAria2().getSecret())) {
            params.add("token:" + properties.getAria2().getSecret().trim());
        }
        params.add(gid);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("jsonrpc", "2.0");
        payload.put("id", "remote-download");
        payload.put("method", "aria2.forceRemove");
        payload.put("params", params);
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (IOException ex) {
            throw new IllegalStateException("构建 aria2 请求失败", ex);
        }
    }

    private long parseLong(String value) {
        if (!StringUtils.hasText(value)) {
            return 0L;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            return 0L;
        }
    }

    private String resolveOutputPath(JsonNode result) {
        JsonNode files = result.path("files");
        if (files.isArray() && files.size() > 0) {
            String path = files.get(0).path("path").asText();
            if (StringUtils.hasText(path)) {
                return path;
            }
        }
        String dir = result.path("dir").asText();
        return emptyToNull(dir);
    }

    private String emptyToNull(String value) {
        return StringUtils.hasText(value) ? value : null;
    }

    private void validateConfiguration() {
        if (!StringUtils.hasText(properties.getAria2().getBaseUrl())) {
            throw new IllegalStateException("aria2 baseUrl 未配置");
        }
    }

    private String normalizeBaseUrl(String baseUrl) {
        return baseUrl.trim().replaceAll("/+$", "");
    }

    interface Transport {
        TransportResponse post(String url, byte[] body, Map<String, String> headers) throws IOException, InterruptedException;
    }

    record TransportResponse(int statusCode, String body) {
    }

    private static final class HttpTransport implements Transport {
        private final HttpClient httpClient = DownloaderHttpClientFactory.create();

        @Override
        public TransportResponse post(String url, byte[] body, Map<String, String> headers) throws IOException, InterruptedException {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body));
            headers.forEach(builder::header);
            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return new TransportResponse(response.statusCode(), response.body());
        }
    }
}
