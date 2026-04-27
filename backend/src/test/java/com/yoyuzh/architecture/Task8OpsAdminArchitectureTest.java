package com.yoyuzh.architecture;

import static org.assertj.core.api.Assertions.assertThat;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

class Task8OpsAdminArchitectureTest {

    private final JavaClasses classes = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.yoyuzh");

    @Test
    void adminControllersMustDependOnOpsAdminApis() {
        ArchRule settingsRule = classes()
                .that()
                .haveFullyQualifiedName("com.yoyuzh.ops.admin.internal.web.AdminSettingsController")
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName("com.yoyuzh.ops.admin.api.AdminSettingsGovernanceApi");

        ArchRule userRule = classes()
                .that()
                .haveFullyQualifiedName("com.yoyuzh.ops.admin.internal.web.AdminUserController")
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName("com.yoyuzh.ops.admin.api.AdminUserGovernanceApi");

        ArchRule resourceRule = classes()
                .that()
                .haveFullyQualifiedName("com.yoyuzh.ops.admin.internal.web.AdminResourceController")
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName("com.yoyuzh.ops.admin.api.AdminResourceGovernanceApi");

        settingsRule.check(classes);
        userRule.check(classes);
        resourceRule.check(classes);
    }

    @Test
    void adminControllersMustStopDependingOnLegacyGovernanceServices() {
        ArchRule noLegacySettingsRule = noClasses()
                .that()
                .haveFullyQualifiedName("com.yoyuzh.ops.admin.internal.web.AdminSettingsController")
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName("com.yoyuzh.ops.admin.internal.application.AdminMutableSettingsService");

        ArchRule noLegacyUserRule = noClasses()
                .that()
                .haveFullyQualifiedName("com.yoyuzh.ops.admin.internal.web.AdminUserController")
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName("com.yoyuzh.ops.admin.internal.application.AdminUserGovernanceService");

        ArchRule noLegacyResourceRule = noClasses()
                .that()
                .haveFullyQualifiedName("com.yoyuzh.ops.admin.internal.web.AdminResourceController")
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName("com.yoyuzh.ops.admin.internal.application.AdminInspectionQueryService")
                .orShould()
                .dependOnClassesThat()
                .haveFullyQualifiedName("com.yoyuzh.ops.admin.internal.application.AdminResourceGovernanceService");

        noLegacySettingsRule.check(classes);
        noLegacyUserRule.check(classes);
        noLegacyResourceRule.check(classes);
    }

    @Test
    void adminTaskQueryMustDependOnPlatformJobApiAndAvoidLegacyTaskInternals() {
        ArchRule dependencyRule = classes()
                .that()
                .haveFullyQualifiedName("com.yoyuzh.ops.admin.internal.application.AdminTaskQueryService")
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName("com.yoyuzh.platform.job.api.BackgroundTaskAdminQueryApi");

        ArchRule identityDependencyRule = classes()
                .that()
                .haveFullyQualifiedName("com.yoyuzh.ops.admin.internal.application.AdminTaskQueryService")
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName("com.yoyuzh.identity.access.api.IdentityUserDirectoryApi");

        ArchRule noLegacyRule = noClasses()
                .that()
                .haveFullyQualifiedName("com.yoyuzh.ops.admin.internal.application.AdminTaskQueryService")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("com.yoyuzh.platform.job.internal.application..");

        ArchRule noLegacyIdentityRepoRule = noClasses()
                .that()
                .haveFullyQualifiedName("com.yoyuzh.ops.admin.internal.application.AdminTaskQueryService")
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName("com.yoyuzh.identity.access.internal.infra.UserRepository");

        dependencyRule.check(classes);
        identityDependencyRule.check(classes);
        noLegacyRule.check(classes);
        noLegacyIdentityRepoRule.check(classes);
    }

