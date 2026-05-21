package com.yoyuzh.files.upload.internal.application;

import com.yoyuzh.files.upload.api.UploadSessionTransportPolicy;
import com.yoyuzh.files.upload.api.UploadSessionUploadMode;
import com.yoyuzh.platform.storage.api.StoragePolicyDescriptor;
import com.yoyuzh.platform.storage.api.StoragePolicyQuery;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class RuntimeUploadSessionTransportPolicy implements UploadSessionTransportPolicy {

    private final StoragePolicyQuery storagePolicyQuery;
    private final UploadPolicyResolver uploadPolicyResolver;
    private final UploadSessionTusService uploadSessionTusService;

    public RuntimeUploadSessionTransportPolicy(StoragePolicyQuery storagePolicyQuery,
                                              UploadPolicyResolver uploadPolicyResolver,
                                              UploadSessionTusService uploadSessionTusService) {
        this.storagePolicyQuery = storagePolicyQuery;
        this.uploadPolicyResolver = uploadPolicyResolver;
        this.uploadSessionTusService = uploadSessionTusService;
    }

    @Override
    public UploadSessionUploadMode resolveUploadMode(Long storagePolicyId,
                                                     String multipartUploadId,
                                                     Integer chunkCount) {
        if (storagePolicyId == null) {
            if (StringUtils.hasText(multipartUploadId) || (chunkCount != null && chunkCount > 1)) {
                return UploadSessionUploadMode.DIRECT_MULTIPART;
            }
            return UploadSessionUploadMode.PROXY;
        }
        return uploadPolicyResolver.resolveUploadMode(storagePolicyQuery.readPolicyCapabilities(storagePolicyId));
    }

    @Override
    public boolean usesTusUpload(Long storagePolicyId) {
        return false;
    }
}
