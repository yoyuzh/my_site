package com.yoyuzh.files.upload.api;

import com.yoyuzh.files.content.api.RegisteredContentFile;

public interface UploadCompletionApi {

    RegisteredContentFile completeStoredBlob(UploadCompletionCommand command);
}
