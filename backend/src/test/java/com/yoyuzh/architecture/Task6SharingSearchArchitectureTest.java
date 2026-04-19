package com.yoyuzh.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

class Task6SharingSearchArchitectureTest {

    private final JavaClasses classes = new ClassFileImporter().importPackages("com.yoyuzh");

    @Test
    void legacySharingAndSearchServicesMustDependOnModuleApis() {
        ArchRule sharingRule = classes()
                .that()
                .haveFullyQualifiedName("com.yoyuzh.files.share.ShareV2Service")
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName("com.yoyuzh.files.sharing.api.SharingApi");

        ArchRule searchRule = classes()
                .that()
                .haveFullyQualifiedName("com.yoyuzh.files.search.FileSearchService")
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName("com.yoyuzh.files.search.api.FileSearchApi");

        sharingRule.check(classes);
        searchRule.check(classes);
    }

    @Test
    void v2ControllersMustDependOnTargetSharingAndSearchApis() {
        ArchRule sharingControllerRule = classes()
                .that()
                .haveFullyQualifiedName("com.yoyuzh.api.v2.shares.ShareV2Controller")
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName("com.yoyuzh.files.sharing.api.SharingApi");

        ArchRule searchControllerRule = classes()
                .that()
                .haveFullyQualifiedName("com.yoyuzh.api.v2.files.FileSearchV2Controller")
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName("com.yoyuzh.files.search.api.FileSearchApi");

        sharingControllerRule.check(classes);
        searchControllerRule.check(classes);
    }
}
