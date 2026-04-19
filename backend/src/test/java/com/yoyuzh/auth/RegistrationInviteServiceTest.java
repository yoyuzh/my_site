package com.yoyuzh.auth;

import com.yoyuzh.shared.kernel.BusinessException;
import com.yoyuzh.identity.access.internal.infra.RegistrationProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegistrationInviteServiceTest {

    @Mock
    private RegistrationInviteStateRepository registrationInviteStateRepository;

    private RegistrationInviteService registrationInviteService;

    @BeforeEach
    void setUp() {
        registrationInviteService = new RegistrationInviteService(
                registrationInviteStateRepository,
                new RegistrationProperties()
        );
    }

    @Test
    void shouldRejectBlankInviteCodeUpdate() {
        RegistrationInviteState state = existingState("INITIAL-CODE");
        when(registrationInviteStateRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(state));

        assertThatThrownBy(() -> registrationInviteService.updateCurrentInviteCode("   "))
                .isInstanceOf(BusinessException.class);
        verify(registrationInviteStateRepository, never()).save(any(RegistrationInviteState.class));
    }

    @Test
    void shouldRejectInviteCodeLongerThan64Characters() {
        RegistrationInviteState state = existingState("INITIAL-CODE");
        when(registrationInviteStateRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(state));

        assertThatThrownBy(() -> registrationInviteService.updateCurrentInviteCode("A".repeat(65)))
                .isInstanceOf(BusinessException.class);
        verify(registrationInviteStateRepository, never()).save(any(RegistrationInviteState.class));
    }

    @Test
    void shouldReturnExistingInviteCodeWhenConcurrentInitializationAlreadyInsertedState() {
        RegistrationInviteState existing = existingState("CONCURRENT-CODE");
        when(registrationInviteStateRepository.findById(1L))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(existing));
        when(registrationInviteStateRepository.saveAndFlush(any(RegistrationInviteState.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        String inviteCode = registrationInviteService.getCurrentInviteCode();

        assertThat(inviteCode).isEqualTo("CONCURRENT-CODE");
    }

    private RegistrationInviteState existingState(String inviteCode) {
        RegistrationInviteState state = new RegistrationInviteState();
        state.setId(1L);
        state.setInviteCode(inviteCode);
        return state;
    }
}
