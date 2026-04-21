package com.yoyuzh.files.upload.internal.application;

import com.yoyuzh.files.content.api.ContentRegistrationApi;
import com.yoyuzh.files.content.api.ContentBlobReference;
import com.yoyuzh.files.content.api.ContentRegistrationCommand;
import com.yoyuzh.files.content.api.RegisteredContentFile;
import com.yoyuzh.files.content.internal.domain.FileBlob;
import com.yoyuzh.files.content.internal.infra.FileBlobRepository;
import com.yoyuzh.files.storage.FileContentStorage;
import com.yoyuzh.files.upload.api.UploadCompletionApi;
import com.yoyuzh.files.upload.api.UploadCompletionCommand;
import com.yoyuzh.files.workspace.api.WorkspacePathPolicy;
import org.springframework.stereotype.Service;

@Service
public final class RuntimeUploadCompletionApi implements UploadCompletionApi {

    private final WorkspacePathPolicy workspacePathPolicy;
    private final ContentRegistrationApi contentRegistrationApi;
    private final FileBlobRepository fileBlobRepository;
    private final FileContentStorage fileContentStorage;

    public RuntimeUploadCompletionApi(WorkspacePathPolicy workspacePathPolicy,
                                      ContentRegistrationApi contentRegistrationApi,
                                      FileBlobRepository fileBlobRepository,
                                      FileContentStorage fileContentStorage) {
        this.workspacePathPolicy = workspacePathPolicy;
        this.contentRegistrationApi = contentRegistrationApi;
        this.fileBlobRepository = fileBlobRepository;
        this.fileContentStorage = fileContentStorage;
    }

    @Override
    public RegisteredContentFile completeStoredBlob(UploadCompletionCommand command) {
        try {
            fileContentStorage.completeBlobUpload(command.objectKey(), command.contentType(), command.size());
            workspacePathPolicy.ensureDirectoryHierarchy(command.userId(), command.normalizedPath());
            FileBlob blob = createAndSaveBlob(command.objectKey(), command.contentType(), command.size());
            return contentRegistrationApi.registerBlob(new ContentRegistrationCommand(
                    command.userId(),
                    command.normalizedPath(),
                    command.filename(),
                    command.contentType(),
                    command.size(),
                    new ContentBlobReference(blob.getId(), blob.getObjectKey(), blob.getContentType(), blob.getSize())
            ));
        } catch (RuntimeException ex) {
            try {
                fileContentStorage.deleteBlob(command.objectKey());
            } catch (RuntimeException cleanupEx) {
                ex.addSuppressed(cleanupEx);
            }
            throw ex;
        }
    }

    private FileBlob createAndSaveBlob(String objectKey, String contentType, long size) {
        FileBlob blob = new FileBlob();
        blob.setObjectKey(objectKey);
        blob.setContentType(contentType);
        blob.setSize(size);
        return fileBlobRepository.save(blob);
    }
}
