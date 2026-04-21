package com.yoyuzh.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

class Task2IdentityAccessArchitectureTest {

    private final JavaClasses classes = new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS).importPackages("com.yoyuzh");

    @Test
    void identityAccessWebMustOwnMovedAuthWebAdapters() {
        assertThat(classes.get("com.yoyuzh.identity.access.internal.web.AuthController")).isNotNull();
        assertThat(classes.get("com.yoyuzh.identity.access.internal.web.DevAuthController")).isNotNull();
        assertThat(classes.get("com.yoyuzh.identity.access.internal.web.UserController")).isNotNull();

        assertThat(classes.stream().map(JavaClass::getFullName))
                .doesNotContain(
                        "com.yoyuzh.auth.AuthController",
                        "com.yoyuzh.auth.DevAuthController",
                        "com.yoyuzh.identity.access.internal.domain.UserController"
                );
    }

    @Test
    void identityAccessApiMustOwnMovedAuthDtos() {
        assertThat(classes.get("com.yoyuzh.identity.access.api.AuthResponse")).isNotNull();
        assertThat(classes.get("com.yoyuzh.identity.access.api.LoginRequest")).isNotNull();
        assertThat(classes.get("com.yoyuzh.identity.access.api.RefreshTokenRequest")).isNotNull();
        assertThat(classes.get("com.yoyuzh.identity.access.api.RegisterRequest")).isNotNull();
        assertThat(classes.get("com.yoyuzh.identity.access.api.UpdateUserAvatarRequest")).isNotNull();
        assertThat(classes.get("com.yoyuzh.identity.access.api.UpdateUserPasswordRequest")).isNotNull();
        assertThat(classes.get("com.yoyuzh.identity.access.api.UpdateUserProfileRequest")).isNotNull();
        assertThat(classes.get("com.yoyuzh.identity.access.api.UserProfileResponse")).isNotNull();

        assertThat(classes.stream().map(JavaClass::getFullName))
                .doesNotContain(
                        "com.yoyuzh.auth.dto.AuthResponse",
                        "com.yoyuzh.auth.dto.LoginRequest",
                        "com.yoyuzh.auth.dto.RefreshTokenRequest",
                        "com.yoyuzh.auth.dto.RegisterRequest",
                        "com.yoyuzh.auth.dto.UpdateUserAvatarRequest",
                        "com.yoyuzh.auth.dto.UpdateUserPasswordRequest",
                        "com.yoyuzh.auth.dto.UpdateUserProfileRequest",
                        "com.yoyuzh.auth.dto.UserProfileResponse"
                );
    }

    @Test
    void identityAccessDomainAndInfraMustOwnMovedAuthPersistenceTypes() {
        assertThat(classes.get("com.yoyuzh.identity.access.internal.domain.RefreshToken")).isNotNull();
        assertThat(classes.get("com.yoyuzh.identity.access.internal.infra.RefreshTokenRepository")).isNotNull();
        assertThat(classes.get("com.yoyuzh.identity.access.internal.domain.RegistrationInviteState")).isNotNull();
        assertThat(classes.get("com.yoyuzh.identity.access.internal.infra.RegistrationInviteStateRepository")).isNotNull();

        assertThat(classes.stream().map(JavaClass::getFullName))
                .doesNotContain(
                        "com.yoyuzh.auth.RefreshToken",
                        "com.yoyuzh.auth.RefreshTokenRepository",
                        "com.yoyuzh.auth.RegistrationInviteState",
                        "com.yoyuzh.auth.RegistrationInviteStateRepository"
                );
    }

    @Test
    void legacyAuthDtoRootMustNotExist() {
        ArchRule rule = noClasses()
                .should()
                .resideInAnyPackage("com.yoyuzh.auth.dto..");

        rule.check(classes);
    }

    @Test
    void identityAccessWebMustNotDependOnLegacyAuthDtoRoot() {
        ArchRule rule = noClasses()
                .that()
                .resideInAnyPackage("com.yoyuzh.identity.access.internal.web..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("com.yoyuzh.auth.dto..");

        rule.check(classes);
    }

    @Test
    void identityAccessInternalMustNotDependOnLegacyAuthRefreshAndInvitePersistenceRoots() {
        ArchRule rule = noClasses()
                .that()
                .resideInAnyPackage("com.yoyuzh.identity.access.internal..")
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName("com.yoyuzh.auth.RefreshToken");

        ArchRule inviteRule = noClasses()
                .that()
                .resideInAnyPackage("com.yoyuzh.identity.access.internal..")
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName("com.yoyuzh.auth.RegistrationInviteState");

        rule.check(classes);
        inviteRule.check(classes);
    }
}
