package com.yoyuzh.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import java.util.List;
import org.junit.jupiter.api.Test;

class BackendNextArchitectureRulesTest {

    private final JavaClasses classes = new ClassFileImporter().importPackages("com.yoyuzh");
    private static final List<String> MODULE_ROOTS = List.of(
            "com.yoyuzh.identity.access",
            "com.yoyuzh.files.workspace",
            "com.yoyuzh.files.content",
            "com.yoyuzh.files.upload",
            "com.yoyuzh.files.sharing",
            "com.yoyuzh.files.search",
            "com.yoyuzh.transfer",
            "com.yoyuzh.platform.job",
            "com.yoyuzh.platform.storage",
            "com.yoyuzh.ops.admin",
            "com.yoyuzh.app.android");

    @Test
    void apiPackagesMustNotDependOnInternalPackages() {
        ArchRule rule = noClasses()
                .that()
                .resideInAPackage("com.yoyuzh..api..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("com.yoyuzh..internal..");

        rule.check(classes);
    }

    @Test
    void domainPackagesMustNotDependOnOuterImplementationLayers() {
        ArchRule rule = noClasses()
                .that()
                .resideInAPackage("com.yoyuzh..internal.domain..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "com.yoyuzh..internal.web..",
                        "com.yoyuzh..internal.application..",
                        "com.yoyuzh..internal.infra..");

        rule.check(classes);
    }

    @Test
    void webPackagesMustNotDependOnDomainOrInfraPackages() {
        ArchRule rule = noClasses()
                .that()
                .resideInAPackage("com.yoyuzh..internal.web..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("com.yoyuzh..internal.domain..", "com.yoyuzh..internal.infra..");

        rule.check(classes);
    }

    @Test
    void internalPackagesMustNotBeAccessedAcrossModuleBoundaries() {
        for (String moduleRoot : MODULE_ROOTS) {
            ArchRule rule = noClasses()
                    .that()
                    .resideOutsideOfPackages(moduleRoot + "..", "com.yoyuzh.boot..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage(moduleRoot + ".internal..");

            rule.check(classes);
        }
    }
}
