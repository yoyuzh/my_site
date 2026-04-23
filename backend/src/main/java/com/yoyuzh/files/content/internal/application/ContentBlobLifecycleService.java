package com.yoyuzh.files.content.internal.application;

import com.yoyuzh.files.content.api.ContentBlobLifecycleApi;
import com.yoyuzh.files.content.api.ContentBlobReference;
import com.yoyuzh.files.content.internal.domain.FileBlob;
import com.yoyuzh.files.content.internal.infra.FileBlobRepository;
import com.yoyuzh.files.workspace.api.WorkspaceContentBindingApi;
import com.yoyuzh.shared.kernel.BusinessException;
import com.yoyuzh.shared.kernel.ErrorCode;
import com.yoyuzh.files.storage.FileContentStorage;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

@Service
public final class ContentBlobLifecycleService implements ContentBlobLifecycleApi {

    private final WorkspaceContentBindingApi workspaceContentBindingApi;
    private final FileBlobRepository fileBlobRepository;
    private final FileContentStorage fileContentStorage;

    public ContentBlobLifecycleService(WorkspaceContentBindingApi workspaceContentBindingApi,
                                       FileBlobRepository fileBlobRepository,
                                       FileContentStorage fileContentStorage) {
        this.workspaceContentBindingApi = workspaceContentBindingApi;
        this.fileBlobRepository = fileBlobRepository;
        this.fileContentStorage = fileContentStorage;
    }

    @Override
    public <T> T executeAfterBlobStored(String objectKey, Supplier<T> operation) {
        try {
            return operation.get();
        } catch (RuntimeException ex) {
            try {
                fileContentStorage.deleteBlob(objectKey);
            } catch (RuntimeException cleanupEx) {
                ex.addSuppressed(cleanupEx);
            }
            throw ex;
        }
    }

    @Override
    public void cleanupWrittenBlobs(List<String> writtenBlobObjectKeys, RuntimeException ex) {
        for (String objectKey : writtenBlobObjectKeys) {
            try {
                fileContentStorage.deleteBlob(objectKey);
            } catch (RuntimeException cleanupEx) {
                ex.addSuppressed(cleanupEx);
            }
        }
    }

    public FileBlob createAndSaveBlob(String objectKey, String contentType, long size) {
        FileBlob blob = new FileBlob();
        blob.setObjectKey(objectKey);
        blob.setContentType(contentType);
        blob.setSize(size);
        return fileBlobRepository.save(blob);
    }

    @Override
    public ContentBlobReference requireBlobReference(Long blobId, boolean directory) {
        return toReference(getRequiredBlob(blobId, directory));
    }

    public FileBlob getRequiredBlob(Long blobId, boolean directory) {
        if (directory || blobId == null) {
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND, "文件内容不存在");
        }
        return fileBlobRepository.findById(blobId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FILE_NOT_FOUND, "文件内容不存在"));
    }

    @Override
    public List<ContentBlobReference> collectBlobReferencesToDelete(List<Long> blobIdsToDelete) {
        return collectBlobsToDelete(blobIdsToDelete).stream()
                .map(this::toReference)
                .toList();
    }

    public List<FileBlob> collectBlobsToDelete(List<Long> blobIdsToDelete) {
        Map<Long, BlobDeletionCandidate> candidates = new HashMap<>();
        for (Long blobId : blobIdsToDelete) {
            if (blobId == null) {
                continue;
            }
            FileBlob blob = fileBlobRepository.findById(blobId).orElse(null);
            if (blob == null) {
                continue;
            }
            BlobDeletionCandidate candidate = candidates.computeIfAbsent(
                    blob.getId(),
                    ignored -> new BlobDeletionCandidate(blob)
            );
            candidate.referencesToDelete += 1;
        }

        List<FileBlob> blobsToDelete = new ArrayList<>();
        for (BlobDeletionCandidate candidate : candidates.values()) {
            long currentReferences = workspaceContentBindingApi.countFilesByBlobId(candidate.blob.getId());
            if (currentReferences == candidate.referencesToDelete) {
                blobsToDelete.add(candidate.blob);
            }
        }
        return blobsToDelete;
    }

    @Override
    public void deleteBlobReferences(List<ContentBlobReference> blobsToDelete) {
        for (ContentBlobReference blobReference : blobsToDelete) {
            fileBlobRepository.findById(blobReference.blobId())
                    .ifPresent(blob -> {
                        fileContentStorage.deleteBlob(blob.getObjectKey());
                        fileBlobRepository.delete(blob);
                    });
        }
    }

    public void deleteBlobs(List<FileBlob> blobsToDelete) {
        for (FileBlob blob : blobsToDelete) {
            fileContentStorage.deleteBlob(blob.getObjectKey());
            fileBlobRepository.delete(blob);
        }
    }

    private ContentBlobReference toReference(FileBlob blob) {
        return new ContentBlobReference(blob.getId(), blob.getObjectKey(), blob.getContentType(), blob.getSize());
    }

    private static final class BlobDeletionCandidate {
        private final FileBlob blob;
        private long referencesToDelete;

        private BlobDeletionCandidate(FileBlob blob) {
            this.blob = blob;
        }
    }
}
