package com.yoyuzh.files.content.api;

import com.yoyuzh.auth.User;
import com.yoyuzh.files.core.FileBlob;
import com.yoyuzh.files.core.FileEntity;
import com.yoyuzh.files.core.StoredFile;
import com.yoyuzh.files.policy.StoragePolicyCapabilities;

public interface ContentAssetApi {

    FileEntity createOrReferencePrimaryEntity(User user, FileBlob blob);

    void savePrimaryEntityRelation(StoredFile storedFile, FileEntity primaryEntity);

    StoragePolicyCapabilities resolveDefaultStoragePolicyCapabilities();

    void backfillPrimaryEntities();
}
