package com.yoyuzh.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;

class Task6Dep001GlobalArchitectureTest {

    private static final List<String> MODULES = List.of(
            "identity.access",
            "platform.storage",
            "files.workspace",
            "files.sharing",
            "platform.job",
            "files.content",
            "files.search",
            "files.upload",
            "shared.kernel",
            "app.android",
            "ops.admin",
            "transfer",
            "infra",
            "boot"
    );

    private final JavaClasses classes = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.yoyuzh");

    @Test
    void productionClassesMustNotDependOnAnotherModulesInternalPackage() {
        ArchRule rule = classes()
                .that()
                .resideInAPackage("com.yoyuzh..")
                .should(notDependOnAnotherModulesInternalPackage());

        rule.check(classes);
    }

    @Test
    void legacyFilesStoragePackageMustStayRemoved() {
        ArchRule rule = noClasses()
                .should()
                .resideInAPackage("com.yoyuzh.files.storage..");

        rule.check(classes);
    }

    @Test
    void contentStorageAdaptersMustStayPrivateToContentModule() {
        ArchRule rule = noClasses()
                .that()
                .resideOutsideOfPackage("com.yoyuzh.files.content..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("com.yoyuzh.files.content.internal.infra.storage..");

        rule.check(classes);
    }

    @Test
    void productionTransactionalBoundariesMustUseSpringTransactionalAnnotation() {
        ArchRule rule = noClasses()
                .that()
                .resideInAPackage("com.yoyuzh..")
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName("jakarta.transaction.Transactional");

        rule.check(classes);
    }

    private static ArchCondition<JavaClass> notDependOnAnotherModulesInternalPackage() {
        return new ArchCondition<>("not depend on another module's internal package") {
            @Override
            public void check(JavaClass sourceClass, ConditionEvents events) {
                String sourceModule = moduleOf(sourceClass.getPackageName());

                for (Dependency dependency : sourceClass.getDirectDependenciesFromSelf()) {
                    JavaClass targetClass = dependency.getTargetClass();
                    String targetPackage = targetClass.getPackageName();
                    if (!targetPackage.startsWith("com.yoyuzh.") || !targetPackage.contains(".internal.")) {
                        continue;
                    }

                    String targetModule = moduleOf(targetPackage.substring("com.yoyuzh.".length()));
                    if (Objects.equals(sourceModule, targetModule)) {
                        continue;
                    }

                    String message = String.format(
                            "%s [%s] depends on %s [%s] via %s",
                            sourceClass.getName(),
                            sourceModule,
                            targetClass.getName(),
                            targetModule,
                            dependency.getDescription()
                    );
                    events.add(SimpleConditionEvent.violated(sourceClass, message));
                }
            }
        };
    }

    private static String moduleOf(String packageName) {
        String normalizedPackage = packageName.startsWith("com.yoyuzh.")
                ? packageName.substring("com.yoyuzh.".length())
                : packageName;
        for (String module : MODULES) {
            if (normalizedPackage.equals(module) || normalizedPackage.startsWith(module + ".")) {
                return module;
            }
        }
        int delimiter = normalizedPackage.indexOf('.');
        return delimiter == -1 ? normalizedPackage : normalizedPackage.substring(0, delimiter);
    }
}
