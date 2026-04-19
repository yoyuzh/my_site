package com.yoyuzh.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

class Task3PlatformSeamArchitectureTest {

    private final JavaClasses classes = new ClassFileImporter().importPackages("com.yoyuzh");

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
    }

    @Test
    void taskApiAndEntryPointsMustDependOnPlatformJobEnums() {
        ArchRule v2Rule = classes()
                .that()
                .haveFullyQualifiedName("com.yoyuzh.api.v2.tasks.BackgroundTaskV2Controller")
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName("com.yoyuzh.platform.job.api.BackgroundTaskLifecycleApi");

        ArchRule v2LegacyRule = noClasses()
                .that()
                .haveFullyQualifiedName("com.yoyuzh.api.v2.tasks.BackgroundTaskV2Controller")
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
                .haveFullyQualifiedName("com.yoyuzh.files.tasks.MediaMetadataTaskBrokerConsumer")
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName("com.yoyuzh.platform.job.api.BackgroundTaskLifecycleApi");

        ArchRule brokerConsumerLegacyRule = noClasses()
                .that()
                .haveFullyQualifiedName("com.yoyuzh.files.tasks.MediaMetadataTaskBrokerConsumer")
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName("com.yoyuzh.files.tasks.BackgroundTaskCommandService");

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
                .resideInAnyPackage("com.yoyuzh.files.tasks..");

        v2Rule.check(classes);
        v2LegacyRule.check(classes);
        adminRule.check(classes);
        adminStorageServiceRule.check(classes);
        adminStorageControllerRule.check(classes);
        brokerConsumerRule.check(classes);
        brokerConsumerLegacyRule.check(classes);
        retryRule.check(classes);
        platformApiNoLegacyTaskEntityRule.check(classes);
    }
}
