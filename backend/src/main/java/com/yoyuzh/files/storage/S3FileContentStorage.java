package com.yoyuzh.files.storage;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yoyuzh.common.BusinessException;
import com.yoyuzh.common.ErrorCode;
import com.yoyuzh.config.FileStorageProperties;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.SdkHttpMethod;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class S3FileContentStorage implements FileContentStorage {

    private static final String DOGECLOUD_TMP_TOKEN_PATH = "/auth/tmp_token.json";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final FileStorageProperties.S3 properties;
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private TemporaryS3Session cachedSession;

    public S3FileContentStorage(FileStorageProperties storageProperties) {
        this.properties = storageProperties.getS3();
    }

    @Override
    public PreparedUpload prepareUpload(Long userId, String path, String storageName, String contentType, long size) {
        return prepareBlobUpload(path, storageName, resolveLegacyFileObjectKey(userId, path, storageName), contentType, size);
    }

    @Override
    public void upload(Long userId, String path, String storageName, MultipartFile file) {
        uploadBlob(resolveLegacyFileObjectKey(userId, path, storageName), file);
    }

    @Override
    public void completeUpload(Long userId, String path, String storageName, String contentType, long size) {
        completeBlobUpload(resolveLegacyFileObjectKey(userId, path, storageName), contentType, size);
    }

    @Override
    public byte[] readFile(Long userId, String path, String storageName) {
        return readBlob(resolveLegacyFileObjectKey(userId, path, storageName));
    }

    @Override
    public void deleteFile(Long userId, String path, String storageName) {
        deleteBlob(resolveLegacyFileObjectKey(userId, path, storageName));
    }

    @Override
    public String createDownloadUrl(Long userId, String path, String storageName, String filename) {
        return createBlobDownloadUrl(resolveLegacyFileObjectKey(userId, path, storageName), filename);
    }

    @Override
    public PreparedUpload prepareBlobUpload(String path, String filename, String objectKey, String contentType, long size) {
        PutObjectRequest.Builder requestBuilder = PutObjectRequest.builder()
                .bucket(getSession().bucket())
                .key(normalizeObjectKey(objectKey));
        if (StringUtils.hasText(contentType)) {
            requestBuilder.contentType(contentType);
        }

        try (S3Presigner presigner = createPresigner()) {
            PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                    .signatureDuration(Duration.ofSeconds(Math.max(1, properties.getTtlSeconds())))
                    .putObjectRequest(requestBuilder.build())
                    .build();
            PresignedPutObjectRequest presignedRequest = presigner.presignPutObject(presignRequest);
            return new PreparedUpload(
                    true,
                    presignedRequest.url().toString(),
                    presignedRequest.httpRequest().method() == SdkHttpMethod.PUT ? "PUT" : "POST",
                    flattenSignedHeaders(presignedRequest.signedHeaders()),
                    objectKey
            );
        }
    }

    @Override
    public void uploadBlob(String objectKey, MultipartFile file) {
        try {
            putObject(objectKey, file.getContentType(), file.getBytes());
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.UNKNOWN, "File write failed");
        }
    }

    @Override
    public void completeBlobUpload(String objectKey, String contentType, long size) {
        try (S3Client s3Client = createClient()) {
            s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(getSession().bucket())
                    .key(normalizeObjectKey(objectKey))
                    .build());
        } catch (NoSuchKeyException ex) {
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND, "File content does not exist");
        } catch (S3Exception ex) {
            throw new BusinessException(ErrorCode.UNKNOWN, "File content verification failed");
        }
    }

    @Override
    public void storeBlob(String objectKey, String contentType, byte[] content) {
        putObject(objectKey, contentType, content);
    }

    @Override
    public byte[] readBlob(String objectKey) {
        try (S3Client s3Client = createClient()) {
            ResponseBytes<?> response = s3Client.getObjectAsBytes(GetObjectRequest.builder()
                    .bucket(getSession().bucket())
                    .key(normalizeObjectKey(objectKey))
                    .build());
            return response.asByteArray();
        } catch (NoSuchKeyException ex) {
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND, "File content does not exist");
        } catch (S3Exception ex) {
            throw new BusinessException(ErrorCode.UNKNOWN, "File read failed");
        }
    }

    @Override
    public void deleteBlob(String objectKey) {
        try (S3Client s3Client = createClient()) {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(getSession().bucket())
                    .key(normalizeObjectKey(objectKey))
                    .build());
        } catch (S3Exception ex) {
            throw new BusinessException(ErrorCode.UNKNOWN, "File delete failed");
        }
    }

    @Override
    public String createBlobDownloadUrl(String objectKey, String filename) {
        GetObjectRequest.Builder requestBuilder = GetObjectRequest.builder()
                .bucket(getSession().bucket())
                .key(normalizeObjectKey(objectKey));
        if (StringUtils.hasText(filename)) {
            requestBuilder.responseContentDisposition(
                    "attachment; filename*=UTF-8''" + URLEncoder.encode(filename, StandardCharsets.UTF_8)
            );
        }

        try (S3Presigner presigner = createPresigner()) {
            GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                    .signatureDuration(Duration.ofSeconds(Math.max(1, properties.getTtlSeconds())))
                    .getObjectRequest(requestBuilder.build())
                    .build();
            PresignedGetObjectRequest presignedRequest = presigner.presignGetObject(presignRequest);
            return presignedRequest.url().toString();
        }
    }

    @Override
    public void createDirectory(Long userId, String logicalPath) {
    }

    @Override
    public void ensureDirectory(Long userId, String logicalPath) {
    }

    @Override
    public void storeTransferFile(String sessionId, String storageName, String contentType, byte[] content) {
        putObject(resolveTransferObjectKey(sessionId, storageName), contentType, content);
    }

    @Override
    public byte[] readTransferFile(String sessionId, String storageName) {
        return readBlob(resolveTransferObjectKey(sessionId, storageName));
    }

    @Override
    public void deleteTransferFile(String sessionId, String storageName) {
        deleteBlob(resolveTransferObjectKey(sessionId, storageName));
    }

    @Override
    public String createTransferDownloadUrl(String sessionId, String storageName, String filename) {
        return createBlobDownloadUrl(resolveTransferObjectKey(sessionId, storageName), filename);
    }

    @Override
    public boolean supportsDirectDownload() {
        return true;
    }

    @Override
    public String resolveLegacyFileObjectKey(Long userId, String path, String storageName) {
        return "users/" + userId + "/" + normalizeRelativePath(path) + "/" + normalizeName(storageName);
    }

    private void putObject(String objectKey, String contentType, byte[] content) {
        PutObjectRequest.Builder requestBuilder = PutObjectRequest.builder()
                .bucket(getSession().bucket())
                .key(normalizeObjectKey(objectKey));
        if (StringUtils.hasText(contentType)) {
            requestBuilder.contentType(contentType);
        }

        try (S3Client s3Client = createClient()) {
            s3Client.putObject(requestBuilder.build(), RequestBody.fromBytes(content));
        } catch (S3Exception ex) {
            throw new BusinessException(ErrorCode.UNKNOWN, "File write failed");
        }
    }

    private String resolveTransferObjectKey(String sessionId, String storageName) {
        return "transfers/" + normalizeName(sessionId) + "/" + normalizeName(storageName);
    }

    private S3Client createClient() {
        TemporaryS3Session session = getSession();
        return S3Client.builder()
                .endpointOverride(session.endpointUri())
                .region(Region.of(properties.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(session.credentials()))
                .build();
    }

    private S3Presigner createPresigner() {
        TemporaryS3Session session = getSession();
        return S3Presigner.builder()
                .endpointOverride(session.endpointUri())
                .region(Region.of(properties.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(session.credentials()))
                .build();
    }

    private synchronized TemporaryS3Session getSession() {
        if (cachedSession != null && cachedSession.expiresAt().isAfter(Instant.now().plusSeconds(60))) {
            return cachedSession;
        }

        cachedSession = requestTemporaryS3Session();
        return cachedSession;
    }

    private TemporaryS3Session requestTemporaryS3Session() {
        requireText(properties.getApiAccessKey(), "Missing DogeCloud API access key");
        requireText(properties.getApiSecretKey(), "Missing DogeCloud API secret key");
        requireText(properties.getScope(), "Missing DogeCloud storage scope");

        String body = "{\"channel\":\"OSS_FULL\",\"ttl\":" + Math.max(1, properties.getTtlSeconds())
                + ",\"scopes\":[\"" + escapeJson(properties.getScope()) + "\"]}";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(trimTrailingSlash(properties.getApiBaseUrl()) + DOGECLOUD_TMP_TOKEN_PATH))
                .header("Content-Type", "application/json")
                .header("Authorization", createDogeCloudApiAuthorization(body))
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new BusinessException(ErrorCode.UNKNOWN, "DogeCloud temporary credential request failed");
            }

            JsonNode payload = OBJECT_MAPPER.readTree(response.body());
            if (payload.path("code").asInt() != 200) {
                throw new BusinessException(ErrorCode.UNKNOWN, "DogeCloud temporary credential request failed");
            }

            JsonNode data = payload.path("data");
            JsonNode credentials = data.path("Credentials");
            JsonNode bucket = selectBucket(data.path("Buckets"), extractScopeBucketName(properties.getScope()));
            Instant expiresAt = data.hasNonNull("ExpiredAt")
                    ? Instant.ofEpochSecond(data.path("ExpiredAt").asLong())
                    : Instant.now().plusSeconds(Math.max(1, properties.getTtlSeconds()));

            return new TemporaryS3Session(
                    requireText(credentials.path("accessKeyId").asText(null), "Missing DogeCloud temporary access key"),
                    requireText(credentials.path("secretAccessKey").asText(null), "Missing DogeCloud temporary secret key"),
                    requireText(credentials.path("sessionToken").asText(null), "Missing DogeCloud temporary session token"),
                    requireText(bucket.path("s3Bucket").asText(null), "Missing DogeCloud S3 bucket"),
                    toEndpointUri(requireText(bucket.path("s3Endpoint").asText(null), "Missing DogeCloud S3 endpoint")),
                    expiresAt
            );
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.UNKNOWN, "DogeCloud temporary credential response is invalid");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.UNKNOWN, "DogeCloud temporary credential request interrupted");
        }
    }

    private JsonNode selectBucket(JsonNode buckets, String bucketName) {
        if (!buckets.isArray() || buckets.isEmpty()) {
            throw new BusinessException(ErrorCode.UNKNOWN, "DogeCloud temporary credential response has no bucket");
        }

        Iterator<JsonNode> iterator = buckets.elements();
        JsonNode first = buckets.get(0);
        while (iterator.hasNext()) {
            JsonNode bucket = iterator.next();
            if (bucketName.equals(bucket.path("name").asText())) {
                return bucket;
            }
        }
        return first;
    }

    private Map<String, String> flattenSignedHeaders(Map<String, java.util.List<String>> headers) {
        Map<String, String> flattened = new HashMap<>();
        headers.forEach((key, values) -> {
            if (!values.isEmpty()) {
                flattened.put(key, String.join(",", values));
            }
        });
        return flattened;
    }

    private String createDogeCloudApiAuthorization(String body) {
        return "TOKEN " + properties.getApiAccessKey() + ":" + hmacSha1Hex(
                properties.getApiSecretKey(),
                DOGECLOUD_TMP_TOKEN_PATH + "\n" + body
        );
    }

    private String hmacSha1Hex(String secret, String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
            byte[] digest = mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte item : digest) {
                result.append(String.format("%02x", item));
            }
            return result.toString();
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.UNKNOWN, "DogeCloud authorization signing failed");
        }
    }

    private String normalizeObjectKey(String objectKey) {
        String cleaned = StringUtils.cleanPath(objectKey == null ? "" : objectKey).replace("\\", "/");
        if (!StringUtils.hasText(cleaned) || cleaned.startsWith("/") || cleaned.contains("..")) {
            throw new BusinessException(ErrorCode.UNKNOWN, "Invalid storage object key");
        }
        return cleaned;
    }

    private String normalizeRelativePath(String path) {
        String cleaned = StringUtils.cleanPath(path == null ? "" : path).replace("\\", "/");
        if (!StringUtils.hasText(cleaned) || "/".equals(cleaned)) {
            return "";
        }
        if (cleaned.startsWith("/")) {
            cleaned = cleaned.substring(1);
        }
        if (cleaned.contains("..")) {
            throw new BusinessException(ErrorCode.UNKNOWN, "Invalid storage path");
        }
        return cleaned;
    }

    private String normalizeName(String name) {
        String cleaned = StringUtils.cleanPath(name == null ? "" : name).replace("\\", "/");
        if (!StringUtils.hasText(cleaned) || cleaned.startsWith("/") || cleaned.contains("..")) {
            throw new BusinessException(ErrorCode.UNKNOWN, "Invalid storage filename");
        }
        return cleaned;
    }

    private String extractScopeBucketName(String scope) {
        int separatorIndex = scope.indexOf(':');
        return separatorIndex >= 0 ? scope.substring(0, separatorIndex) : scope;
    }

    private URI toEndpointUri(String endpoint) {
        return URI.create(endpoint.startsWith("http://") || endpoint.startsWith("https://")
                ? endpoint
                : "https://" + endpoint);
    }

    private String trimTrailingSlash(String value) {
        return value.replaceAll("/+$", "");
    }

    private String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(ErrorCode.UNKNOWN, message);
        }
        return value;
    }

    private record TemporaryS3Session(
            String accessKeyId,
            String secretAccessKey,
            String sessionToken,
            String bucket,
            URI endpointUri,
            Instant expiresAt
    ) {

        AwsSessionCredentials credentials() {
            return AwsSessionCredentials.create(accessKeyId, secretAccessKey, sessionToken);
        }
    }
}
