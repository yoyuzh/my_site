package com.yoyuzh.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

class Task4ABridgeArchitectureTest {

    private final JavaClasses classes = new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS).importPackages("com.yoyuzh");

    @Test
    void workspaceAndContentApiPackagesMustNotDependOnInternalPackages() {
        ArchRule rule = noClasses()
                .that()
                .resideInAnyPackage(
                        "com.yoyuzh.files.workspace.api..",
                        "com.yoyuzh.files.content.api..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "com.yoyuzh.files.workspace.internal..",
                        "com.yoyuzh.files.content.internal..");

        rule.check(classes);
    }

    @Test
    void workspaceApiContractsMustNotDependOnLegacyAuthTypes() {
        ArchRule rule = noClasses()
                .that()
                .resideInAnyPackage(
                        "com.yoyuzh.files.workspace.api..",
                        "com.yoyuzh.files.content.api..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("com.yoyuzh.auth..");

        rule.check(classes);
    }

    @Test
    void contentApiContractsMustNotDependOnLegacyFilesCoreTypes() {
        ArchRule rule = noClasses()
                .that()
                .resideInAnyPackage("com.yoyuzh.files.content.api..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("com.yoyuzh.files.core..");

        rule.check(classes);
    }

    @Test
    void workspaceAndContentInternalApplicationBridgesMustStayInsideOwningModules() {
        ArchRule workspaceRule = noClasses()
                .that()
                .resideOutsideOfPackages(
                        "com.yoyuzh.files.workspace..",
                        "com.yoyuzh.boot..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("com.yoyuzh.files.workspace.internal.application..");

        ArchRule contentRule = noClasses()
                .that()
                .resideOutsideOfPackages(
                        "com.yoyuzh.files.content..")
                .and()
                .doNotHaveFullyQualifiedName("com.yoyuzh.files.workspace.internal.application.FileService")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("com.yoyuzh.files.content.internal.application..");

        workspaceRule.check(classes);
        contentRule.check(classes);
    }

    @Test
    void fileServiceMustDependOnTask4AApiSeams() {
        ArchRule workspaceRule = classes()
                .that()
                .haveFullyQualifiedName("com.yoyuzh.files.workspace.internal.application.FileService")
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName("com.yoyuzh.files.workspace.api.WorkspaceDirectoryApi");

        ArchRule workspaceMutationRule = classes()
                .that()
                .haveFullyQualifiedName("com.yoyuzh.files.workspace.internal.application.FileService")
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName("com.yoyuzh.files.workspace.api.WorkspaceMutationApi");

        ArchRule ingressRule = classes()
                .that()
                .haveFullyQualifiedName("com.yoyuzh.files.workspace.internal.application.FileService")
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName("com.yoyuzh.files.workspace.internal.application.WorkspaceFileIngressService");

        ArchRule contentRule = classes()
                .that()
                .haveFullyQualifiedName("com.yoyuzh.files.workspace.internal.application.WorkspaceFileIngressService")
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName("com.yoyuzh.files.content.api.ContentRegistrationApi");

        workspaceRule.check(classes);
        workspaceMutationRule.check(classes);
        ingressRule.check(classes);
        contentRule.check(classes);
    }

    @Test
    void fileMetadataResponseMustBeOwnedByWorkspaceApiPackage() {
        assertThatCode(() -> Class.forName("com.yoyuzh.files.workspace.api.FileMetadataResponse"))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> Class.forName("com.yoyuzh.files.core.FileMetadataResponse"))
                .isInstanceOf(ClassNotFoundException.class);
    }
}
