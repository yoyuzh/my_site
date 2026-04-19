package com.yoyuzh.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

class Task4ABridgeArchitectureTest {

    private final JavaClasses classes = new ClassFileImporter().importPackages("com.yoyuzh");

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
    void onlyFilesCoreCompatibilityShellMayDependOnWorkspaceAndContentInternalApplicationBridges() {
        ArchRule workspaceRule = noClasses()
                .that()
                .resideOutsideOfPackages(
                        "com.yoyuzh.files.workspace..",
                        "com.yoyuzh.files.core..",
                        "com.yoyuzh.boot..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("com.yoyuzh.files.workspace.internal.application..");

        ArchRule contentRule = noClasses()
                .that()
                .resideOutsideOfPackages(
                        "com.yoyuzh.files.content..",
                        "com.yoyuzh.files.core..",
                        "com.yoyuzh.boot..")
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
                .haveFullyQualifiedName("com.yoyuzh.files.core.FileService")
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName("com.yoyuzh.files.workspace.api.WorkspaceDirectoryApi");

        ArchRule workspaceMutationRule = classes()
                .that()
                .haveFullyQualifiedName("com.yoyuzh.files.core.FileService")
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName("com.yoyuzh.files.workspace.api.WorkspaceMutationApi");

        ArchRule contentRule = classes()
                .that()
                .haveFullyQualifiedName("com.yoyuzh.files.core.FileService")
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName("com.yoyuzh.files.content.api.ContentRegistrationApi");

        workspaceRule.check(classes);
        workspaceMutationRule.check(classes);
        contentRule.check(classes);
    }
}
