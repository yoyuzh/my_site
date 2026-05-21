package com.yoyuzh.files.content.api;

import com.yoyuzh.platform.storage.api.StoragePolicyCapabilities;

public interface ContentAssetApi extends ContentPrimaryEntityApi {

    StoragePolicyCapabilities resolveDefaultStoragePolicyCapabilities();

    void backfillPrimaryEntities();
}
