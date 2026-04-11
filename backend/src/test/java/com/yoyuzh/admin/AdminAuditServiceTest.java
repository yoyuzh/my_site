package com.yoyuzh.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yoyuzh.auth.User;
import com.yoyuzh.auth.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminAuditServiceTest {

    @Mock
    private AdminAuditLogRepository adminAuditLogRepository;
    @Mock
    private UserRepository userRepository;

    private AdminAuditService adminAuditService;

    @BeforeEach
    void setUp() {
        adminAuditService = new AdminAuditService(adminAuditLogRepository, userRepository, new ObjectMapper());
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldRecordAuditLogWithAuthenticatedActorSnapshot() {
        User adminUser = new User();
        adminUser.setId(99L);
        adminUser.setUsername("service-admin");
        when(userRepository.findByUsername("service-admin")).thenReturn(Optional.of(adminUser));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "service-admin",
                "N/A",
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"), new SimpleGrantedAuthority("ROLE_MODERATOR"))
        ));

        adminAuditService.record(
                AdminAuditAction.UPDATE_USER_ROLE,
                "USER",
                42L,
                "Updated user role",
                Map.of("role", "ADMIN")
        );

        ArgumentCaptor<AdminAuditLogEntity> captor = ArgumentCaptor.forClass(AdminAuditLogEntity.class);
        verify(adminAuditLogRepository).save(captor.capture());
        AdminAuditLogEntity entity = captor.getValue();
        assertThat(entity.getActorUserId()).isEqualTo(99L);
        assertThat(entity.getActorUsername()).isEqualTo("service-admin");
        assertThat(entity.getActorAuthorities()).isEqualTo("ROLE_ADMIN,ROLE_MODERATOR");
        assertThat(entity.getActionType()).isEqualTo("UPDATE_USER_ROLE");
        assertThat(entity.getTargetType()).isEqualTo("USER");
        assertThat(entity.getTargetId()).isEqualTo(42L);
        assertThat(entity.getSummary()).isEqualTo("Updated user role");
        assertThat(entity.getDetailsJson()).contains("\"role\":\"ADMIN\"");
    }

    @Test
    void shouldRecordAuditLogWithSystemActorWhenAuthenticationMissing() {
        adminAuditService.record(
                AdminAuditAction.DELETE_FILE,
                "FILE",
                7L,
                "Deleted file",
                Map.of("filename", "report.pdf")
        );

        ArgumentCaptor<AdminAuditLogEntity> captor = ArgumentCaptor.forClass(AdminAuditLogEntity.class);
        verify(adminAuditLogRepository).save(captor.capture());
        AdminAuditLogEntity entity = captor.getValue();
        assertThat(entity.getActorUserId()).isNull();
        assertThat(entity.getActorUsername()).isEqualTo("system");
        assertThat(entity.getActorAuthorities()).isEmpty();
        assertThat(entity.getActionType()).isEqualTo("DELETE_FILE");
    }
}
