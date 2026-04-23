package com.yoyuzh.files.content.internal.application;

import com.yoyuzh.files.content.internal.domain.FileBlob;
import com.yoyuzh.files.content.internal.infra.FileBlobRepository;
import com.yoyuzh.files.storage.FileContentStorage;
import com.yoyuzh.files.workspace.api.WorkspaceContentBindingApi;
import com.yoyuzh.files.workspace.api.WorkspaceContentBindingFile;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Component
@Order(0)
@RequiredArgsConstructor
public class FileBlobBackfillService implements CommandLineRunner {

    private final WorkspaceContentBindingApi workspaceContentBindingApi;
    private final FileBlobRepository fileBlobRepository;
    private final FileContentStorage fileContentStorage;

    @Override
    @Transactional
    public void run(String... args) {
        backfillMissingBlobs();
    }

    @Transactional
    public void backfillMissingBlobs() {
        for (WorkspaceContentBindingFile storedFile : workspaceContentBindingApi.findFilesMissingBlobBindings()) {
            String legacyStorageName = storedFile.legacyStorageName();
            if (!StringUtils.hasText(legacyStorageName)) {
                throw new IllegalStateException("文件缺少 blob 引用且没有 legacy storage_name: " + storedFile.fileId());
            }

            String objectKey = fileContentStorage.resolveLegacyFileObjectKey(
                    storedFile.userId(),
                    storedFile.path(),
                    legacyStorageName
            );
            FileBlob blob = fileBlobRepository.findByObjectKey(objectKey)
                    .orElseGet(() -> createBlob(storedFile, objectKey));
            workspaceContentBindingApi.attachBlob(storedFile.fileId(), blob.getId());
        }
    }

    private FileBlob createBlob(WorkspaceContentBindingFile storedFile, String objectKey) {
        FileBlob blob = new FileBlob();
        blob.setObjectKey(objectKey);
        blob.setContentType(storedFile.contentType());
        blob.setSize(storedFile.size());
        return fileBlobRepository.save(blob);
    }
}
