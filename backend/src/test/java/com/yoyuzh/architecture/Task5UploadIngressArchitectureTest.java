package com.yoyuzh.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

class Task5UploadIngressArchitectureTest {

    private final JavaClasses classes = new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS).importPackages("com.yoyuzh");

    @Test
    void uploadSessionServiceMustDependOnUploadTargetPolicy() {
        ArchRule rule = classes()
                .that()
                .haveFullyQualifiedName("com.yoyuzh.files.upload.UploadSessionService")
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName("com.yoyuzh.files.upload.api.UploadTargetPolicy");

        rule.check(classes);
    }

    @Test
    void fileServiceMustDependOnUploadCompletionApi() {
        ArchRule rule = classes()
                .that()
                .haveFullyQualifiedName("com.yoyuzh.files.workspace.internal.application.FileService")
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName("com.yoyuzh.files.upload.api.UploadCompletionApi");

        rule.check(classes);
    }

    @Test
    void uploadSessionServiceMustStopDependingOnLegacyCoreUploadRules() {
        ArchRule workspaceRule = noClasses()
                .that()
                .haveFullyQualifiedName("com.yoyuzh.files.upload.UploadSessionService")
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName("com.yoyuzh.files.workspace.internal.application.WorkspaceNodeRulesService");

        ArchRule uploadRule = noClasses()
                .that()
                .haveFullyQualifiedName("com.yoyuzh.files.upload.UploadSessionService")
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName("com.yoyuzh.files.workspace.internal.application.FileUploadRulesService");

        workspaceRule.check(classes);
        uploadRule.check(classes);
    }

    @Test
    void uploadApiContractsMustNotDependOnLegacyAuthTypes() {
        ArchRule rule = noClasses()
                .that()
                .resideInAnyPackage("com.yoyuzh.files.upload.api..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("com.yoyuzh.auth..");

        rule.check(classes);
    }
}
