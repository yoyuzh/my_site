package com.yoyuzh.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

class Task4DWorkspaceRulesArchitectureTest {

    private final JavaClasses classes = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.yoyuzh");

    @Test
    void workspaceNodeRulesServiceMustDependOnWorkspaceAbstractionsInsteadOfRuntimeInfrastructure() {
        ArchRule pathPolicyRule = classes()
                .that()
                .haveFullyQualifiedName("com.yoyuzh.files.workspace.internal.application.WorkspaceNodeRulesService")
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName("com.yoyuzh.files.workspace.api.WorkspacePathPolicy");

        ArchRule runtimePolicyRule = noClasses()
                .that()
                .haveFullyQualifiedName("com.yoyuzh.files.workspace.internal.application.WorkspaceNodeRulesService")
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName("com.yoyuzh.files.workspace.internal.application.RuntimeWorkspacePathPolicy");

        ArchRule repositoryRule = noClasses()
                .that()
                .haveFullyQualifiedName("com.yoyuzh.files.workspace.internal.application.WorkspaceNodeRulesService")
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName("com.yoyuzh.files.workspace.internal.infra.StoredFileRepository");

        ArchRule storageRule = noClasses()
                .that()
                .haveFullyQualifiedName("com.yoyuzh.files.workspace.internal.application.WorkspaceNodeRulesService")
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName("com.yoyuzh.files.storage.FileContentStorage");

        pathPolicyRule.check(classes);
        runtimePolicyRule.check(classes);
        repositoryRule.check(classes);
        storageRule.check(classes);
    }

    @Test
    void fileUploadRulesServiceMustUsePlatformStorageApisWithoutLeakingInternals() {
        ArchRule queryRule = classes()
                .that()
                .haveFullyQualifiedName("com.yoyuzh.files.workspace.internal.application.FileUploadRulesService")
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName("com.yoyuzh.platform.storage.api.StoragePolicyQuery");

        ArchRule constraintRule = classes()
                .that()
                .haveFullyQualifiedName("com.yoyuzh.files.workspace.internal.application.FileUploadRulesService")
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName("com.yoyuzh.platform.storage.api.UploadConstraintPolicy");

        ArchRule noInternalRule = noClasses()
                .that()
                .haveFullyQualifiedName("com.yoyuzh.files.workspace.internal.application.FileUploadRulesService")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("com.yoyuzh.platform.storage.internal..");

        queryRule.check(classes);
        constraintRule.check(classes);
        noInternalRule.check(classes);
    }
}
