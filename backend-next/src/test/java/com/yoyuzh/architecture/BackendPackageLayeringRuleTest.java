package com.yoyuzh.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

class BackendPackageLayeringRuleTest {

    private final JavaClasses classes = new ClassFileImporter().importPackages("com.yoyuzh");

    @Test
    void applicationPackagesMustNotDependOnWebPackages() {
        ArchRule rule = noClasses()
                .that()
                .resideInAPackage("com.yoyuzh..internal.application..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("com.yoyuzh..internal.web..");

        rule.check(classes);
    }

    @Test
    void webPackagesMustNotDependOnRepositoryTypes() {
        ArchRule rule = noClasses()
                .that()
                .resideInAPackage("com.yoyuzh..internal.web..")
                .should()
                .dependOnClassesThat()
                .haveSimpleNameEndingWith("Repository");

        rule.check(classes);
    }
}
