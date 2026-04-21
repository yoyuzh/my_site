package com.yoyuzh.files.content.api;

import com.yoyuzh.platform.storage.api.StoragePolicyCapabilities;

public interface ContentAssetApi {

    ContentPrimaryEntity createOrReferencePrimaryEntity(Long userId, ContentBlobReference blob);

    void savePrimaryEntityRelation(ContentPrimaryEntityRelationCommand command);

    StoragePolicyCapabilities resolveDefaultStoragePolicyCapabilities();

    void backfillPrimaryEntities();
}
