package com.yoyuzh.ops.admin.internal.application;

import com.yoyuzh.identity.access.api.AdminAccessPolicy;
import com.yoyuzh.ops.admin.api.AdminPermissionCode;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RuntimeAdminPermissionQueryApiTest {

    @Mock
    private AdminAccessPolicy adminAccessPolicy;

    private RuntimeAdminPermissionQueryApi runtimeAdminPermissionQueryApi;

    @BeforeEach
    void setUp() {
        runtimeAdminPermissionQueryApi = new RuntimeAdminPermissionQueryApi(adminAccessPolicy);
    }

    @Test
    void shouldReturnEmptyPermissionsWhenAuthenticationIsMissing() {
        assertThat(runtimeAdminPermissionQueryApi.currentPermissions(null).permissions()).isEmpty();
        verify(adminAccessPolicy, never()).hasAdminAccess(null);
    }

    @Test
    void shouldReturnEmptyPermissionsWhenUserIsNotAdmin() {
        Authentication authentication = new TestingAuthenticationToken(
                "alice",
                "password",
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
        when(adminAccessPolicy.hasAdminAccess(authentication)).thenReturn(false);

        assertThat(runtimeAdminPermissionQueryApi.currentPermissions(authentication).permissions()).isEmpty();
    }

    @Test
    void shouldReturnAllDefinedPermissionsWhenUserHasAdminAccess() {
        Authentication authentication = new TestingAuthenticationToken(
                "alice",
                "password",
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
        );
        when(adminAccessPolicy.hasAdminAccess(authentication)).thenReturn(true);

        assertThat(runtimeAdminPermissionQueryApi.currentPermissions(authentication).permissions())
                .containsExactly(Arrays.stream(AdminPermissionCode.values())
                        .map(AdminPermissionCode::code)
                        .toArray(String[]::new));
    }
}
