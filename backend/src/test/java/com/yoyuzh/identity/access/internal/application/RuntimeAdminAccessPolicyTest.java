package com.yoyuzh.identity.access.internal.application;

import com.yoyuzh.ops.admin.api.AdminRuntimeSettingsApi;
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
class RuntimeAdminAccessPolicyTest {

    @Mock
    private AdminRuntimeSettingsApi adminRuntimeSettingsApi;

    @Test
    void shouldAllowConfiguredManagementRole() {
        RuntimeAdminAccessPolicy policy = new RuntimeAdminAccessPolicy(adminRuntimeSettingsApi);
        when(adminRuntimeSettingsApi.registrationManagementRoles()).thenReturn(List.of("ADMIN"));
        Authentication authentication = new TestingAuthenticationToken(
                "alice",
                "password",
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
        );

        assertThat(policy.hasAdminAccess(authentication)).isTrue();
    }

    @Test
    void shouldRejectRoleOutsideConfiguredManagementRoles() {
        RuntimeAdminAccessPolicy policy = new RuntimeAdminAccessPolicy(adminRuntimeSettingsApi);
        when(adminRuntimeSettingsApi.registrationManagementRoles()).thenReturn(List.of("ADMIN"));
        Authentication authentication = new TestingAuthenticationToken(
                "alice",
                "password",
                List.of(new SimpleGrantedAuthority("ROLE_MODERATOR"))
        );

        assertThat(policy.hasAdminAccess(authentication)).isFalse();
    }

    @Test
    void shouldNormalizeConfiguredRoleNames() {
        RuntimeAdminAccessPolicy policy = new RuntimeAdminAccessPolicy(adminRuntimeSettingsApi);
        when(adminRuntimeSettingsApi.registrationManagementRoles()).thenReturn(List.of("role_admin"));
        Authentication authentication = new TestingAuthenticationToken(
                "alice",
                "password",
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
        );

        assertThat(policy.hasAdminAccess(authentication)).isTrue();
    }

}
