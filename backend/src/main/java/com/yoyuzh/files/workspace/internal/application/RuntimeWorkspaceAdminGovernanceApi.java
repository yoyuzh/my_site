package com.yoyuzh.files.workspace.internal.application;

import com.yoyuzh.files.workspace.internal.application.FileService;
import com.yoyuzh.files.workspace.internal.domain.StoredFile;
import com.yoyuzh.files.workspace.internal.infra.StoredFileRepository;
import com.yoyuzh.files.workspace.api.WorkspaceAdminFileSnapshot;
import com.yoyuzh.files.workspace.api.WorkspaceAdminFileQuery;
import com.yoyuzh.files.workspace.api.WorkspaceAdminFileView;
import com.yoyuzh.files.workspace.api.WorkspaceAdminGovernanceApi;
import com.yoyuzh.identity.access.api.IdentityUserDirectoryApi;
import com.yoyuzh.identity.access.api.IdentityUserProfileSummary;
import com.yoyuzh.shared.kernel.PageResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class RuntimeWorkspaceAdminGovernanceApi implements WorkspaceAdminGovernanceApi {

    private final StoredFileRepository storedFileRepository;
    private final FileService fileService;
    private final IdentityUserDirectoryApi identityUserDirectoryApi;

    @Autowired
    public RuntimeWorkspaceAdminGovernanceApi(StoredFileRepository storedFileRepository,
                                              FileService fileService,
                                              IdentityUserDirectoryApi identityUserDirectoryApi) {
        this.storedFileRepository = storedFileRepository;
        this.fileService = fileService;
        this.identityUserDirectoryApi = identityUserDirectoryApi;
    }

    public RuntimeWorkspaceAdminGovernanceApi(StoredFileRepository storedFileRepository,
                                              FileService fileService) {
        this(
                storedFileRepository,
                fileService,
                new IdentityUserDirectoryApi() {
                    @Override
                    public java.util.Map<Long, com.yoyuzh.identity.access.api.IdentityUserProfileSummary> findProfilesByIds(java.util.Set<Long> userIds) {
                        return java.util.Map.of();
                    }

                    @Override
                    public java.util.Optional<com.yoyuzh.identity.access.api.IdentityUserProfileSummary> findProfileById(Long userId) {
                        return java.util.Optional.empty();
                    }

                    @Override
                    public java.util.Optional<com.yoyuzh.identity.access.api.IdentityUserSnapshot> findSnapshotById(Long userId) {
                        return java.util.Optional.empty();
                    }

                    @Override
                    public java.util.Optional<com.yoyuzh.identity.access.api.IdentityUserProfileSummary> findProfileByUsername(String username) {
                        return java.util.Optional.empty();
                    }
                }
        );
    }

    @Override
    @Transactional
    public Optional<WorkspaceAdminFileSnapshot> deleteFileAsAdmin(Long fileId) {
        Optional<StoredFile> existing = storedFileRepository.findById(fileId);
        if (existing.isEmpty()) {
            return Optional.empty();
        }
        StoredFile storedFile = existing.get();
        WorkspaceAdminFileSnapshot snapshot = new WorkspaceAdminFileSnapshot(
                storedFile.getId(),
                storedFile.getUserId(),
                storedFile.getPath(),
                storedFile.getFilename(),
                storedFile.isDirectory()
        );
        fileService.delete(storedFile.getUserId(), storedFile.getId());
        return Optional.of(snapshot);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<WorkspaceAdminFileView> listFilesAsAdmin(WorkspaceAdminFileQuery query) {
        int page = query.page();
        int size = query.size();
        Page<StoredFile> result = storedFileRepository.searchAdminFiles(
                normalizeQuery(query.query()),
                normalizeQuery(query.ownerQuery()),
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
        );
        return new PageResponse<>(
                result.getContent().stream().map(this::toAdminFileView).toList(),
                result.getTotalElements(),
                page,
                size
        );
    }

    @Override
    @Transactional(readOnly = true)
    public long countFilesAsAdmin() {
        return storedFileRepository.count();
    }

    @Override
    @Transactional(readOnly = true)
    public long loadUsedStorageBytesByUserId(Long userId) {
        if (userId == null) {
            return 0L;
        }
        return storedFileRepository.sumFileSizeByUserId(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, Long> loadUsedStorageBytesByUserIds(Set<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        return storedFileRepository.sumFileSizeByUserIds(userIds).stream()
                .collect(Collectors.toMap(
                        StoredFileRepository.UserStorageUsageProjection::getUserId,
                        projection -> projection.getUsedStorageBytes() == null ? 0L : projection.getUsedStorageBytes()
                ));
    }

    private WorkspaceAdminFileView toAdminFileView(StoredFile storedFile) {
        IdentityUserProfileSummary owner = storedFile.getUserId() == null
                ? null
                : identityUserDirectoryApi.findProfileById(storedFile.getUserId()).orElse(null);
        return new WorkspaceAdminFileView(
                storedFile.getId(),
                storedFile.getFilename(),
                storedFile.getPath(),
                storedFile.getSize(),
                storedFile.getContentType(),
                storedFile.isDirectory(),
                storedFile.getCreatedAt(),
                storedFile.getUserId(),
                owner == null ? null : owner.username(),
                owner == null ? null : owner.email(),
                storedFile.isFavorite(),
                false
        );
    }

    private String normalizeQuery(String value) {
        if (value == null) {
            return "";
        }
        return value.trim();
    }
}
