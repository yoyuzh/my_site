package com.yoyuzh.architecture;

import static org.assertj.core.api.Assertions.assertThat;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RestController;

class Task9BootSharedInfraArchitectureTest {

    private final JavaClasses classes = new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS).importPackages("com.yoyuzh");

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
        assertThat(classes.get("com.yoyuzh.boot.security.JwtTokenProvider")).isNotNull();
        assertThat(classes.get("com.yoyuzh.boot.security.AuthTokenInvalidationService")).isNotNull();
        assertThat(classes.get("com.yoyuzh.boot.security.NoOpAuthTokenInvalidationService")).isNotNull();
        assertThat(classes.get("com.yoyuzh.boot.security.CustomUserDetailsService")).isNotNull();

        assertThat(classes.stream().map(JavaClass::getFullName))
                .doesNotContain(
                        "com.yoyuzh.auth.JwtTokenProvider",
                        "com.yoyuzh.auth.AuthTokenInvalidationService",
                        "com.yoyuzh.auth.NoOpAuthTokenInvalidationService",
                        "com.yoyuzh.auth.CustomUserDetailsService"
                );
    }

    @Test
    void bootMustOwnRemainingGlobalWiring() {
        assertThat(classes.get("com.yoyuzh.boot.web.ApiRootController")).isNotNull();
        assertThat(classes.get("com.yoyuzh.boot.web.SiteV2Controller")).isNotNull();
        assertThat(classes.get("com.yoyuzh.boot.web.OpenApiConfig")).isNotNull();
        assertThat(classes.get("com.yoyuzh.boot.RestClientConfig")).isNotNull();
        assertThat(classes.get("com.yoyuzh.boot.SchedulingConfiguration")).isNotNull();
        assertThat(classes.get("com.yoyuzh.boot.FileStorageConfiguration")).isNotNull();

        assertThat(classes.stream().map(JavaClass::getFullName))
                .doesNotContain("com.yoyuzh.config.FileStorageConfiguration");
    }

    @Test
    void bootWebV2MustOwnProtocolEnvelopeWithoutRecreatingTopLevelApiRoot() {
        ArchRule noV2ControllersInTopLevelApiRule = noClasses()
                .that()
                .resideInAnyPackage("com.yoyuzh.boot.web.v2..")
                .should()
                .beAnnotatedWith(RestController.class);

        noV2ControllersInTopLevelApiRule.check(classes);
        assertThat(classes.stream().map(JavaClass::getPackageName))
                .noneMatch(packageName -> packageName.equals("com.yoyuzh.api") || packageName.startsWith("com.yoyuzh.api."))
                .doesNotContain(
                        "com.yoyuzh.boot.web.v2.files",
                        "com.yoyuzh.boot.web.v2.shares",
                        "com.yoyuzh.boot.web.v2.tasks",
                        "com.yoyuzh.boot.web.v2.site"
                );
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
