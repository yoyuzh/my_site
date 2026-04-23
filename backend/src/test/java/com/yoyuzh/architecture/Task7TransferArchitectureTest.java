package com.yoyuzh.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

class Task7TransferArchitectureTest {

    private final JavaClasses classes = new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS).importPackages("com.yoyuzh");

    @Test
    void transferEntryPointsMustDependOnTransferApis() {
        ArchRule controllerRule = classes()
                .that()
                .haveFullyQualifiedName("com.yoyuzh.transfer.internal.web.TransferController")
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName("com.yoyuzh.transfer.api.TransferSessionApi");

        ArchRule importShellRule = classes()
                .that()
                .haveFullyQualifiedName("com.yoyuzh.transfer.internal.application.TransferImportService")
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName("com.yoyuzh.transfer.api.TransferImportApi");

        controllerRule.check(classes);
        importShellRule.check(classes);
    }

    @Test
    void transferRootPackageMustNotOwnRuntimeClasses() {
        assertThat(classes.stream().map(javaClass -> javaClass.getPackageName()))
                .doesNotContain("com.yoyuzh.transfer");
    }

    @Test
    void transferApiContractsMustNotDependOnLegacyAuthTypes() {
        ArchRule noLegacyAuthRule = noClasses()
                .that()
                .resideInAnyPackage("com.yoyuzh.transfer.api..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("com.yoyuzh.auth..");

        noLegacyAuthRule.check(classes);
    }

    @Test
    void transferImportMustStopDependingOnLegacyFileService() {
        ArchRule legacyDependencyRule = noClasses()
                .that()
                .haveFullyQualifiedName("com.yoyuzh.transfer.internal.application.TransferImportService")
                .or()
                .haveFullyQualifiedName("com.yoyuzh.transfer.internal.application.RuntimeTransferImportApi")
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName("com.yoyuzh.files.workspace.internal.application.FileService");

        ArchRule workspaceAndContentRule = classes()
                .that()
                .haveFullyQualifiedName("com.yoyuzh.transfer.internal.application.RuntimeTransferImportApi")
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName("com.yoyuzh.files.workspace.api.WorkspacePathPolicy");

        ArchRule contentRule = classes()
                .that()
                .haveFullyQualifiedName("com.yoyuzh.transfer.internal.application.RuntimeTransferImportApi")
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName("com.yoyuzh.files.content.api.ContentRegistrationApi");

        legacyDependencyRule.check(classes);
        workspaceAndContentRule.check(classes);
        contentRule.check(classes);
    }

    @Test
    void transferRuntimeApplicationMustUseApiSeamsForOtherModules() {
        ArchRule rule = noClasses()
                .that()
                .haveFullyQualifiedName("com.yoyuzh.transfer.internal.application.RuntimeTransferImportApi")
                .or()
                .haveFullyQualifiedName("com.yoyuzh.transfer.internal.application.RuntimeTransferSessionApi")
                .or()
                .haveFullyQualifiedName("com.yoyuzh.transfer.internal.application.OfflineTransferQuotaService")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "com.yoyuzh.identity.access.internal..",
                        "com.yoyuzh.files.content.internal..",
                        "com.yoyuzh.files.workspace.internal..",
                        "com.yoyuzh.platform.storage.internal..",
                        "com.yoyuzh.ops.admin.internal.."
                );

        rule.check(classes);
    }
}
