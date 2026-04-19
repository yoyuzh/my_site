package com.yoyuzh.files.content.internal.application;

import com.yoyuzh.auth.User;
import com.yoyuzh.files.content.api.ContentAssetApi;
import com.yoyuzh.files.content.api.ContentDuplicationApi;
import com.yoyuzh.files.content.api.ContentRegistrationApi;
import com.yoyuzh.files.content.api.ContentRegistrationCommand;
import com.yoyuzh.files.content.api.RegisteredContentFile;
import com.yoyuzh.files.core.FileBlob;
import com.yoyuzh.files.core.FileEntity;
import com.yoyuzh.files.core.FileEntityRepository;
import com.yoyuzh.files.core.StoredFile;
import com.yoyuzh.files.core.StoredFileEntityRepository;
import com.yoyuzh.files.core.StoredFileRepository;
import com.yoyuzh.platform.storage.api.StoragePolicyQuery;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public final class RuntimeContentRegistrationApi implements ContentRegistrationApi, ContentDuplicationApi {

    private final StoredFileRepository storedFileRepository;
    private final ContentAssetApi contentAssetApi;

    @Autowired
    public RuntimeContentRegistrationApi(StoredFileRepository storedFileRepository,
                                         FileEntityRepository fileEntityRepository,
                                         StoredFileEntityRepository storedFileEntityRepository,
                                         StoragePolicyQuery storagePolicyQuery) {
        this(
                storedFileRepository,
                new RuntimeContentAssetApi(
                        storedFileRepository,
                        fileEntityRepository,
                        storedFileEntityRepository,
                        storagePolicyQuery
                )
        );
    }

    public RuntimeContentRegistrationApi(StoredFileRepository storedFileRepository,
                                         ContentAssetApi contentAssetApi) {
        this.storedFileRepository = storedFileRepository;
        this.contentAssetApi = contentAssetApi;
    }

    @Override
    public RegisteredContentFile registerBlob(ContentRegistrationCommand command) {
        return persistBlobBackedFile(command);
    }

    @Override
    public RegisteredContentFile duplicateBlobBackedFile(ContentRegistrationCommand command) {
        return persistBlobBackedFile(command);
    }

    private RegisteredContentFile persistBlobBackedFile(ContentRegistrationCommand command) {
        StoredFile storedFile = new StoredFile();
        storedFile.setUser(command.user());
        storedFile.setFilename(command.filename());
        storedFile.setPath(command.normalizedPath());
        storedFile.setContentType(command.contentType());
        storedFile.setSize(command.size());
        storedFile.setDirectory(false);
        storedFile.setBlob(command.blob());
        storedFile.setLegacyStorageName(command.blob().getObjectKey());
        FileEntity primaryEntity = contentAssetApi.createOrReferencePrimaryEntity(command.user(), command.blob());
        storedFile.setPrimaryEntity(primaryEntity);
        StoredFile savedFile = storedFileRepository.save(storedFile);
        contentAssetApi.savePrimaryEntityRelation(savedFile, primaryEntity);
        return toRegisteredContentFile(savedFile);
    }

    private RegisteredContentFile toRegisteredContentFile(StoredFile storedFile) {
        return new RegisteredContentFile(
                storedFile.getId(),
                storedFile.getFilename(),
                storedFile.getPath(),
                storedFile.getSize(),
                storedFile.getContentType(),
                storedFile.isDirectory(),
                storedFile.getCreatedAt()
        );
    }
}
