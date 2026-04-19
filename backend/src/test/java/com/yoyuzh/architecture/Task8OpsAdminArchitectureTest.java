package com.yoyuzh.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

class Task8OpsAdminArchitectureTest {

    private final JavaClasses classes = new ClassFileImporter().importPackages("com.yoyuzh");

    @Test
    void adminControllersMustDependOnOpsAdminApis() {
        ArchRule settingsRule = classes()
                .that()
                .haveFullyQualifiedName("com.yoyuzh.admin.AdminSettingsController")
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName("com.yoyuzh.ops.admin.api.AdminSettingsGovernanceApi");

        ArchRule userRule = classes()
                .that()
                .haveFullyQualifiedName("com.yoyuzh.admin.AdminUserController")
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName("com.yoyuzh.ops.admin.api.AdminUserGovernanceApi");

        ArchRule resourceRule = classes()
                .that()
                .haveFullyQualifiedName("com.yoyuzh.admin.AdminResourceController")
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
                .haveFullyQualifiedName("com.yoyuzh.admin.AdminSettingsController")
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName("com.yoyuzh.admin.AdminMutableSettingsService");

        ArchRule noLegacyUserRule = noClasses()
                .that()
                .haveFullyQualifiedName("com.yoyuzh.admin.AdminUserController")
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName("com.yoyuzh.admin.AdminUserGovernanceService");

        ArchRule noLegacyResourceRule = noClasses()
                .that()
                .haveFullyQualifiedName("com.yoyuzh.admin.AdminResourceController")
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName("com.yoyuzh.admin.AdminInspectionQueryService")
                .orShould()
                .dependOnClassesThat()
                .haveFullyQualifiedName("com.yoyuzh.admin.AdminResourceGovernanceService");

        noLegacySettingsRule.check(classes);
        noLegacyUserRule.check(classes);
        noLegacyResourceRule.check(classes);
    }
}