    @Test
    void adminAuditServiceMustUseIdentityUserDirectoryApi() {
        ArchRule dependencyRule = classes()
                .that()
                .haveFullyQualifiedName("com.yoyuzh.ops.admin.internal.application.AdminAuditService")
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName("com.yoyuzh.identity.access.api.IdentityUserDirectoryApi");

        ArchRule noLegacyRule = noClasses()
                .that()
                .haveFullyQualifiedName("com.yoyuzh.ops.admin.internal.application.AdminAuditService")
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName("com.yoyuzh.identity.access.internal.infra.UserRepository");

        dependencyRule.check(classes);
        noLegacyRule.check(classes);
    }

    @Test
    void adminResourceGovernanceServiceMustUseWorkspaceAndSharingApis() {
        ArchRule workspaceDependencyRule = classes()
                .that()
                .haveFullyQualifiedName("com.yoyuzh.ops.admin.internal.application.AdminResourceGovernanceService")
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName("com.yoyuzh.files.workspace.api.WorkspaceAdminGovernanceApi");

        ArchRule sharingDependencyRule = classes()
                .that()
                .haveFullyQualifiedName("com.yoyuzh.ops.admin.internal.application.AdminResourceGovernanceService")
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName("com.yoyuzh.files.sharing.api.SharingApi");

        ArchRule noLegacyRepositoryBypassRule = noClasses()
                .that()
                .haveFullyQualifiedName("com.yoyuzh.ops.admin.internal.application.AdminResourceGovernanceService")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("com.yoyuzh.files.core..", "com.yoyuzh.files.share..");

        workspaceDependencyRule.check(classes);
        sharingDependencyRule.check(classes);
        noLegacyRepositoryBypassRule.check(classes);
    }

