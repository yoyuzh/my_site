package com.yoyuzh.app.android.internal.application;

import com.yoyuzh.app.android.api.AndroidReleaseQueryApi;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yoyuzh.app.android.api.AndroidReleaseDownload;
import com.yoyuzh.app.android.api.AndroidReleaseResponse;
import com.yoyuzh.app.android.internal.domain.AndroidReleaseMetadata;
import com.yoyuzh.app.android.internal.infra.AndroidReleaseProperties;
import com.yoyuzh.files.content.api.FileContentStorage;
import com.yoyuzh.infra.cache.RedisCacheNames;
import com.yoyuzh.shared.kernel.BusinessException;
import com.yoyuzh.shared.kernel.ErrorCode;
import java.io.IOException;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RuntimeAndroidReleaseQueryApi implements AndroidReleaseQueryApi {

    private final FileContentStorage fileContentStorage;
    private final ObjectMapper objectMapper;
    private final AndroidReleaseProperties androidReleaseProperties;

    @Override
    @Cacheable(cacheNames = RedisCacheNames.ANDROID_RELEASE, key = "'latest'")
    public AndroidReleaseResponse getLatestRelease() {
        AndroidReleaseMetadata metadata = loadReleaseMetadata();
        return new AndroidReleaseResponse(
                buildVersionedDownloadUrl(metadata),
                metadata.fileName(),
                metadata.versionCode(),
                metadata.versionName(),
                metadata.publishedAt()
        );
    }

    @Override
    public AndroidReleaseDownload downloadLatestRelease() {
        AndroidReleaseMetadata metadata = loadReleaseMetadata();
        String objectKey = metadata.objectKey();
        String fileName = metadata.fileName();
        if (objectKey == null || objectKey.isBlank() || fileName == null || fileName.isBlank()) {
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND, "Android 安装包暂未发布");
        }

        return new AndroidReleaseDownload(
                fileName,
                fileContentStorage.readBlob(objectKey)
        );
    }

    private String buildVersionedDownloadUrl(AndroidReleaseMetadata metadata) {
        String fileName = metadata.fileName();
        if (fileName == null || fileName.isBlank()) {
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND, "Android 安装包暂未发布");
        }

        String baseUrl = androidReleaseProperties.getDownloadPublicUrl();
        String separator = baseUrl.endsWith("/") ? "" : "/";
        return baseUrl + separator + URI.create("https://placeholder/" + fileName).getPath().substring(1);
    }

    private void validateReleaseMetadata(AndroidReleaseMetadata metadata) {
        String objectKey = metadata.objectKey();
        String fileName = metadata.fileName();
        if (objectKey == null || objectKey.isBlank() || fileName == null || fileName.isBlank()) {
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND, "Android 安装包暂未发布");
        }
    }

    private AndroidReleaseMetadata loadReleaseMetadata() {
        String metadataObjectKey = androidReleaseProperties.getMetadataObjectKey();
        if (metadataObjectKey == null || metadataObjectKey.isBlank()) {
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND, "Android 安装包暂未发布");
        }

        try {
            byte[] content = fileContentStorage.readBlob(metadataObjectKey);
            AndroidReleaseMetadata metadata = objectMapper.readValue(content, AndroidReleaseMetadata.class);
            if (metadata == null) {
                throw new BusinessException(ErrorCode.FILE_NOT_FOUND, "Android 安装包暂未发布");
            }
            validateReleaseMetadata(metadata);
            return metadata;
        } catch (BusinessException ex) {
            throw ex;
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.UNKNOWN, "Android 安装包元数据读取失败");
        }
    }
}
