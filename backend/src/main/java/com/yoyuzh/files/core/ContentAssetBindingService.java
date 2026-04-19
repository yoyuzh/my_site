package com.yoyuzh.files.core;

import com.yoyuzh.auth.User;
import com.yoyuzh.files.content.api.ContentAssetApi;
import com.yoyuzh.files.content.internal.application.RuntimeContentAssetApi;
import com.yoyuzh.files.policy.StoragePolicyCapabilities;
import com.yoyuzh.platform.storage.api.StoragePolicyQuery;

final class ContentAssetBindingService {

    private final ContentAssetApi contentAssetApi;

    ContentAssetBindingService(FileEntityRepository fileEntityRepository,
                               StoredFileEntityRepository storedFileEntityRepository,
                               StoragePolicyQuery storagePolicyQuery) {
        this.contentAssetApi = new RuntimeContentAssetApi(
                null,
                fileEntityRepository,
                storedFileEntityRepository,
                storagePolicyQuery
        );
    }

    FileEntity createOrReferencePrimaryEntity(User user, FileBlob blob) {
        return contentAssetApi.createOrReferencePrimaryEntity(user, blob);
    }

    StoragePolicyCapabilities resolveDefaultStoragePolicyCapabilities() {
        return contentAssetApi.resolveDefaultStoragePolicyCapabilities();
    }

    void savePrimaryEntityRelation(StoredFile storedFile, FileEntity primaryEntity) {
        contentAssetApi.savePrimaryEntityRelation(storedFile, primaryEntity);
    }
}
