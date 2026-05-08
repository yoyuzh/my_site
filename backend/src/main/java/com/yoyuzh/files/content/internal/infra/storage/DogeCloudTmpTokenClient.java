package com.yoyuzh.files.content.internal.infra.storage;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yoyuzh.platform.storage.api.StorageRuntimeProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Locale;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class DogeCloudTmpTokenClient {

    private static final Logger log = LoggerFactory.getLogger(DogeCloudTmpTokenClient.class);
    private static final long SLOW_TMP_TOKEN_NANOS = 300L * 1_000_000L;

    private static final String API_PATH = "/auth/tmp_token.json";

    private final StorageRuntimeProperties.S3 properties;
    private final ObjectMapper objectMapper;
    private final Transport transport;

    DogeCloudTmpTokenClient(StorageRuntimeProperties.S3 properties, ObjectMapper objectMapper) {
        this(properties, objectMapper, new HttpTransport());
    }

    DogeCloudTmpTokenClient(StorageRuntimeProperties.S3 properties, ObjectMapper objectMapper, Transport transport) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.transport = transport;
    }

    DogeCloudTemporaryS3Session fetchSession() {
        return fetchSession(new TokenRequest("OSS_FULL", properties.getScope()));
    }

    DogeCloudTemporaryS3Session fetchUploadSession(String objectKey) {
        return fetchSession(new TokenRequest("OSS_UPLOAD", buildUploadScope(objectKey)));
    }

    private DogeCloudTemporaryS3Session fetchSession(TokenRequest request) {
        long startedAt = System.nanoTime();
        validateConfiguration();
        String body = buildRequestBody(request);
        Map<String, String> headers = Map.of(
                "Content-Type", "application/json",
                "Authorization", buildAuthorization(body)
        );

        long httpStartedAt = System.nanoTime();
        TransportResponse response = post(body, headers);
        long httpDuration = System.nanoTime() - httpStartedAt;
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            log.warn(
                    "upload-probe operation=dogecloud-tmp-token-http durationMs={} status={} scope={}",
                    formatMillis(httpDuration),
                    response.statusCode(),
                    request.scope()
            );
            throw new IllegalStateException("多吉云临时密钥请求失败: HTTP " + response.statusCode() + " " + response.body());
        }

        try {
            long parseStartedAt = System.nanoTime();
            JsonNode root = objectMapper.readTree(response.body());
            if (root.path("code").asInt() != 200) {
                throw new IllegalStateException("多吉云临时密钥请求失败: " + root.path("msg").asText("unknown"));
            }

            JsonNode data = root.path("data");
            JsonNode credentials = data.path("Credentials");
            JsonNode bucketNode = resolveBucketNode(data.path("Buckets"), request.scope());
            DogeCloudTemporaryS3Session session = new DogeCloudTemporaryS3Session(
                    requiredText(bucketNode, "s3Bucket"),
                    requiredText(bucketNode, "s3Endpoint"),
                    requiredText(credentials, "accessKeyId"),
                    requiredText(credentials, "secretAccessKey"),
                    requiredText(credentials, "sessionToken"),
                    resolveExpiresAt(data.path("ExpiredAt"))
            );
            long parseDuration = System.nanoTime() - parseStartedAt;
            long totalDuration = System.nanoTime() - startedAt;
            if (totalDuration >= SLOW_TMP_TOKEN_NANOS) {
                log.info(
                        "upload-probe operation=dogecloud-tmp-token durationMs={} httpMs={} parseMs={} scope={} bucket={}",
                        formatMillis(totalDuration),
                        formatMillis(httpDuration),
                        formatMillis(parseDuration),
                        request.scope(),
                        session.bucket()
                );
            }
            return session;
        } catch (IOException ex) {
            log.warn(
                    "upload-probe operation=dogecloud-tmp-token-parse durationMs={} scope={}",
                    formatMillis(System.nanoTime() - startedAt),
                    request.scope(),
                    ex
            );
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
        if (!properties.hasApiCredentials() || !StringUtils.hasText(properties.getScope())) {
            throw new IllegalStateException("多吉云存储配置不完整");
        }
    }

    private String buildRequestBody(TokenRequest request) {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("channel", request.channel());
        payload.put("ttl", properties.getTtlSeconds());
        payload.put("scopes", List.of(request.scope()));
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (IOException ex) {
            throw new IllegalStateException("构建多吉云临时密钥请求失败", ex);
        }
    }

    private String buildAuthorization(String body) {
        String signTarget = API_PATH + "\n" + body;
        return properties.createApiAuthorization(signTarget);
    }

    private String resolveBaseUrl() {
        String configured = properties.getApiBaseUrl();
        if (!StringUtils.hasText(configured)) {
            return "https://api.dogecloud.com";
        }
        return configured.replaceAll("/+$", "");
    }

    private JsonNode resolveBucketNode(JsonNode bucketsNode, String scope) {
        if (!bucketsNode.isArray() || bucketsNode.isEmpty()) {
            throw new IllegalStateException("多吉云临时密钥响应缺少 Buckets");
        }

        String bucketName = extractBucketName(scope);
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

    private String buildUploadScope(String objectKey) {
        String bucketName = extractBucketName(properties.getScope());
        String normalizedObjectKey = objectKey == null ? "" : objectKey.trim();
        if (!StringUtils.hasText(bucketName) || !StringUtils.hasText(normalizedObjectKey)) {
            throw new IllegalStateException("上传临时密钥 scope 构建失败");
        }
        return bucketName + ":" + normalizedObjectKey;
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

    private static String formatMillis(long durationNanos) {
        return String.format(Locale.ROOT, "%.2f", durationNanos / 1_000_000.0d);
    }

    interface Transport {
        TransportResponse post(String baseUrl, String apiPath, String body, Map<String, String> headers) throws IOException, InterruptedException;
    }

    record TransportResponse(int statusCode, String body) {
    }

    private record TokenRequest(String channel, String scope) {
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
