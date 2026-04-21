package com.yoyuzh.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

class Task4CContentOwnershipArchitectureTest {

    private final JavaClasses classes = new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS).importPackages("com.yoyuzh");

    @Test
    void fileServiceMustDependOnContentAssetApi() {
        ArchRule rule = classes()
                .that()
                .haveFullyQualifiedName("com.yoyuzh.files.workspace.internal.application.FileService")
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName("com.yoyuzh.files.content.api.ContentAssetApi");

        rule.check(classes);
    }

    @Test
    void contentCompatibilityShellsMustDependOnContentAssetApi() {
        ArchRule bindingRule = classes()
                .that()
                .haveFullyQualifiedName("com.yoyuzh.files.content.internal.application.ContentAssetBindingService")
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName("com.yoyuzh.files.content.api.ContentAssetApi");

        ArchRule backfillRule = classes()
                .that()
                .haveFullyQualifiedName("com.yoyuzh.files.content.internal.application.FileEntityBackfillService")
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName("com.yoyuzh.files.content.api.ContentAssetApi");

        bindingRule.check(classes);
        backfillRule.check(classes);
    }
}
