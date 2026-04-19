package com.yoyuzh.identity.access.internal.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.yoyuzh.auth.RegistrationInviteService;
import com.yoyuzh.auth.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RuntimeIdentityAdminSummaryApiTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private RegistrationInviteService registrationInviteService;

    private RuntimeIdentityAdminSummaryApi runtimeIdentityAdminSummaryApi;

    @BeforeEach
    void setUp() {
        runtimeIdentityAdminSummaryApi = new RuntimeIdentityAdminSummaryApi(userRepository, registrationInviteService);
    }

    @Test
    void shouldCountUsersAsAdmin() {
        when(userRepository.count()).thenReturn(9L);

        long totalUsers = runtimeIdentityAdminSummaryApi.countUsersAsAdmin();

        assertThat(totalUsers).isEqualTo(9L);
    }

    @Test
    void shouldReadCurrentInviteCode() {
        when(registrationInviteService.getCurrentInviteCode()).thenReturn("INV-001");

        String inviteCode = runtimeIdentityAdminSummaryApi.currentInviteCode();

        assertThat(inviteCode).isEqualTo("INV-001");
    }

    @Test
    void shouldUpdateInviteCode() {
        when(registrationInviteService.updateCurrentInviteCode("INV-002")).thenReturn("INV-002");

        String inviteCode = runtimeIdentityAdminSummaryApi.updateInviteCode("INV-002");

        assertThat(inviteCode).isEqualTo("INV-002");
    }

    @Test
    void shouldRotateInviteCode() {
        when(registrationInviteService.rotateCurrentInviteCode()).thenReturn("INV-ROTATED");

        String inviteCode = runtimeIdentityAdminSummaryApi.rotateInviteCode();

        assertThat(inviteCode).isEqualTo("INV-ROTATED");
    }
}