    @Test
    void adminInspectionQueryServiceMustUseModuleApisForFileAndShareReadModels() {
        ArchRule workspaceDependencyRule = classes()
                .that()
                .haveFullyQualifiedName("com.yoyuzh.ops.admin.internal.application.AdminInspectionQueryService")
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName("com.yoyuzh.files.workspace.api.WorkspaceAdminGovernanceApi");

        ArchRule contentDependencyRule = classes()
                .that()
                .haveFullyQualifiedName("com.yoyuzh.ops.admin.internal.application.AdminInspectionQueryService")
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName("com.yoyuzh.files.content.api.ContentAdminInspectionApi");

        ArchRule sharingDependencyRule = classes()
                .that()
                .haveFullyQualifiedName("com.yoyuzh.ops.admin.internal.application.AdminInspectionQueryService")
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName("com.yoyuzh.files.sharing.api.SharingApi");

        ArchRule identityDependencyRule = classes()
                .that()
                .haveFullyQualifiedName("com.yoyuzh.ops.admin.internal.application.AdminInspectionQueryService")
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName("com.yoyuzh.identity.access.api.IdentityAdminSummaryApi");

        ArchRule transferDependencyRule = classes()
                .that()
                .haveFullyQualifiedName("com.yoyuzh.ops.admin.internal.application.AdminInspectionQueryService")
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName("com.yoyuzh.transfer.api.TransferAdminMetricsApi");

        ArchRule noLegacyStoredFileEntityRule = noClasses()
                .that()
                .haveFullyQualifiedName("com.yoyuzh.ops.admin.internal.application.AdminInspectionQueryService")
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName("com.yoyuzh.files.workspace.internal.domain.StoredFile");

        ArchRule noLegacyFileEntityRepositoryRule = noClasses()
                .that()
                .haveFullyQualifiedName("com.yoyuzh.ops.admin.internal.application.AdminInspectionQueryService")
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName("com.yoyuzh.files.content.internal.infra.FileEntityRepository");

        ArchRule noLegacyStoredFileRepositoryRule = noClasses()
                .that()
                .haveFullyQualifiedName("com.yoyuzh.ops.admin.internal.application.AdminInspectionQueryService")
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName("com.yoyuzh.files.workspace.internal.infra.StoredFileRepository");

        ArchRule noLegacyFileBlobRepositoryRule = noClasses()
                .that()
                .haveFullyQualifiedName("com.yoyuzh.ops.admin.internal.application.AdminInspectionQueryService")
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName("com.yoyuzh.files.content.internal.infra.FileBlobRepository");

        ArchRule noLegacyStoredFileEntityRepositoryRule = noClasses()
                .that()
                .haveFullyQualifiedName("com.yoyuzh.ops.admin.internal.application.AdminInspectionQueryService")
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName("com.yoyuzh.files.workspace.internal.domain.StoredFileEntityRepository");

        ArchRule noLegacyShareRepositoryRule = noClasses()
                .that()
                .haveFullyQualifiedName("com.yoyuzh.ops.admin.internal.application.AdminInspectionQueryService")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("com.yoyuzh.files.share..");

        ArchRule noLegacyUserRepositoryRule = noClasses()
                .that()
                .haveFullyQualifiedName("com.yoyuzh.ops.admin.internal.application.AdminInspectionQueryService")
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName("com.yoyuzh.identity.access.internal.infra.UserRepository");

        ArchRule noLegacyRegistrationInviteServiceRule = noClasses()
                .that()
                .haveFullyQualifiedName("com.yoyuzh.ops.admin.internal.application.AdminInspectionQueryService")
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName("com.yoyuzh.auth.RegistrationInviteService");

        ArchRule noLegacyOfflineTransferRepositoryRule = noClasses()
                .that()
                .haveFullyQualifiedName("com.yoyuzh.ops.admin.internal.application.AdminInspectionQueryService")
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName("com.yoyuzh.transfer.internal.infra.OfflineTransferSessionRepository");

        workspaceDependencyRule.check(classes);
        contentDependencyRule.check(classes);
        sharingDependencyRule.check(classes);
        identityDependencyRule.check(classes);
        transferDependencyRule.check(classes);
        noLegacyStoredFileEntityRule.check(classes);
        noLegacyFileEntityRepositoryRule.check(classes);
        noLegacyStoredFileRepositoryRule.check(classes);
        noLegacyFileBlobRepositoryRule.check(classes);
        noLegacyStoredFileEntityRepositoryRule.check(classes);
        noLegacyShareRepositoryRule.check(classes);
        noLegacyUserRepositoryRule.check(classes);
        noLegacyRegistrationInviteServiceRule.check(classes);
        noLegacyOfflineTransferRepositoryRule.check(classes);
    }

