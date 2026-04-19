package com.yoyuzh.files.content.internal.application;

import com.yoyuzh.files.content.api.ContentAdminFileBlobQuery;
import com.yoyuzh.files.content.api.ContentAdminFileBlobView;
import com.yoyuzh.files.content.api.ContentAdminInspectionApi;
import com.yoyuzh.files.content.api.ContentEntityType;
import com.yoyuzh.files.core.FileBlob;
import com.yoyuzh.files.core.FileBlobRepository;
import com.yoyuzh.files.core.FileEntity;
import com.yoyuzh.files.core.FileEntityRepository;
import com.yoyuzh.files.core.FileEntityType;
import com.yoyuzh.files.core.StoredFileEntityRepository;
import com.yoyuzh.shared.kernel.PageResponse;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public final class RuntimeContentAdminInspectionApi implements ContentAdminInspectionApi {

    private final FileEntityRepository fileEntityRepository;
    private final FileBlobRepository fileBlobRepository;
    private final StoredFileEntityRepository storedFileEntityRepository;

    public RuntimeContentAdminInspectionApi(FileEntityRepository fileEntityRepository,
                                            FileBlobRepository fileBlobRepository,
                                            StoredFileEntityRepository storedFileEntityRepository) {
        this.fileEntityRepository = fileEntityRepository;
        this.fileBlobRepository = fileBlobRepository;
        this.storedFileEntityRepository = storedFileEntityRepository;
    }

    @Override
    public PageResponse<ContentAdminFileBlobView> listFileBlobsAsAdmin(ContentAdminFileBlobQuery query) {
        int page = query.page();
        int size = query.size();
        Page<FileEntity> result = fileEntityRepository.searchAdminEntities(
                normalizeQuery(query.userQuery()),
                query.storagePolicyId(),
                normalizeQuery(query.objectKey()),
                toLegacyType(query.entityType()),
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
        );

        List<FileEntity> entities = result.getContent();
        Map<String, FileBlob> blobsByObjectKey = loadBlobsByObjectKey(entities);
        Map<Long, StoredFileEntityRepository.FileEntityLinkStatsProjection> linkStatsByEntityId =
                loadLinkStatsByEntityId(entities);

        return new PageResponse<>(
                entities.stream()
                        .map(entity -> toAdminFileBlobView(
                                entity,
                                blobsByObjectKey.get(entity.getObjectKey()),
                                linkStatsByEntityId.get(entity.getId())
                        ))
                        .toList(),
                result.getTotalElements(),
                page,
                size
        );
    }

    @Override
    public long totalBlobSize() {
        return fileBlobRepository.sumAllBlobSize();
    }

    @Override
    public long countBlobsAsAdmin() {
        return fileBlobRepository.count();
    }

    @Override
    public long countEntitiesAsAdmin() {
        return fileEntityRepository.count();
    }

    private ContentAdminFileBlobView toAdminFileBlobView(FileEntity entity,
                                                         FileBlob blob,
                                                         StoredFileEntityRepository.FileEntityLinkStatsProjection linkStats) {
        long linkedStoredFileCount = linkStats == null || linkStats.getLinkedStoredFileCount() == null
                ? 0L
                : linkStats.getLinkedStoredFileCount();
        long linkedOwnerCount = linkStats == null || linkStats.getLinkedOwnerCount() == null
                ? 0L
                : linkStats.getLinkedOwnerCount();
        String sampleOwnerUsername = linkStats == null ? null : linkStats.getSampleOwnerUsername();
        String sampleOwnerEmail = linkStats == null ? null : linkStats.getSampleOwnerEmail();

        return new ContentAdminFileBlobView(
                entity.getId(),
                blob == null ? null : blob.getId(),
                entity.getObjectKey(),
                entity.getEntityType() == null ? null : ContentEntityType.valueOf(entity.getEntityType().name()),
                entity.getStoragePolicyId(),
                entity.getSize(),
                StringUtils.hasText(entity.getContentType()) ? entity.getContentType() : blob == null ? null : blob.getContentType(),
                entity.getReferenceCount(),
                linkedStoredFileCount,
                linkedOwnerCount,
                sampleOwnerUsername,
                sampleOwnerEmail,
                entity.getCreatedBy() == null ? null : entity.getCreatedBy().getId(),
                entity.getCreatedBy() == null ? null : entity.getCreatedBy().getUsername(),
                entity.getCreatedAt(),
                blob == null ? null : blob.getCreatedAt(),
                blob == null,
                linkedStoredFileCount == 0,
                entity.getReferenceCount() == null || entity.getReferenceCount() != linkedStoredFileCount
        );
    }

    private Map<String, FileBlob> loadBlobsByObjectKey(List<FileEntity> entities) {
        Set<String> objectKeys = entities.stream()
                .map(FileEntity::getObjectKey)
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());
        if (objectKeys.isEmpty()) {
            return Map.of();
        }
        return fileBlobRepository.findAllByObjectKeyIn(objectKeys).stream()
                .collect(Collectors.toMap(
                        FileBlob::getObjectKey,
                        Function.identity(),
                        (left, right) -> left
                ));
    }

    private Map<Long, StoredFileEntityRepository.FileEntityLinkStatsProjection> loadLinkStatsByEntityId(List<FileEntity> entities) {
        Set<Long> entityIds = entities.stream()
                .map(FileEntity::getId)
                .filter(id -> id != null)
                .collect(Collectors.toSet());
        if (entityIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return storedFileEntityRepository.findAdminLinkStatsByFileEntityIds(entityIds).stream()
                .collect(Collectors.toMap(
                        StoredFileEntityRepository.FileEntityLinkStatsProjection::getFileEntityId,
                        Function.identity()
                ));
    }

    private FileEntityType toLegacyType(ContentEntityType type) {
        if (type == null) {
            return null;
        }
        return FileEntityType.valueOf(type.name());
    }

    private String normalizeQuery(String value) {
        if (value == null) {
            return "";
        }
        return value.trim();
    }
}
