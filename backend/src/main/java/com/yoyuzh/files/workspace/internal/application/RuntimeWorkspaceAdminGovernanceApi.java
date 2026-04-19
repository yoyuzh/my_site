package com.yoyuzh.files.workspace.internal.application;

import com.yoyuzh.files.core.FileService;
import com.yoyuzh.files.core.StoredFile;
import com.yoyuzh.files.core.StoredFileRepository;
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

import java.util.Optional;

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