    @Test
    void adminConfigSnapshotServiceMustUseModuleApisForSettingsAndFilesystemSummary() {
        ArchRule identityDependencyRule = classes()
                .that()
                .haveFullyQualifiedName("com.yoyuzh.ops.admin.internal.application.AdminConfigSnapshotService")
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName("com.yoyuzh.identity.access.api.IdentityAdminSummaryApi");

        ArchRule workspaceDependencyRule = classes()
                .that()
                .haveFullyQualifiedName("com.yoyuzh.ops.admin.internal.application.AdminConfigSnapshotService")
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName("com.yoyuzh.files.workspace.api.WorkspaceAdminGovernanceApi");

        ArchRule contentDependencyRule = classes()
                .that()
                .haveFullyQualifiedName("com.yoyuzh.ops.admin.internal.application.AdminConfigSnapshotService")
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName("com.yoyuzh.files.content.api.ContentAdminInspectionApi");

        ArchRule storageDependencyRule = classes()
                .that()
                .haveFullyQualifiedName("com.yoyuzh.ops.admin.internal.application.AdminConfigSnapshotService")
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName("com.yoyuzh.platform.storage.api.StoragePolicyAdminApi");

        ArchRule noLegacyUserRepositoryRule = noClasses()
                .that()
                .haveFullyQualifiedName("com.yoyuzh.ops.admin.internal.application.AdminConfigSnapshotService")
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName("com.yoyuzh.identity.access.internal.infra.UserRepository");

        ArchRule noLegacyInviteServiceRule = noClasses()
                .that()
                .haveFullyQualifiedName("com.yoyuzh.ops.admin.internal.application.AdminConfigSnapshotService")
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName("com.yoyuzh.auth.RegistrationInviteService");

        ArchRule noLegacyStoredFileRepositoryRule = noClasses()
                .that()
                .haveFullyQualifiedName("com.yoyuzh.ops.admin.internal.application.AdminConfigSnapshotService")
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName("com.yoyuzh.files.workspace.internal.infra.StoredFileRepository");

        ArchRule noLegacyFileBlobRepositoryRule = noClasses()
                .that()
                .haveFullyQualifiedName("com.yoyuzh.ops.admin.internal.application.AdminConfigSnapshotService")
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName("com.yoyuzh.files.content.internal.infra.FileBlobRepository");

        ArchRule noLegacyFileEntityRepositoryRule = noClasses()
                .that()
                .haveFullyQualifiedName("com.yoyuzh.ops.admin.internal.application.AdminConfigSnapshotService")
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName("com.yoyuzh.files.content.internal.infra.FileEntityRepository");

        ArchRule noLegacyStoragePolicyRule = noClasses()
                .that()
                .haveFullyQualifiedName("com.yoyuzh.ops.admin.internal.application.AdminConfigSnapshotService")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("com.yoyuzh.files.policy..");

        identityDependencyRule.check(classes);
        workspaceDependencyRule.check(classes);
        contentDependencyRule.check(classes);
        storageDependencyRule.check(classes);
        noLegacyUserRepositoryRule.check(classes);
        noLegacyInviteServiceRule.check(classes);
        noLegacyStoredFileRepositoryRule.check(classes);
        noLegacyFileBlobRepositoryRule.check(classes);
        noLegacyFileEntityRepositoryRule.check(classes);
        noLegacyStoragePolicyRule.check(classes);
    }

    @Test
    void adminMutableSettingsServiceMustUseIdentityAdminSummaryApi() {
        ArchRule identityDependencyRule = classes()
                .that()
                .haveFullyQualifiedName("com.yoyuzh.ops.admin.internal.application.AdminMutableSettingsService")
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName("com.yoyuzh.identity.access.api.IdentityAdminSummaryApi");

        ArchRule noLegacyInviteServiceRule = noClasses()
                .that()
                .haveFullyQualifiedName("com.yoyuzh.ops.admin.internal.application.AdminMutableSettingsService")
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName("com.yoyuzh.auth.RegistrationInviteService");

        identityDependencyRule.check(classes);
        noLegacyInviteServiceRule.check(classes);
    }

    @Test
    void adminUserGovernanceServiceMustUseIdentityAndWorkspaceApis() {
        ArchRule identityDependencyRule = classes()
                .that()
                .haveFullyQualifiedName("com.yoyuzh.ops.admin.internal.application.AdminUserGovernanceService")
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName("com.yoyuzh.identity.access.api.IdentityAdminUserGovernanceApi");

        ArchRule workspaceDependencyRule = classes()
                .that()
                .haveFullyQualifiedName("com.yoyuzh.ops.admin.internal.application.AdminUserGovernanceService")
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName("com.yoyuzh.files.workspace.api.WorkspaceAdminGovernanceApi");

        ArchRule noLegacyAuthRule = noClasses()
                .that()
                .haveFullyQualifiedName("com.yoyuzh.ops.admin.internal.application.AdminUserGovernanceService")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("com.yoyuzh.auth..");

        ArchRule noLegacyStoredFileRepositoryRule = noClasses()
                .that()
                .haveFullyQualifiedName("com.yoyuzh.ops.admin.internal.application.AdminUserGovernanceService")
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName("com.yoyuzh.files.workspace.internal.infra.StoredFileRepository");

        identityDependencyRule.check(classes);
        workspaceDependencyRule.check(classes);
        noLegacyAuthRule.check(classes);
        noLegacyStoredFileRepositoryRule.check(classes);
    }

