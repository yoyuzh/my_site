package com.yoyuzh.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

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
                .haveFullyQualifiedName("com.yoyuzh.transfer.TransferController")
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName("com.yoyuzh.transfer.api.TransferSessionApi");

        ArchRule compatibilityShellRule = classes()
                .that()
                .haveFullyQualifiedName("com.yoyuzh.transfer.TransferService")
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName("com.yoyuzh.transfer.api.TransferSessionApi");

        ArchRule importShellRule = classes()
                .that()
                .haveFullyQualifiedName("com.yoyuzh.transfer.TransferImportService")
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName("com.yoyuzh.transfer.api.TransferImportApi");

        controllerRule.check(classes);
        compatibilityShellRule.check(classes);
        importShellRule.check(classes);
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
                .haveFullyQualifiedName("com.yoyuzh.transfer.TransferImportService")
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
}
