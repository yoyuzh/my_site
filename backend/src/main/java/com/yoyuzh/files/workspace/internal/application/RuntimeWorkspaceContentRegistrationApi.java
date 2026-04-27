package com.yoyuzh.files.workspace.internal.application;

import com.yoyuzh.files.content.api.ContentAssetApi;
import com.yoyuzh.files.content.api.ContentDuplicationApi;
import com.yoyuzh.files.content.api.ContentPrimaryEntity;
import com.yoyuzh.files.content.api.ContentPrimaryEntityRelationCommand;
import com.yoyuzh.files.content.api.ContentRegistrationApi;
import com.yoyuzh.files.content.api.ContentRegistrationCommand;
import com.yoyuzh.files.content.api.RegisteredContentFile;
import com.yoyuzh.files.workspace.api.WorkspacePathPolicy;
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
    private final WorkspacePathPolicy workspacePathPolicy;

    public RuntimeWorkspaceContentRegistrationApi(StoredFileRepository storedFileRepository,
                                                  ContentAssetApi contentAssetApi) {
        this(storedFileRepository, contentAssetApi, FileListDirectoryCacheService.noOp(), new RuntimeWorkspacePathPolicy(storedFileRepository, null));
    }

    public RuntimeWorkspaceContentRegistrationApi(StoredFileRepository storedFileRepository,
                                                  ContentAssetApi contentAssetApi,
                                                  FileListDirectoryCacheService fileListDirectoryCacheService) {
        this(storedFileRepository, contentAssetApi, fileListDirectoryCacheService, new RuntimeWorkspacePathPolicy(storedFileRepository, null));
    }

    @Autowired
    public RuntimeWorkspaceContentRegistrationApi(StoredFileRepository storedFileRepository,
                                                  ContentAssetApi contentAssetApi,
                                                  FileListDirectoryCacheService fileListDirectoryCacheService,
                                                  WorkspacePathPolicy workspacePathPolicy) {
        this.storedFileRepository = storedFileRepository;
        this.contentAssetApi = contentAssetApi;
        this.fileListDirectoryCacheService = fileListDirectoryCacheService == null
                ? FileListDirectoryCacheService.noOp()
                : fileListDirectoryCacheService;
        this.workspacePathPolicy = workspacePathPolicy;
    }

    @Override
    public RegisteredContentFile registerBlob(ContentRegistrationCommand command) {
        String resolvedFilename = workspacePathPolicy.resolveAvailableNodeName(
                command.userId(),
                command.normalizedPath(),
                command.filename()
        );
        return persistBlobBackedFile(new ContentRegistrationCommand(
                command.userId(),
                command.normalizedPath(),
                resolvedFilename,
                command.contentType(),
                command.size(),
                command.blob()
        ));
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