    @Test
    void runtimeAdminUserGovernanceApiMustNotDependOnLegacyAuthRoleTypes() {
        ArchRule noLegacyAuthRule = noClasses()
                .that()
                .haveFullyQualifiedName("com.yoyuzh.ops.admin.internal.application.RuntimeAdminUserGovernanceApi")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("com.yoyuzh.auth..");

        noLegacyAuthRule.check(classes);
    }

    @Test
    void opsAdminApplicationMustAvoidLegacyModuleBypasses() {
        ArchRule noLegacyBypassRule = noClasses()
                .that()
                .resideInAnyPackage("com.yoyuzh.ops.admin.internal.application..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "com.yoyuzh.auth..",
                        "com.yoyuzh.files.core..",
                        "com.yoyuzh.files.policy..",
                        "com.yoyuzh.files.share..",
                        "com.yoyuzh.platform.job.internal.application..",
                        "com.yoyuzh.platform.storage.internal.domain.."
                );

        noLegacyBypassRule.check(classes);
    }

    @Test
    void adminStorageServicesMustUsePlatformStorageApiAndAvoidLegacyStorageInternals() {
        ArchRule queryDependencyRule = classes()
                .that()
                .haveFullyQualifiedName("com.yoyuzh.ops.admin.internal.application.AdminStoragePolicyQueryService")
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName("com.yoyuzh.platform.storage.api.StoragePolicyAdminApi");

        ArchRule governanceDependencyRule = classes()
                .that()
                .haveFullyQualifiedName("com.yoyuzh.ops.admin.internal.application.AdminStorageGovernanceService")
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName("com.yoyuzh.platform.storage.api.StoragePolicyAdminApi");

        ArchRule noLegacyStorageRepositoryRule = noClasses()
                .that()
                .haveFullyQualifiedName("com.yoyuzh.ops.admin.internal.application.AdminStoragePolicyQueryService")
                .or()
                .haveFullyQualifiedName("com.yoyuzh.ops.admin.internal.application.AdminStorageGovernanceService")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "com.yoyuzh.files.policy..",
                        "com.yoyuzh.platform.storage.internal.domain.."
                );

        ArchRule noStoragePolicyInternalServiceRule = noClasses()
                .that()
                .haveFullyQualifiedName("com.yoyuzh.ops.admin.internal.application.AdminStoragePolicyQueryService")
                .or()
                .haveFullyQualifiedName("com.yoyuzh.ops.admin.internal.application.AdminStorageGovernanceService")
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName("com.yoyuzh.platform.storage.internal.application.StoragePolicyService");

        ArchRule noStoragePolicyInternalRepositoryRule = noClasses()
                .that()
                .haveFullyQualifiedName("com.yoyuzh.ops.admin.internal.application.AdminStoragePolicyQueryService")
                .or()
                .haveFullyQualifiedName("com.yoyuzh.ops.admin.internal.application.AdminStorageGovernanceService")
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName("com.yoyuzh.platform.storage.internal.infra.StoragePolicyRepository");

        ArchRule noLegacyStorageEntityCountRule = noClasses()
                .that()
                .haveFullyQualifiedName("com.yoyuzh.ops.admin.internal.application.AdminStorageGovernanceService")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("com.yoyuzh.files.core..");

        ArchRule noLegacyAuthRule = noClasses()
                .that()
                .haveFullyQualifiedName("com.yoyuzh.ops.admin.internal.application.AdminStorageGovernanceService")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("com.yoyuzh.auth..");

        ArchRule noAdminWebDtoRule = noClasses()
                .that()
                .haveFullyQualifiedName("com.yoyuzh.ops.admin.internal.application.AdminStorageGovernanceService")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("com.yoyuzh.ops.admin.internal.web..");

