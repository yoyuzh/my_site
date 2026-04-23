package com.yoyuzh.files.workspace.internal.application;

import com.yoyuzh.files.workspace.api.WorkspaceFileQueryApi;
import com.yoyuzh.files.workspace.api.WorkspaceFileSnapshot;
import com.yoyuzh.files.workspace.internal.domain.StoredFile;
import com.yoyuzh.files.workspace.internal.infra.StoredFileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class RuntimeWorkspaceFileQueryApi implements WorkspaceFileQueryApi {

    private final StoredFileRepository storedFileRepository;

    public RuntimeWorkspaceFileQueryApi(StoredFileRepository storedFileRepository) {
        this.storedFileRepository = storedFileRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<WorkspaceFileSnapshot> findOwnedActiveFile(Long userId, Long fileId) {
        return storedFileRepository.findByIdAndUserIdAndDeletedAtIsNull(fileId, userId)
                .map(this::toSnapshot);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<WorkspaceFileSnapshot> findActiveFile(Long fileId) {
        return storedFileRepository.findByIdAndDeletedAtIsNull(fileId)
                .map(this::toSnapshot);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, WorkspaceFileSnapshot> findActiveFilesByIds(Set<Long> fileIds) {
        if (fileIds == null || fileIds.isEmpty()) {
            return Map.of();
        }
        return storedFileRepository.findByIdInAndDeletedAtIsNull(fileIds).stream()
                .map(this::toSnapshot)
                .collect(Collectors.toMap(WorkspaceFileSnapshot::id, Function.identity()));
    }

    @Override
    @Transactional(readOnly = true)
    public long sumFileSizeByUserId(Long userId) {
        if (userId == null) {
            return 0L;
        }
        return storedFileRepository.sumFileSizeByUserId(userId);
    }

    private WorkspaceFileSnapshot toSnapshot(StoredFile file) {
        return new WorkspaceFileSnapshot(
                file.getId(),
                file.getUserId(),
                file.getFilename(),
                file.getPath(),
                file.getSize(),
                file.getContentType(),
                file.isDirectory(),
                file.getBlobId(),
                file.getCreatedAt()
        );
    }
}
