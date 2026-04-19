package com.yoyuzh.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

class Task4BWorkspaceOwnershipArchitectureTest {

    private final JavaClasses classes = new ClassFileImporter().importPackages("com.yoyuzh");

    @Test
    void fileServiceMustDependOnWorkspaceLifecycleApi() {
        ArchRule rule = classes()
                .that()
                .haveFullyQualifiedName("com.yoyuzh.files.core.FileService")
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName("com.yoyuzh.files.workspace.api.WorkspaceLifecycleApi");

        rule.check(classes);
    }

    @Test
    void workspaceNodeRulesServiceMustDelegateToWorkspacePathPolicy() {
        ArchRule rule = classes()
                .that()
                .haveFullyQualifiedName("com.yoyuzh.files.core.WorkspaceNodeRulesService")
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName("com.yoyuzh.files.workspace.api.WorkspacePathPolicy");

        rule.check(classes);
    }
}
