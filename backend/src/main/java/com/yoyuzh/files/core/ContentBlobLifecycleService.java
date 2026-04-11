package com.yoyuzh.files.core;

import com.yoyuzh.common.BusinessException;
import com.yoyuzh.common.ErrorCode;
import com.yoyuzh.files.storage.FileContentStorage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

final class ContentBlobLifecycleService {

    private final StoredFileRepository storedFileRepository;
    private final FileBlobRepository fileBlobRepository;
    private final FileContentStorage fileContentStorage;

    ContentBlobLifecycleService(StoredFileRepository storedFileRepository,
                                FileBlobRepository fileBlobRepository,
                                FileContentStorage fileContentStorage) {
        this.storedFileRepository = storedFileRepository;
        this.fileBlobRepository = fileBlobRepository;
        this.fileContentStorage = fileContentStorage;
    }

    <T> T executeAfterBlobStored(String objectKey, Supplier<T> operation) {
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

    void cleanupWrittenBlobs(List<String> writtenBlobObjectKeys, RuntimeException ex) {
        for (String objectKey : writtenBlobObjectKeys) {
            try {
                fileContentStorage.deleteBlob(objectKey);
            } catch (RuntimeException cleanupEx) {
                ex.addSuppressed(cleanupEx);
            }
        }
    }

    FileBlob createAndSaveBlob(String objectKey, String contentType, long size) {
        FileBlob blob = new FileBlob();
        blob.setObjectKey(objectKey);
        blob.setContentType(contentType);
        blob.setSize(size);
        return fileBlobRepository.save(blob);
    }

    FileBlob getRequiredBlob(StoredFile storedFile) {
        if (storedFile.isDirectory() || storedFile.getBlob() == null) {
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND, "文件内容不存在");
        }
        return storedFile.getBlob();
    }

    List<FileBlob> collectBlobsToDelete(List<StoredFile> filesToDelete) {
        Map<Long, BlobDeletionCandidate> candidates = new HashMap<>();
        for (StoredFile file : filesToDelete) {
            if (file.getBlob() == null || file.getBlob().getId() == null) {
                continue;
            }
            BlobDeletionCandidate candidate = candidates.computeIfAbsent(
                    file.getBlob().getId(),
                    ignored -> new BlobDeletionCandidate(file.getBlob())
            );
            candidate.referencesToDelete += 1;
        }

        List<FileBlob> blobsToDelete = new ArrayList<>();
        for (BlobDeletionCandidate candidate : candidates.values()) {
            long currentReferences = storedFileRepository.countByBlobId(candidate.blob.getId());
            if (currentReferences == candidate.referencesToDelete) {
                blobsToDelete.add(candidate.blob);
            }
        }
        return blobsToDelete;
    }

    void deleteBlobs(List<FileBlob> blobsToDelete) {
        for (FileBlob blob : blobsToDelete) {
            fileContentStorage.deleteBlob(blob.getObjectKey());
            fileBlobRepository.delete(blob);
        }
    }

    private static final class BlobDeletionCandidate {
        private final FileBlob blob;
        private long referencesToDelete;

        private BlobDeletionCandidate(FileBlob blob) {
            this.blob = blob;
        }
    }
}
