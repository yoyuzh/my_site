package com.yoyuzh.ops.admin.internal.application;

import com.yoyuzh.ops.admin.internal.infra.AdminAuditLogEntity;
import com.yoyuzh.ops.admin.internal.infra.AdminAuditLogRepository;
import com.yoyuzh.shared.kernel.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AdminAuditQueryService {

    private final AdminAuditLogRepository adminAuditLogRepository;

    public PageResponse<AdminAuditLogResponse> listAuditLogs(int page,
                                                             int size,
                                                             String actorQuery,
                                                             String actionType,
                                                             String targetType,
                                                             Long targetId) {
        Page<AdminAuditLogEntity> result = adminAuditLogRepository.search(
                normalizeQuery(actorQuery),
                normalizeQuery(actionType),
                normalizeQuery(targetType),
                targetId,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")
                        .and(Sort.by(Sort.Direction.DESC, "id")))
        );
        return new PageResponse<>(
                result.getContent().stream().map(this::toResponse).toList(),
                result.getTotalElements(),
                page,
                size
        );
    }

    private AdminAuditLogResponse toResponse(AdminAuditLogEntity entity) {
        return new AdminAuditLogResponse(
                entity.getId(),
                entity.getActorUserId(),
                entity.getActorUsername(),
                entity.getActorAuthorities(),
                entity.getActionType(),
                entity.getTargetType(),
                entity.getTargetId(),
                entity.getSummary(),
                entity.getDetailsJson(),
                entity.getCreatedAt()
        );
    }

    private String normalizeQuery(String query) {
        if (query == null) {
            return "";
        }
        return query.trim();
    }
}
