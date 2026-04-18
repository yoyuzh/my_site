package com.yoyuzh.admin;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminAccessEvaluatorTest {

    @Mock
    private AdminRuntimeSettingsService adminRuntimeSettingsService;

    private AdminAccessEvaluator adminAccessEvaluator;

    @BeforeEach
    void setUp() {
        adminAccessEvaluator = new AdminAccessEvaluator(adminRuntimeSettingsService);
    }

    @Test
    void shouldReturnFalseWhenAuthenticationIsMissing() {
        assertThat(adminAccessEvaluator.isAdmin(null)).isFalse();
    }

    @Test
    void shouldReturnFalseWhenAuthenticationIsNotAuthenticated() {
        Authentication authentication = new TestingAuthenticationToken("alice", "password");
        authentication.setAuthenticated(false);

        assertThat(adminAccessEvaluator.isAdmin(authentication)).isFalse();
    }

    @Test
    void shouldAllowRoleThatIsConfiguredAsManagementRole() {
        when(adminRuntimeSettingsService.snapshot()).thenReturn(runtimeStateWithRoles(List.of("ADMIN")));
        Authentication authentication = new TestingAuthenticationToken(
                "alice",
                "password",
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
        );

        assertThat(adminAccessEvaluator.isAdmin(authentication)).isTrue();
    }

    @Test
    void shouldRejectRoleThatIsNotConfiguredAsManagementRole() {
        when(adminRuntimeSettingsService.snapshot()).thenReturn(runtimeStateWithRoles(List.of("ADMIN")));
        Authentication authentication = new TestingAuthenticationToken(
                "alice",
                "password",
                List.of(new SimpleGrantedAuthority("ROLE_MODERATOR"))
        );

        assertThat(adminAccessEvaluator.isAdmin(authentication)).isFalse();
    }

    @Test
    void shouldMatchConfiguredRoleCaseInsensitively() {
        when(adminRuntimeSettingsService.snapshot()).thenReturn(runtimeStateWithRoles(List.of("admin")));
        Authentication authentication = new TestingAuthenticationToken(
                "alice",
                "password",
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
        );

        assertThat(adminAccessEvaluator.isAdmin(authentication)).isTrue();
    }

    @Test
    void shouldAllowRolePrefixedConfiguredManagementRole() {
        when(adminRuntimeSettingsService.snapshot()).thenReturn(runtimeStateWithRoles(List.of("ROLE_ADMIN")));
        Authentication authentication = new TestingAuthenticationToken(
                "alice",
                "password",
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
        );

        assertThat(adminAccessEvaluator.isAdmin(authentication)).isTrue();
    }

    private static AdminRuntimeSettingsService.State runtimeStateWithRoles(List<String> roles) {
        return new AdminRuntimeSettingsService.State(
                false,
                true,
                roles,
                900L,
                1209600L,
                false,
                60L,
                true,
                false,
                false,
                "in-memory",
                3000L,
                15000L,
                false,
                "local",
                false
        );
    }
}
