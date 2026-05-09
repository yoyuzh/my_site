package com.yoyuzh.ops.admin.internal.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yoyuzh.boot.security.AuthenticatedUserPrincipal;
import com.yoyuzh.identity.access.api.IdentityUserDirectoryApi;
import com.yoyuzh.identity.access.api.IdentityUserProfileSummary;
import com.yoyuzh.ops.admin.internal.infra.AdminAuditLogEntity;
import com.yoyuzh.ops.admin.internal.infra.AdminAuditLogRepository;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminAuditServiceTest {

    @Mock
    private AdminAuditLogRepository adminAuditLogRepository;
    @Mock
    private IdentityUserDirectoryApi identityUserDirectoryApi;

    private AdminAuditService adminAuditService;

    @BeforeEach
    void setUp() {
        adminAuditService = new AdminAuditService(adminAuditLogRepository, identityUserDirectoryApi, new ObjectMapper());
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldRecordAuditLogWithAuthenticatedActorSnapshot() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new AuthenticatedUserPrincipal(
                        99L,
                        "service-admin",
                        "N/A",
                        0L,
                        0L,
                        List.of(new SimpleGrantedAuthority("ROLE_ADMIN"), new SimpleGrantedAuthority("ROLE_MODERATOR")),
                        true
                ),
                "N/A",
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"), new SimpleGrantedAuthority("ROLE_MODERATOR"))
        ));

        adminAuditService.record(
                AdminAuditAction.USER_ROLE_UPDATED,
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
        assertThat(entity.getActionType()).isEqualTo("USER_ROLE_UPDATED");
        assertThat(entity.getTargetType()).isEqualTo("USER");
        assertThat(entity.getTargetId()).isEqualTo(42L);
        assertThat(entity.getSummary()).isEqualTo("Updated user role");
        assertThat(entity.getDetailsJson()).isEqualTo("{\"role\":\"ADMIN\"}");
        verify(identityUserDirectoryApi, never()).findProfileByUsername("service-admin");
    }

    @Test
    void shouldRecordAuditLogWithSystemActorWhenAuthenticationMissing() {
        adminAuditService.record(
                AdminAuditAction.FILE_DELETED,
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
        assertThat(entity.getActionType()).isEqualTo("FILE_DELETED");
    }

    @Test
    void shouldFallbackToDirectoryLookupWhenPrincipalDoesNotExposeUserId() {
        IdentityUserProfileSummary adminUser = new IdentityUserProfileSummary(99L, "service-admin", "admin@example.com");
        when(identityUserDirectoryApi.findProfileByUsername("service-admin")).thenReturn(Optional.of(adminUser));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "service-admin",
                "N/A",
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
        ));

        adminAuditService.record(
                AdminAuditAction.USER_ROLE_UPDATED,
                "USER",
                42L,
                "Updated user role",
                Map.of("role", "ADMIN")
        );

        verify(identityUserDirectoryApi).findProfileByUsername("service-admin");
    }

    @Test
    void shouldFailWhenAuditDetailsCannotBeSerialized() throws Exception {
        ObjectMapper failingMapper = org.mockito.Mockito.mock(ObjectMapper.class);
        when(failingMapper.writeValueAsString(Map.of("role", "ADMIN")))
                .thenThrow(new JsonProcessingException("boom") {
                });
        AdminAuditService service = new AdminAuditService(adminAuditLogRepository, identityUserDirectoryApi, failingMapper);

        assertThatThrownBy(() -> service.record(
                AdminAuditAction.USER_ROLE_UPDATED,
                "USER",
                42L,
                "Updated user role",
                Map.of("role", "ADMIN")
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("serialize");
    }
}