        queryDependencyRule.check(classes);
        governanceDependencyRule.check(classes);
        noLegacyStorageRepositoryRule.check(classes);
        noStoragePolicyInternalServiceRule.check(classes);
        noStoragePolicyInternalRepositoryRule.check(classes);
        noLegacyStorageEntityCountRule.check(classes);
        noLegacyAuthRule.check(classes);
        noAdminWebDtoRule.check(classes);
    }

    @Test
    void opsAdminApiMustOwnAdminGovernanceContracts() {
        assertThat(classes.get("com.yoyuzh.ops.admin.api.AdminPasswordResetResponse")).isNotNull();
        assertThat(classes.get("com.yoyuzh.ops.admin.api.AdminUserResponse")).isNotNull();
        assertThat(classes.get("com.yoyuzh.ops.admin.api.AdminFileBlobResponse")).isNotNull();
        assertThat(classes.get("com.yoyuzh.ops.admin.api.AdminFileResponse")).isNotNull();
        assertThat(classes.get("com.yoyuzh.ops.admin.api.AdminShareResponse")).isNotNull();
        assertThat(classes.get("com.yoyuzh.ops.admin.api.AdminOfflineTransferStorageLimitResponse")).isNotNull();
        assertThat(classes.get("com.yoyuzh.ops.admin.api.AdminRegistrationInviteCodeResponse")).isNotNull();
        assertThat(classes.get("com.yoyuzh.ops.admin.api.AdminSettingsResponse")).isNotNull();
        assertThat(classes.get("com.yoyuzh.ops.admin.api.AdminSettingsUpdateRequest")).isNotNull();

        assertThat(classes.stream().map(JavaClass::getFullName))
                .doesNotContain(
                        "com.yoyuzh.admin.AdminPasswordResetResponse",
                        "com.yoyuzh.admin.AdminUserResponse",
                        "com.yoyuzh.admin.AdminFileBlobResponse",
                        "com.yoyuzh.admin.AdminFileResponse",
                        "com.yoyuzh.admin.AdminShareResponse",
                        "com.yoyuzh.admin.AdminOfflineTransferStorageLimitResponse",
                        "com.yoyuzh.admin.AdminRegistrationInviteCodeResponse",
                        "com.yoyuzh.admin.AdminSettingsResponse",
                        "com.yoyuzh.admin.AdminSettingsUpdateRequest"
                );
    }

    @Test
    void opsAdminApiMustNotDependOnLegacyFilesCoreTypes() {
        ArchRule rule = noClasses()
                .that()
                .resideInAnyPackage("com.yoyuzh.ops.admin.api..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("com.yoyuzh.files.core..");

        rule.check(classes);
    }

    @Test
    void opsAdminApiMustNotDependOnLegacyAuthRoleTypes() {
        ArchRule rule = noClasses()
                .that()
                .resideInAnyPackage("com.yoyuzh.ops.admin.api..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("com.yoyuzh.auth..");

        rule.check(classes);
    }

    @Test
    void opsAdminWebMustOwnMovedAdminWebAdapters() {
        assertThat(classes.get("com.yoyuzh.ops.admin.internal.web.AdminSettingsController")).isNotNull();
        assertThat(classes.get("com.yoyuzh.ops.admin.internal.web.AdminUserController")).isNotNull();
        assertThat(classes.get("com.yoyuzh.ops.admin.internal.web.AdminResourceController")).isNotNull();
        assertThat(classes.get("com.yoyuzh.ops.admin.internal.web.AdminOverviewController")).isNotNull();
        assertThat(classes.get("com.yoyuzh.ops.admin.internal.web.AdminAuditController")).isNotNull();
        assertThat(classes.get("com.yoyuzh.ops.admin.internal.web.AdminTaskController")).isNotNull();
        assertThat(classes.get("com.yoyuzh.ops.admin.internal.web.AdminStoragePolicyController")).isNotNull();
        assertThat(classes.get("com.yoyuzh.ops.admin.internal.web.AdminAccessEvaluator")).isNotNull();

        assertThat(classes.stream().map(JavaClass::getFullName))
                .doesNotContain(
                        "com.yoyuzh.admin.AdminSettingsController",
                        "com.yoyuzh.admin.AdminUserController",
                        "com.yoyuzh.admin.AdminResourceController",
                        "com.yoyuzh.admin.AdminOverviewController",
                        "com.yoyuzh.admin.AdminAuditController",
                        "com.yoyuzh.admin.AdminTaskController",
                        "com.yoyuzh.admin.AdminStoragePolicyController",
                        "com.yoyuzh.admin.AdminAccessEvaluator",
                        "com.yoyuzh.admin.ApiRequestMetricsFilter"
                );
    }

    @Test
    void opsAdminInfraMustOwnMovedAdminPersistenceTypes() {
        assertThat(classes.get("com.yoyuzh.ops.admin.internal.infra.AdminAuditLogEntity")).isNotNull();
        assertThat(classes.get("com.yoyuzh.ops.admin.internal.infra.AdminAuditLogRepository")).isNotNull();
        assertThat(classes.get("com.yoyuzh.ops.admin.internal.infra.AdminDailyActiveUserEntity")).isNotNull();
        assertThat(classes.get("com.yoyuzh.ops.admin.internal.infra.AdminDailyActiveUserRepository")).isNotNull();
        assertThat(classes.get("com.yoyuzh.ops.admin.internal.infra.AdminMetricsState")).isNotNull();
        assertThat(classes.get("com.yoyuzh.ops.admin.internal.infra.AdminMetricsStateRepository")).isNotNull();
        assertThat(classes.get("com.yoyuzh.ops.admin.internal.infra.AdminRequestTimelinePointEntity")).isNotNull();
        assertThat(classes.get("com.yoyuzh.ops.admin.internal.infra.AdminRequestTimelinePointRepository")).isNotNull();
        assertThat(classes.get("com.yoyuzh.ops.admin.internal.infra.AdminRuntimeSettingsState")).isNotNull();
        assertThat(classes.get("com.yoyuzh.ops.admin.internal.infra.AdminRuntimeSettingsStateRepository")).isNotNull();

        assertThat(classes.stream().map(JavaClass::getFullName))
                .doesNotContain(
                        "com.yoyuzh.admin.AdminAuditLogEntity",
                        "com.yoyuzh.admin.AdminAuditLogRepository",
                        "com.yoyuzh.admin.AdminDailyActiveUserEntity",
                        "com.yoyuzh.admin.AdminDailyActiveUserRepository",
                        "com.yoyuzh.admin.AdminMetricsState",
                        "com.yoyuzh.admin.AdminMetricsStateRepository",
                        "com.yoyuzh.admin.AdminRequestTimelinePointEntity",
                        "com.yoyuzh.admin.AdminRequestTimelinePointRepository",
                        "com.yoyuzh.admin.AdminRuntimeSettingsState",
                        "com.yoyuzh.admin.AdminRuntimeSettingsStateRepository"
                );
    }

    @Test
    void opsAdminApplicationMustOwnMovedAdminOrchestrationServices() {
        assertThat(classes.get("com.yoyuzh.ops.admin.internal.application.AdminAuditQueryService")).isNotNull();
        assertThat(classes.get("com.yoyuzh.ops.admin.internal.application.AdminAuditService")).isNotNull();
        assertThat(classes.get("com.yoyuzh.ops.admin.internal.application.AdminConfigSnapshotService")).isNotNull();
        assertThat(classes.get("com.yoyuzh.ops.admin.internal.application.AdminInspectionQueryService")).isNotNull();
        assertThat(classes.get("com.yoyuzh.ops.admin.internal.application.AdminMetricsService")).isNotNull();
        assertThat(classes.get("com.yoyuzh.ops.admin.internal.application.AdminMutableSettingsService")).isNotNull();
        assertThat(classes.get("com.yoyuzh.ops.admin.internal.application.AdminResourceGovernanceService")).isNotNull();
        assertThat(classes.get("com.yoyuzh.ops.admin.internal.application.AdminRuntimeSettingsService")).isNotNull();
        assertThat(classes.get("com.yoyuzh.ops.admin.internal.application.AdminStorageGovernanceService")).isNotNull();
        assertThat(classes.get("com.yoyuzh.ops.admin.internal.application.AdminStoragePolicyQueryService")).isNotNull();
        assertThat(classes.get("com.yoyuzh.ops.admin.internal.application.AdminTaskQueryService")).isNotNull();
        assertThat(classes.get("com.yoyuzh.ops.admin.internal.application.AdminUserGovernanceService")).isNotNull();
        assertThat(classes.get("com.yoyuzh.ops.admin.internal.application.AdminAuditAction")).isNotNull();
        assertThat(classes.get("com.yoyuzh.ops.admin.internal.application.AdminAuditLogResponse")).isNotNull();
        assertThat(classes.get("com.yoyuzh.ops.admin.internal.application.AdminDailyActiveUserSummary")).isNotNull();
        assertThat(classes.get("com.yoyuzh.ops.admin.internal.application.AdminFilesystemResponse")).isNotNull();
        assertThat(classes.get("com.yoyuzh.ops.admin.internal.application.AdminMetricsSnapshot")).isNotNull();
        assertThat(classes.get("com.yoyuzh.ops.admin.internal.application.AdminRequestTimelinePoint")).isNotNull();
        assertThat(classes.get("com.yoyuzh.ops.admin.internal.application.AdminStoragePolicyResponse")).isNotNull();
        assertThat(classes.get("com.yoyuzh.ops.admin.internal.application.AdminStoragePolicyResponses")).isNotNull();
        assertThat(classes.get("com.yoyuzh.ops.admin.internal.application.AdminSummaryResponse")).isNotNull();
        assertThat(classes.get("com.yoyuzh.ops.admin.internal.application.AdminTaskLeaseState")).isNotNull();
        assertThat(classes.get("com.yoyuzh.ops.admin.internal.application.AdminTaskResponse")).isNotNull();

        assertThat(classes.stream().map(JavaClass::getFullName))
                .doesNotContain(
                        "com.yoyuzh.admin.AdminAuditQueryService",
                        "com.yoyuzh.admin.AdminAuditService",
                        "com.yoyuzh.admin.AdminConfigSnapshotService",
                        "com.yoyuzh.admin.AdminInspectionQueryService",
                        "com.yoyuzh.admin.AdminMetricsService",
                        "com.yoyuzh.admin.AdminMutableSettingsService",
                        "com.yoyuzh.admin.AdminResourceGovernanceService",
                        "com.yoyuzh.admin.AdminRuntimeSettingsService",
                        "com.yoyuzh.admin.AdminStorageGovernanceService",
                        "com.yoyuzh.admin.AdminStoragePolicyQueryService",
                        "com.yoyuzh.admin.AdminTaskQueryService",
                        "com.yoyuzh.admin.AdminUserGovernanceService",
                        "com.yoyuzh.admin.AdminAuditAction",
                        "com.yoyuzh.admin.AdminAuditLogResponse",
                        "com.yoyuzh.admin.AdminDailyActiveUserSummary",
                        "com.yoyuzh.admin.AdminFilesystemResponse",
                        "com.yoyuzh.admin.AdminMetricsSnapshot",
                        "com.yoyuzh.admin.AdminRequestTimelinePoint",
                        "com.yoyuzh.admin.AdminStoragePolicyResponse",
                        "com.yoyuzh.admin.AdminStoragePolicyResponses",
                        "com.yoyuzh.admin.AdminSummaryResponse",
                        "com.yoyuzh.admin.AdminTaskLeaseState",
                        "com.yoyuzh.admin.AdminTaskResponse"
                );
    }
}
