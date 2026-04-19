package com.yoyuzh.ops.admin.internal.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yoyuzh.identity.access.api.IdentityUserDirectoryApi;
import com.yoyuzh.ops.admin.internal.infra.AdminAuditLogEntity;
import com.yoyuzh.ops.admin.internal.infra.AdminAuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminAuditService {

    private final AdminAuditLogRepository adminAuditLogRepository;
    private final IdentityUserDirectoryApi identityUserDirectoryApi;
    private final ObjectMapper objectMapper;

    public void record(AdminAuditAction action,
                       String targetType,
                       Long targetId,
                       String summary,
                       Map<String, Object> details) {
        ActorSnapshot actor = resolveActorSnapshot();
        AdminAuditLogEntity entity = new AdminAuditLogEntity();
        entity.setActorUserId(actor.userId());
        entity.setActorUsername(actor.username());
        entity.setActorAuthorities(actor.authorities());
        entity.setActionType(action.name());
        entity.setTargetType(targetType);
        entity.setTargetId(targetId);
        entity.setSummary(summary);
        entity.setDetailsJson(serializeDetails(details));
        adminAuditLogRepository.save(entity);
    }

    private ActorSnapshot resolveActorSnapshot() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return new ActorSnapshot(null, "system", "");
        }
        String username = authentication.getName();
        Long userId = StringUtils.hasText(username)
                ? identityUserDirectoryApi.findProfileByUsername(username).map(profile -> profile.id()).orElse(null)
                : null;
        String authorities = authentication.getAuthorities() == null
                ? ""
                : authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .sorted()
                .collect(Collectors.joining(","));
        if (!StringUtils.hasText(username)) {
            return new ActorSnapshot(userId, "system", authorities);
        }
        return new ActorSnapshot(userId, username, authorities);
    }

    private String serializeDetails(Map<String, Object> details) {
        try {
            return objectMapper.writeValueAsString(details == null ? Map.of() : details);
        } catch (JsonProcessingException ex) {
            return "{}";
        }
    }

    private record ActorSnapshot(Long userId, String username, String authorities) {
    }
}
