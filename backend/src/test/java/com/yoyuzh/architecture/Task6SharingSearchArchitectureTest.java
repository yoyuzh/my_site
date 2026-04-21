package com.yoyuzh.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

class Task6SharingSearchArchitectureTest {

    private final JavaClasses classes = new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS).importPackages("com.yoyuzh");

    @Test
    void legacySharingAndSearchServicesMustDependOnModuleApis() {
        ArchRule searchRule = classes()
                .that()
                .haveFullyQualifiedName("com.yoyuzh.files.search.FileSearchService")
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName("com.yoyuzh.files.search.api.FileSearchApi");

        searchRule.check(classes);
    }

    @Test
    void v2ControllersMustDependOnTargetSharingAndSearchApis() {
        ArchRule sharingControllerRule = classes()
                .that()
                .haveFullyQualifiedName("com.yoyuzh.files.sharing.internal.web.ShareV2Controller")
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName("com.yoyuzh.files.sharing.api.SharingApi");

        ArchRule searchControllerRule = classes()
                .that()
                .haveFullyQualifiedName("com.yoyuzh.files.search.internal.web.FileSearchV2Controller")
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName("com.yoyuzh.files.search.api.FileSearchApi");

        ArchRule fileEventsControllerRule = classes()
                .that()
                .haveFullyQualifiedName("com.yoyuzh.files.search.internal.web.FileEventsV2Controller")
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName("com.yoyuzh.files.search.api.FileEventApi");

        ArchRule legacyFileControllerDtoRule = classes()
                .that()
                .haveFullyQualifiedName("com.yoyuzh.files.workspace.internal.web.FileController")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("com.yoyuzh.files.sharing.api..");

        ArchRule legacyFileControllerSharingRule = classes()
                .that()
                .haveFullyQualifiedName("com.yoyuzh.files.workspace.internal.web.FileController")
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName("com.yoyuzh.files.sharing.api.SharingApi");

        ArchRule legacyTransferControllerDtoRule = classes()
                .that()
                .haveFullyQualifiedName("com.yoyuzh.transfer.TransferController")
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName("com.yoyuzh.files.sharing.api.ImportSharedFileRequest");

        sharingControllerRule.check(classes);
        searchControllerRule.check(classes);
        fileEventsControllerRule.check(classes);
        legacyFileControllerDtoRule.check(classes);
        legacyFileControllerSharingRule.check(classes);
        legacyTransferControllerDtoRule.check(classes);
    }

    @Test
    void sharingAndSearchApiContractsMustNotDependOnLegacyAuthTypes() {
        ArchRule noLegacySearchAuthRule = noClasses()
                .that()
                .resideInAnyPackage("com.yoyuzh.files.search.api..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("com.yoyuzh.auth..");

        ArchRule noLegacySharingAuthRule = noClasses()
                .that()
                .resideInAnyPackage("com.yoyuzh.files.sharing.api..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("com.yoyuzh.auth..");

        noLegacySearchAuthRule.check(classes);
        noLegacySharingAuthRule.check(classes);
    }

    @Test
    void fileServiceMustUseSearchApiForFileEvents() {
        ArchRule apiRule = classes()
                .that()
                .haveFullyQualifiedName("com.yoyuzh.files.workspace.internal.application.FileService")
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName("com.yoyuzh.files.search.api.FileEventApi");

        ArchRule noInternalRule = noClasses()
                .that()
                .haveFullyQualifiedName("com.yoyuzh.files.workspace.internal.application.FileService")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("com.yoyuzh.files.search.internal..", "com.yoyuzh.files.events..");

        apiRule.check(classes);
        noInternalRule.check(classes);
    }

    @Test
    void legacyFilesEventsRootMustNotExist() {
        ArchRule rule = noClasses()
                .should()
                .resideInAnyPackage("com.yoyuzh.files.events..");

        rule.check(classes);
    }

    @Test
    void legacyFilesShareRootMustNotExist() {
        ArchRule rule = noClasses()
                .should()
                .resideInAnyPackage("com.yoyuzh.files.share..");

        rule.check(classes);
    }

}
