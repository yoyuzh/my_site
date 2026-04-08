package com.yoyuzh.files;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Component
@Order(1)
@RequiredArgsConstructor
public class FileEntityBackfillService implements CommandLineRunner {

    static final String PRIMARY_ENTITY_ROLE = "PRIMARY";

    private final StoredFileRepository storedFileRepository;
    private final FileEntityRepository fileEntityRepository;
    private final StoredFileEntityRepository storedFileEntityRepository;

    @Override
    @Transactional
    public void run(String... args) {
        backfillPrimaryEntities();
    }

    @Transactional
    public void backfillPrimaryEntities() {
        for (StoredFile storedFile : storedFileRepository.findAllByDirectoryFalseAndBlobIsNotNullAndPrimaryEntityIsNull()) {
            FileBlob blob = storedFile.getBlob();
            Optional<FileEntity> existingEntity = fileEntityRepository
                    .findByObjectKeyAndEntityType(blob.getObjectKey(), FileEntityType.VERSION);
            FileEntity fileEntity = existingEntity.orElseGet(() -> createEntity(storedFile, blob));

            if (existingEntity.isPresent()) {
                fileEntity.setReferenceCount(fileEntity.getReferenceCount() + 1);
                fileEntityRepository.save(fileEntity);
            }
            storedFile.setPrimaryEntity(fileEntity);
            storedFileRepository.save(storedFile);
            storedFileEntityRepository.save(createRelation(storedFile, fileEntity));
        }
    }

    private FileEntity createEntity(StoredFile storedFile, FileBlob blob) {
        FileEntity fileEntity = new FileEntity();
        fileEntity.setObjectKey(blob.getObjectKey());
        fileEntity.setSize(blob.getSize());
        fileEntity.setContentType(blob.getContentType());
        fileEntity.setEntityType(FileEntityType.VERSION);
        fileEntity.setReferenceCount(1);
        fileEntity.setCreatedBy(storedFile.getUser());
        return fileEntityRepository.save(fileEntity);
    }

    private StoredFileEntity createRelation(StoredFile storedFile, FileEntity fileEntity) {
        StoredFileEntity relation = new StoredFileEntity();
        relation.setStoredFile(storedFile);
        relation.setFileEntity(fileEntity);
        relation.setEntityRole(PRIMARY_ENTITY_ROLE);
        return relation;
    }
}
