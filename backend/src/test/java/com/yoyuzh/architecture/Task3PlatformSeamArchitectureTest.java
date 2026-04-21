package com.yoyuzh.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

class Task3PlatformSeamArchitectureTest {

    private final JavaClasses classes = new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS).importPackages("com.yoyuzh");

    @Test
    void taskEnumsMustBeOwnedByPlatformJobApiPackage() {
        assertThatCode(() -> Class.forName("com.yoyuzh.platform.job.api.BackgroundTaskType"))
                .doesNotThrowAnyException();
        assertThatCode(() -> Class.forName("com.yoyuzh.platform.job.api.BackgroundTaskStatus"))
                .doesNotThrowAnyException();
        assertThatCode(() -> Class.forName("com.yoyuzh.platform.job.api.BackgroundTaskFailureCategory"))
                .doesNotThrowAnyException();

        assertThatThrownBy(() -> Class.forName("com.yoyuzh.files.tasks.BackgroundTaskType"))
                .isInstanceOf(ClassNotFoundException.class);
        assertThatThrownBy(() -> Class.forName("com.yoyuzh.files.tasks.BackgroundTaskStatus"))
                .isInstanceOf(ClassNotFoundException.class);
        assertThatThrownBy(() -> Class.forName("com.yoyuzh.files.tasks.BackgroundTaskFailureCategory"))
                .isInstanceOf(ClassNotFoundException.class);

        assertThat(classes.stream().map(JavaClass::getPackageName))
                .noneMatch(packageName -> packageName.equals("com.yoyuzh.files.tasks")
                        || packageName.startsWith("com.yoyuzh.files.tasks."));
    }

    @Test
    void storagePolicyRuntimeMustBeOwnedByPlatformStorageInternalPackages() {
        assertThat(classes.get("com.yoyuzh.platform.storage.internal.domain.StoragePolicy")).isNotNull();
        assertThat(classes.get("com.yoyuzh.platform.storage.internal.infra.StoragePolicyRepository")).isNotNull();
        assertThat(classes.get("com.yoyuzh.platform.storage.internal.application.StoragePolicyService")).isNotNull();

        assertThat(classes.stream().map(JavaClass::getPackageName))
                .noneMatch(packageName -> packageName.equals("com.yoyuzh.files.policy")
                        || packageName.startsWith("com.yoyuzh.files.policy."));
    }

    @Test
    void taskApiAndEntryPointsMustDependOnPlatformJobEnums() {
        ArchRule v2Rule = classes()
                .that()
                .haveFullyQualifiedName("com.yoyuzh.platform.job.internal.web.BackgroundTaskV2Controller")
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName("com.yoyuzh.platform.job.api.BackgroundTaskLifecycleApi");

        ArchRule v2LegacyRule = noClasses()
                .that()
                .haveFullyQualifiedName("com.yoyuzh.platform.job.internal.web.BackgroundTaskV2Controller")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("com.yoyuzh.files.tasks..");

        ArchRule adminRule = classes()
                .that()
                .haveFullyQualifiedName("com.yoyuzh.ops.admin.internal.web.AdminTaskController")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("com.yoyuzh.platform.job.api..");

        ArchRule adminStorageServiceRule = classes()
                .that()
                .haveFullyQualifiedName("com.yoyuzh.ops.admin.internal.application.AdminStorageGovernanceService")
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName("com.yoyuzh.platform.job.api.BackgroundTaskLifecycleApi");

        ArchRule adminStorageControllerRule = noClasses()
                .that()
                .haveFullyQualifiedName("com.yoyuzh.ops.admin.internal.web.AdminStoragePolicyController")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("com.yoyuzh.files.tasks..");

        ArchRule brokerConsumerRule = classes()
                .that()
                .haveFullyQualifiedName("com.yoyuzh.platform.job.internal.application.MediaMetadataTaskBrokerConsumer")
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName("com.yoyuzh.platform.job.api.BackgroundTaskLifecycleApi");

        ArchRule brokerConsumerLegacyRule = noClasses()
                .that()
                .haveFullyQualifiedName("com.yoyuzh.platform.job.internal.application.MediaMetadataTaskBrokerConsumer")
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName("com.yoyuzh.platform.job.internal.application.BackgroundTaskCommandService");

        ArchRule retryRule = classes()
                .that()
                .haveFullyQualifiedName("com.yoyuzh.platform.job.api.AsyncJobRetryPolicy")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("com.yoyuzh.platform.job.api..");

        ArchRule platformApiNoLegacyTaskEntityRule = noClasses()
                .that()
                .resideInAnyPackage("com.yoyuzh.platform.job.api..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("com.yoyuzh.platform.job.internal..", "com.yoyuzh.files.tasks..");

        ArchRule platformApiNoLegacyAuthRule = noClasses()
                .that()
                .resideInAnyPackage("com.yoyuzh.platform.job.api..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("com.yoyuzh.auth..");

        ArchRule uploadPoliciesNoLegacyDependencyRule = noClasses()
                .that()
                .resideInAnyPackage("com.yoyuzh.platform.storage.api..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "com.yoyuzh.auth..",
                        "com.yoyuzh.files.policy..",
                        "com.yoyuzh.platform.storage.internal.."
                );

        ArchRule uploadPoliciesNoUploadPackageLeakRule = noClasses()
                .that()
                .resideInAnyPackage("com.yoyuzh.platform.storage.api..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("com.yoyuzh.files.upload..");

        ArchRule fileStoragePolicyConsumersUseApiRule = noClasses()
                .that()
                .haveFullyQualifiedName("com.yoyuzh.files.workspace.internal.application.FileService")
                .or()
                .haveFullyQualifiedName("com.yoyuzh.files.workspace.internal.application.FileUploadRulesService")
                .or()
                .haveFullyQualifiedName("com.yoyuzh.files.upload.UploadSessionService")
                .or()
                .haveFullyQualifiedName("com.yoyuzh.files.upload.internal.application.RuntimeUploadTargetPolicy")
                .or()
                .haveFullyQualifiedName("com.yoyuzh.transfer.internal.application.RuntimeTransferImportApi")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "com.yoyuzh.platform.storage.internal.domain..",
                        "com.yoyuzh.platform.storage.internal.application.."
                );

        ArchRule jobStorageMigrationUsesStorageApiRule = noClasses()
                .that()
                .haveFullyQualifiedName("com.yoyuzh.platform.job.internal.application.StoragePolicyMigrationBackgroundTaskHandler")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "com.yoyuzh.platform.storage.internal.domain..",
                        "com.yoyuzh.platform.storage.internal.infra.."
                );

        v2Rule.check(classes);
        v2LegacyRule.check(classes);
        adminRule.check(classes);
        adminStorageServiceRule.check(classes);
        adminStorageControllerRule.check(classes);
        brokerConsumerRule.check(classes);
        brokerConsumerLegacyRule.check(classes);
        retryRule.check(classes);
        platformApiNoLegacyTaskEntityRule.check(classes);
        platformApiNoLegacyAuthRule.check(classes);
        uploadPoliciesNoLegacyDependencyRule.check(classes);
        uploadPoliciesNoUploadPackageLeakRule.check(classes);
        fileStoragePolicyConsumersUseApiRule.check(classes);
        jobStorageMigrationUsesStorageApiRule.check(classes);
    }
}
