package com.yoyuzh.files.workspace.internal.application;

import com.yoyuzh.files.content.api.ContentAssetApi;
import com.yoyuzh.files.content.api.ContentDuplicationApi;
import com.yoyuzh.files.content.api.ContentPrimaryEntity;
import com.yoyuzh.files.content.api.ContentPrimaryEntityRelationCommand;
import com.yoyuzh.files.content.api.ContentRegistrationApi;
import com.yoyuzh.files.content.api.ContentRegistrationCommand;
import com.yoyuzh.files.content.api.RegisteredContentFile;
import com.yoyuzh.files.workspace.internal.domain.StoredFile;
import com.yoyuzh.files.workspace.internal.infra.FileListDirectoryCacheService;
import com.yoyuzh.files.workspace.internal.infra.StoredFileRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public final class RuntimeWorkspaceContentRegistrationApi implements ContentRegistrationApi, ContentDuplicationApi {

    private final StoredFileRepository storedFileRepository;
    private final ContentAssetApi contentAssetApi;
    private final FileListDirectoryCacheService fileListDirectoryCacheService;

    public RuntimeWorkspaceContentRegistrationApi(StoredFileRepository storedFileRepository,
                                                  ContentAssetApi contentAssetApi) {
        this(storedFileRepository, contentAssetApi, FileListDirectoryCacheService.noOp());
    }

    @Autowired
    public RuntimeWorkspaceContentRegistrationApi(StoredFileRepository storedFileRepository,
                                                  ContentAssetApi contentAssetApi,
                                                  FileListDirectoryCacheService fileListDirectoryCacheService) {
        this.storedFileRepository = storedFileRepository;
        this.contentAssetApi = contentAssetApi;
        this.fileListDirectoryCacheService = fileListDirectoryCacheService == null
                ? FileListDirectoryCacheService.noOp()
                : fileListDirectoryCacheService;
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
        ContentPrimaryEntity primaryEntity = contentAssetApi.createOrReferencePrimaryEntity(command.userId(), command.blob());
        StoredFile storedFile = StoredFile.blobBackedFile(
                command.userId(),
                command.normalizedPath(),
                command.filename(),
                command.contentType(),
                command.size(),
                command.blob().blobId(),
                command.blob().objectKey(),
                primaryEntity.entityId()
        );
        StoredFile savedFile = storedFileRepository.save(storedFile);
        contentAssetApi.savePrimaryEntityRelation(new ContentPrimaryEntityRelationCommand(savedFile.getId(), primaryEntity.entityId()));
        fileListDirectoryCacheService.touchDirectory(command.userId(), command.normalizedPath());
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
