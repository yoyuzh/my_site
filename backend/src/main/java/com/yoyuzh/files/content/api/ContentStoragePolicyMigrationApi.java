package com.yoyuzh.files.content.api;

import java.util.List;

public interface ContentStoragePolicyMigrationApi {

    String VERSION_ENTITY_TYPE = "VERSION";

    List<ContentStoragePolicyMigrationItem> listVersionItemsByStoragePolicyId(Long storagePolicyId);

    ContentStoragePolicyMigrationInspection inspectVersionItemsByStoragePolicyId(Long storagePolicyId);

    void reassignVersionItem(Long entityId, Long blobId, Long targetStoragePolicyId, String nextObjectKey);
}
