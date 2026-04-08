package com.yoyuzh.files.storage;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yoyuzh.config.FileStorageProperties;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class DogeCloudTmpTokenClient {

    private static final String API_PATH = "/auth/tmp_token.json";

    private final FileStorageProperties.S3 properties;
    private final ObjectMapper objectMapper;
    private final Transport transport;

    DogeCloudTmpTokenClient(FileStorageProperties.S3 properties, ObjectMapper objectMapper) {
        this(properties, objectMapper, new HttpTransport());
    }

    DogeCloudTmpTokenClient(FileStorageProperties.S3 properties, ObjectMapper objectMapper, Transport transport) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.transport = transport;
    }

    DogeCloudTemporaryS3Session fetchSession() {
        validateConfiguration();
        String body = buildRequestBody();
        Map<String, String> headers = Map.of(
                "Content-Type", "application/json",
                "Authorization", buildAuthorization(body)
        );

        TransportResponse response = post(body, headers);
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("多吉云临时密钥请求失败: HTTP " + response.statusCode() + " " + response.body());
        }

        try {
            JsonNode root = objectMapper.readTree(response.body());
            if (root.path("code").asInt() != 200) {
                throw new IllegalStateException("多吉云临时密钥请求失败: " + root.path("msg").asText("unknown"));
            }

            JsonNode data = root.path("data");
            JsonNode credentials = data.path("Credentials");
            JsonNode bucketNode = resolveBucketNode(data.path("Buckets"));
            return new DogeCloudTemporaryS3Session(
                    requiredText(bucketNode, "s3Bucket"),
                    requiredText(bucketNode, "s3Endpoint"),
                    requiredText(credentials, "accessKeyId"),
                    requiredText(credentials, "secretAccessKey"),
                    requiredText(credentials, "sessionToken"),
                    resolveExpiresAt(data.path("ExpiredAt"))
            );
        } catch (IOException ex) {
            throw new IllegalStateException("解析多吉云临时密钥响应失败", ex);
        }
    }

    private TransportResponse post(String body, Map<String, String> headers) {
        try {
            return transport.post(resolveBaseUrl(), API_PATH, body, headers);
        } catch (IOException ex) {
            throw new IllegalStateException("请求多吉云临时密钥失败", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("请求多吉云临时密钥被中断", ex);
        }
    }

    private void validateConfiguration() {
        if (!StringUtils.hasText(properties.getApiAccessKey())
                || !StringUtils.hasText(properties.getApiSecretKey())
                || !StringUtils.hasText(properties.getScope())) {
            throw new IllegalStateException("多吉云存储配置不完整");
        }
    }

    private String buildRequestBody() {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("channel", "OSS_FULL");
        payload.put("ttl", properties.getTtlSeconds());
        payload.put("scopes", List.of(properties.getScope()));
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (IOException ex) {
            throw new IllegalStateException("构建多吉云临时密钥请求失败", ex);
        }
    }

    private String buildAuthorization(String body) {
        String signTarget = API_PATH + "\n" + body;
        return "TOKEN " + properties.getApiAccessKey() + ":" + hmacSha1Hex(properties.getApiSecretKey(), signTarget);
    }

    private String resolveBaseUrl() {
        String configured = properties.getApiBaseUrl();
        if (!StringUtils.hasText(configured)) {
            return "https://api.dogecloud.com";
        }
        return configured.replaceAll("/+$", "");
    }

    private JsonNode resolveBucketNode(JsonNode bucketsNode) {
        if (!bucketsNode.isArray() || bucketsNode.isEmpty()) {
            throw new IllegalStateException("多吉云临时密钥响应缺少 Buckets");
        }

        String bucketName = extractBucketName(properties.getScope());
        for (JsonNode node : bucketsNode) {
            if (bucketName.equals(node.path("name").asText())) {
                return node;
            }
        }

        if (bucketsNode.size() == 1) {
            return bucketsNode.get(0);
        }
        throw new IllegalStateException("多吉云临时密钥响应中未找到匹配的存储桶: " + bucketName);
    }

    static String extractBucketName(String scope) {
        int separatorIndex = scope.indexOf(':');
        return separatorIndex >= 0 ? scope.substring(0, separatorIndex) : scope;
    }

    private static Instant resolveExpiresAt(JsonNode node) {
        long epochSeconds = node.asLong(0L);
        if (epochSeconds <= 0L) {
            throw new IllegalStateException("多吉云临时密钥响应缺少 ExpiredAt");
        }
        return Instant.ofEpochSecond(epochSeconds);
    }

    private static String requiredText(JsonNode node, String fieldName) {
        String value = node.path(fieldName).asText();
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException("多吉云临时密钥响应缺少字段: " + fieldName);
        }
        return value;
    }

    private static String hmacSha1Hex(String secret, String content) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
            byte[] digest = mac.doFinal(content.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(digest.length * 2);
            for (byte current : digest) {
                builder.append(String.format("%02x", current));
            }
            return builder.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("生成多吉云 API 签名失败", ex);
        }
    }

    interface Transport {
        TransportResponse post(String baseUrl, String apiPath, String body, Map<String, String> headers) throws IOException, InterruptedException;
    }

    record TransportResponse(int statusCode, String body) {
    }

    private static final class HttpTransport implements Transport {
        private final HttpClient httpClient = HttpClient.newHttpClient();

        @Override
        public TransportResponse post(String baseUrl, String apiPath, String body, Map<String, String> headers) throws IOException, InterruptedException {
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(URI.create(baseUrl + apiPath))
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                requestBuilder.header(entry.getKey(), entry.getValue());
            }
            HttpResponse<String> response = httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return new TransportResponse(response.statusCode(), response.body());
        }
    }
}
