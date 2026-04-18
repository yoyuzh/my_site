package com.yoyuzh.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

class BackendNextArchitectureRulesTest {

    private final JavaClasses classes = new ClassFileImporter().importPackages("com.yoyuzh");

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
        ArchRule rule = classes()
                .that()
                .resideInAnyPackage(
                        "com.yoyuzh.identity.access.internal..",
                        "com.yoyuzh.files.workspace.internal..",
                        "com.yoyuzh.files.content.internal..",
                        "com.yoyuzh.files.upload.internal..",
                        "com.yoyuzh.files.sharing.internal..",
                        "com.yoyuzh.files.search.internal..",
                        "com.yoyuzh.transfer.internal..",
                        "com.yoyuzh.platform.job.internal..",
                        "com.yoyuzh.platform.storage.internal..",
                        "com.yoyuzh.ops.admin.internal..",
                        "com.yoyuzh.app.android.internal..")
                .should()
                .onlyBeAccessed()
                .byAnyPackage(
                        "com.yoyuzh.identity.access..",
                        "com.yoyuzh.files.workspace..",
                        "com.yoyuzh.files.content..",
                        "com.yoyuzh.files.upload..",
                        "com.yoyuzh.files.sharing..",
                        "com.yoyuzh.files.search..",
                        "com.yoyuzh.transfer..",
                        "com.yoyuzh.platform.job..",
                        "com.yoyuzh.platform.storage..",
                        "com.yoyuzh.ops.admin..",
                        "com.yoyuzh.app.android..",
                        "com.yoyuzh.boot..");

        rule.check(classes);
    }
}
