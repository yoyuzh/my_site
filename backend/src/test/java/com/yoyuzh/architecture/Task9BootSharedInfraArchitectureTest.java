package com.yoyuzh.architecture;

import static org.assertj.core.api.Assertions.assertThat;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClass;
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
                .haveFullyQualifiedName("com.yoyuzh.app.android.internal.web.AndroidReleaseController")
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName("com.yoyuzh.app.android.api.AndroidReleaseQueryApi");

        ArchRule noLegacyAndroidServiceRule = noClasses()
                .that()
                .haveFullyQualifiedName("com.yoyuzh.app.android.internal.web.AndroidReleaseController")
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName("com.yoyuzh.config.AndroidReleaseService");

        ArchRule runtimeApiShouldNotDependOnLegacyService = noClasses()
                .that()
                .haveFullyQualifiedName("com.yoyuzh.app.android.internal.application.RuntimeAndroidReleaseQueryApi")
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName("com.yoyuzh.config.AndroidReleaseService");

        androidControllerRule.check(classes);
        noLegacyAndroidServiceRule.check(classes);
        runtimeApiShouldNotDependOnLegacyService.check(classes);
    }

    @Test
    void appAndroidApiMustOwnAndroidReleaseContracts() {
        assertThat(classes.get("com.yoyuzh.app.android.api.AndroidReleaseResponse")).isNotNull();
        assertThat(classes.get("com.yoyuzh.app.android.api.AndroidReleaseDownload")).isNotNull();
    }

    @Test
    void sharedKernelAndBootWebMustOwnGlobalContracts() {
        assertThat(classes.containPackage("com.yoyuzh.shared.kernel")).isTrue();
        assertThat(classes.containPackage("com.yoyuzh.boot.web")).isTrue();

        assertThat(classes.get("com.yoyuzh.shared.kernel.ApiResponse")).isNotNull();
        assertThat(classes.get("com.yoyuzh.shared.kernel.PageResponse")).isNotNull();
        assertThat(classes.get("com.yoyuzh.shared.kernel.BusinessException")).isNotNull();
        assertThat(classes.get("com.yoyuzh.shared.kernel.ErrorCode")).isNotNull();
        assertThat(classes.get("com.yoyuzh.boot.web.GlobalExceptionHandler")).isNotNull();

        assertThat(classes.stream().map(JavaClass::getPackageName))
                .doesNotContain("com.yoyuzh.common");
    }

    @Test
    void bootSecurityMustOwnSecurityWiring() {
        assertThat(classes.containPackage("com.yoyuzh.boot.security")).isTrue();
        assertThat(classes.get("com.yoyuzh.boot.security.SecurityConfig")).isNotNull();
        assertThat(classes.get("com.yoyuzh.boot.security.JwtAuthenticationFilter")).isNotNull();
        assertThat(classes.get("com.yoyuzh.boot.security.JwtProperties")).isNotNull();
        assertThat(classes.get("com.yoyuzh.boot.security.CorsProperties")).isNotNull();
    }

    @Test
    void bootMustOwnRemainingGlobalWiring() {
        assertThat(classes.get("com.yoyuzh.boot.web.ApiRootController")).isNotNull();
        assertThat(classes.get("com.yoyuzh.boot.web.OpenApiConfig")).isNotNull();
        assertThat(classes.get("com.yoyuzh.boot.RestClientConfig")).isNotNull();
        assertThat(classes.get("com.yoyuzh.boot.SchedulingConfiguration")).isNotNull();
        assertThat(classes.get("com.yoyuzh.boot.FileStorageConfiguration")).isNotNull();

        assertThat(classes.stream().map(JavaClass::getFullName))
                .doesNotContain("com.yoyuzh.config.FileStorageConfiguration");
    }

    @Test
    void identityAndAdminModulesMustOwnLegacyRegistrationAndAdminProperties() {
        assertThat(classes.get("com.yoyuzh.identity.access.internal.infra.RegistrationProperties")).isNotNull();
        assertThat(classes.get("com.yoyuzh.identity.access.internal.infra.AdminProperties")).isNotNull();

        assertThat(classes.stream().map(JavaClass::getFullName))
                .doesNotContain(
                        "com.yoyuzh.config.RegistrationProperties",
                        "com.yoyuzh.config.AdminProperties"
                );
    }

    @Test
    void platformStorageMustOwnFileStorageProperties() {
        assertThat(classes.get("com.yoyuzh.platform.storage.internal.infra.FileStorageProperties")).isNotNull();

        assertThat(classes.stream().map(JavaClass::getFullName))
                .doesNotContain("com.yoyuzh.config.FileStorageProperties");
    }
}
