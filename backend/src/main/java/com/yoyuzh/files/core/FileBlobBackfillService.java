package com.yoyuzh.files.core;

import com.yoyuzh.files.storage.FileContentStorage;
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

    private final StoredFileRepository storedFileRepository;
    private final FileBlobRepository fileBlobRepository;
    private final FileContentStorage fileContentStorage;

    @Override
    @Transactional
    public void run(String... args) {
        backfillMissingBlobs();
    }

    @Transactional
    public void backfillMissingBlobs() {
        for (StoredFile storedFile : storedFileRepository.findAllByDirectoryFalseAndBlobIsNull()) {
            String legacyStorageName = storedFile.getLegacyStorageName();
            if (!StringUtils.hasText(legacyStorageName)) {
                throw new IllegalStateException("文件缺少 blob 引用且没有 legacy storage_name: " + storedFile.getId());
            }

            String objectKey = fileContentStorage.resolveLegacyFileObjectKey(
                    storedFile.getUser().getId(),
                    storedFile.getPath(),
                    legacyStorageName
            );
            FileBlob blob = fileBlobRepository.findByObjectKey(objectKey)
                    .orElseGet(() -> createBlob(storedFile, objectKey));
            storedFile.setBlob(blob);
            storedFileRepository.save(storedFile);
        }
    }

    private FileBlob createBlob(StoredFile storedFile, String objectKey) {
        FileBlob blob = new FileBlob();
        blob.setObjectKey(objectKey);
        blob.setContentType(storedFile.getContentType());
        blob.setSize(storedFile.getSize());
        return fileBlobRepository.save(blob);
    }
}
