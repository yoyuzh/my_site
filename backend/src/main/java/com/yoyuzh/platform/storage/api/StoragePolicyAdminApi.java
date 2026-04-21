package com.yoyuzh.platform.storage.api;

import java.util.List;

public interface StoragePolicyAdminApi {

    StoragePolicyAdminView readDefaultStoragePolicyAsAdmin();

    List<StoragePolicyAdminView> listStoragePoliciesAsAdmin();

    StoragePolicyAdminView createStoragePolicyAsAdmin(StoragePolicyAdminUpsertCommand command);

    StoragePolicyAdminView updateStoragePolicyAsAdmin(Long policyId, StoragePolicyAdminUpsertCommand command);

    StoragePolicyAdminView updateStoragePolicyStatusAsAdmin(Long policyId, boolean enabled);

    StoragePolicyMigrationCandidate buildStoragePolicyMigrationCandidate(Long sourcePolicyId, Long targetPolicyId);
}
