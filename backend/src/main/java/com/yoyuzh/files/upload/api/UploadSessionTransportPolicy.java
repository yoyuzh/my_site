package com.yoyuzh.files.upload.api;

public interface UploadSessionTransportPolicy {

    UploadSessionUploadMode resolveUploadMode(Long storagePolicyId,
                                              String multipartUploadId,
                                              Integer chunkCount);

    boolean usesTusUpload(Long storagePolicyId);
}
