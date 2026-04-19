package com.yoyuzh.ops.admin.internal.application;

import com.yoyuzh.ops.admin.internal.infra.AdminAuditLogEntity;
import com.yoyuzh.ops.admin.internal.infra.AdminAuditLogRepository;
import com.yoyuzh.shared.kernel.PageResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminAuditQueryServiceTest {

    @Mock
    private AdminAuditLogRepository adminAuditLogRepository;

    private AdminAuditQueryService adminAuditQueryService;

    @BeforeEach
    void setUp() {
        adminAuditQueryService = new AdminAuditQueryService(adminAuditLogRepository);
    }

    @Test
    void shouldListAuditLogsWithPagination() {
        AdminAuditLogEntity entity = new AdminAuditLogEntity();
        entity.setActorUserId(1L);
        entity.setActorUsername("service-admin");
        entity.setActorAuthorities("ROLE_ADMIN");
        entity.setActionType("UPDATE_USER_ROLE");
        entity.setTargetType("USER");
        entity.setTargetId(2L);
        entity.setSummary("Updated user role");
        entity.setDetailsJson("{\"role\":\"ADMIN\"}");
        when(adminAuditLogRepository.search(eq("service-admin"), eq("UPDATE_USER_ROLE"), eq("USER"), eq(2L), any()))
                .thenReturn(new PageImpl<>(List.of(entity)));

        PageResponse<AdminAuditLogResponse> response = adminAuditQueryService.listAuditLogs(
                0,
                10,
                "service-admin",
                "UPDATE_USER_ROLE",
                "USER",
                2L
        );

        assertThat(response.total()).isEqualTo(1);
        assertThat(response.items()).hasSize(1);
        AdminAuditLogResponse item = response.items().get(0);
        assertThat(item.actorUsername()).isEqualTo("service-admin");
        assertThat(item.actionType()).isEqualTo("UPDATE_USER_ROLE");
        assertThat(item.targetType()).isEqualTo("USER");
        assertThat(item.targetId()).isEqualTo(2L);
        assertThat(item.summary()).isEqualTo("Updated user role");
        assertThat(item.detailsJson()).contains("\"role\":\"ADMIN\"");
        verify(adminAuditLogRepository).search(eq("service-admin"), eq("UPDATE_USER_ROLE"), eq("USER"), eq(2L), any());
    }

    @Test
    void shouldNormalizeNullFiltersToEmptyStrings() {
        when(adminAuditLogRepository.search(eq(""), eq(""), eq(""), eq(null), any()))
                .thenReturn(new PageImpl<>(List.of()));

        PageResponse<AdminAuditLogResponse> response = adminAuditQueryService.listAuditLogs(
                0,
                10,
                null,
                null,
                null,
                null
        );

        assertThat(response.total()).isZero();
        assertThat(response.items()).isEmpty();
        verify(adminAuditLogRepository).search(eq(""), eq(""), eq(""), eq(null), any());
    }
}
