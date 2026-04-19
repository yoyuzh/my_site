package com.yoyuzh.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

class Task9BootSharedInfraArchitectureTest {

    private final JavaClasses classes = new ClassFileImporter().importPackages("com.yoyuzh");

    @Test
    void androidReleaseControllerMustDependOnAppAndroidApi() {
        ArchRule androidControllerRule = classes()
                .that()
                .haveFullyQualifiedName("com.yoyuzh.config.AndroidReleaseController")
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName("com.yoyuzh.app.android.api.AndroidReleaseQueryApi");

        ArchRule noLegacyAndroidServiceRule = noClasses()
                .that()
                .haveFullyQualifiedName("com.yoyuzh.config.AndroidReleaseController")
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName("com.yoyuzh.config.AndroidReleaseService");

        androidControllerRule.check(classes);
        noLegacyAndroidServiceRule.check(classes);
    }
}
