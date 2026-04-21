package com.yoyuzh.files.workspace.internal.application;

import com.yoyuzh.files.workspace.internal.application.FileService;
import com.yoyuzh.files.workspace.internal.domain.StoredFile;
import com.yoyuzh.files.workspace.internal.infra.StoredFileRepository;
import com.yoyuzh.files.workspace.api.WorkspaceAdminFileSnapshot;
import com.yoyuzh.files.workspace.api.WorkspaceAdminFileQuery;
import com.yoyuzh.files.workspace.api.WorkspaceAdminFileView;
import com.yoyuzh.files.workspace.api.WorkspaceAdminGovernanceApi;
import com.yoyuzh.shared.kernel.PageResponse;
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
public class RuntimeWorkspaceAdminGovernanceApi implements WorkspaceAdminGovernanceApi {

    private final StoredFileRepository storedFileRepository;
    private final FileService fileService;

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
                storedFile.getUser() == null ? null : storedFile.getUser().getId(),
                storedFile.getPath(),
                storedFile.getFilename(),
                storedFile.isDirectory()
        );
        fileService.delete(storedFile.getUser(), storedFile.getId());
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
                PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "user.username")
                        .and(Sort.by(Sort.Direction.DESC, "createdAt")))
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
        return new WorkspaceAdminFileView(
                storedFile.getId(),
                storedFile.getFilename(),
                storedFile.getPath(),
                storedFile.getSize(),
                storedFile.getContentType(),
                storedFile.isDirectory(),
                storedFile.getCreatedAt(),
                storedFile.getUser() == null ? null : storedFile.getUser().getId(),
                storedFile.getUser() == null ? null : storedFile.getUser().getUsername(),
                storedFile.getUser() == null ? null : storedFile.getUser().getEmail()
        );
    }

    private String normalizeQuery(String value) {
        if (value == null) {
            return "";
        }
        return value.trim();
    }
}
