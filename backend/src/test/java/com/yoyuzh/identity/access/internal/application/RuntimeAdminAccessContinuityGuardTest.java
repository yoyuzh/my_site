package com.yoyuzh.identity.access.internal.application;

import com.yoyuzh.admin.AdminRuntimeSettingsService;
import com.yoyuzh.auth.UserRole;
import com.yoyuzh.auth.UserRepository;
import com.yoyuzh.common.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RuntimeAdminAccessContinuityGuardTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private AdminRuntimeSettingsService adminRuntimeSettingsService;

    @Test
    void shouldRejectDemotingLastAdminCapableUser() {
        RuntimeAdminAccessContinuityGuard guard =
                new RuntimeAdminAccessContinuityGuard(userRepository, adminRuntimeSettingsService);
        when(adminRuntimeSettingsService.snapshot()).thenReturn(runtimeState("ADMIN"));
        when(userRepository.countByBannedFalseAndRoleIn(anyCollection())).thenReturn(1L);

        assertThatThrownBy(() -> guard.ensureAdminAccessRemainsAvailable(
                        UserRole.ADMIN.name(),
                        false,
                        UserRole.USER.name(),
                        false))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("at least one unbanned admin-capable user must remain");
    }

    @Test
    void shouldAllowUpdateWhenAnotherAdminCapableUserRemains() {
        RuntimeAdminAccessContinuityGuard guard =
                new RuntimeAdminAccessContinuityGuard(userRepository, adminRuntimeSettingsService);
        when(adminRuntimeSettingsService.snapshot()).thenReturn(runtimeState("ADMIN"));
        when(userRepository.countByBannedFalseAndRoleIn(anyCollection())).thenReturn(2L);

        assertThatCode(() -> guard.ensureAdminAccessRemainsAvailable(
                        UserRole.ADMIN.name(),
                        false,
                        UserRole.USER.name(),
                        false))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldSkipCountWhenCurrentUserIsNotAdminCapable() {
        RuntimeAdminAccessContinuityGuard guard =
                new RuntimeAdminAccessContinuityGuard(userRepository, adminRuntimeSettingsService);
        when(adminRuntimeSettingsService.snapshot()).thenReturn(runtimeState("ADMIN"));

        assertThatCode(() -> guard.ensureAdminAccessRemainsAvailable(
                        UserRole.USER.name(),
                        false,
                        UserRole.USER.name(),
                        false))
                .doesNotThrowAnyException();

        verifyNoInteractions(userRepository);
    }

    private static AdminRuntimeSettingsService.State runtimeState(String... managementRoles) {
        return new AdminRuntimeSettingsService.State(
                false,
                true,
                List.of(managementRoles),
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
