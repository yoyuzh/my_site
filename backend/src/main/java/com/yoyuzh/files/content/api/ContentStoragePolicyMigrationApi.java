package com.yoyuzh.files.content.api;

import java.util.List;

public interface ContentStoragePolicyMigrationApi {

    String VERSION_ENTITY_TYPE = "VERSION";

    List<ContentStoragePolicyMigrationItem> listVersionItemsByStoragePolicyId(Long storagePolicyId);

    ContentStoragePolicyMigrationInspection inspectVersionItemsByStoragePolicyId(Long storagePolicyId);

    void reassignVersionItems(Long targetStoragePolicyId, List<ContentStoragePolicyMigrationMutation> mutations);
}
