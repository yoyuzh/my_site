package com.yoyuzh.ops.admin.internal.application.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yoyuzh.boot.security.AuthenticatedUserPrincipal;
import com.yoyuzh.ops.admin.api.AdminConfigDefinitionResponse;
import com.yoyuzh.ops.admin.api.AdminConfigHistoryResponse;
import com.yoyuzh.ops.admin.api.AdminConfigUpdateRequest;
import com.yoyuzh.ops.admin.api.AdminSettingsResponse;
import com.yoyuzh.ops.admin.api.AdminSettingsUpdateRequest;
import com.yoyuzh.ops.admin.internal.application.AdminAuditAction;
import com.yoyuzh.ops.admin.internal.application.AdminAuditService;
import com.yoyuzh.ops.admin.internal.application.AdminConfigSnapshotService;
import com.yoyuzh.ops.admin.internal.application.AdminMutableSettingsService;
import com.yoyuzh.ops.admin.internal.application.AdminRuntimeSettingsDefaults;
import com.yoyuzh.ops.admin.internal.application.AdminRuntimeSettingsService;
import com.yoyuzh.ops.admin.internal.infra.config.AdminConfigChangeLogEntity;
import com.yoyuzh.ops.admin.internal.infra.config.AdminConfigChangeLogRepository;
import com.yoyuzh.ops.admin.internal.infra.config.AdminConfigValueEntity;
import com.yoyuzh.ops.admin.internal.infra.config.AdminConfigValueRepository;
import com.yoyuzh.shared.kernel.BusinessException;
import com.yoyuzh.shared.kernel.ErrorCode;
import com.yoyuzh.shared.kernel.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class AdminConfigValueGovernanceService {

    private static final String REGISTRATION_INVITE_REQUIRED_KEY = "registration.inviteCodeRequired";
    private static final String REGISTRATION_MANAGEMENT_ROLES_KEY = "registration.managementRoles";
    private static final String TRANSFER_OFFLINE_LIMIT_KEY = "transfer.offlineTransferStorageLimitBytes";
    private static final String TARGET_TYPE = "ADMIN_CONFIG";

    private final AdminConfigRegistry adminConfigRegistry;
    private final AdminConfigSnapshotService adminConfigSnapshotService;
    private final AdminRuntimeSettingsDefaults adminRuntimeSettingsDefaults;
    private final AdminMutableSettingsService adminMutableSettingsService;
    private final AdminConfigValueRepository adminConfigValueRepository;
    private final AdminConfigChangeLogRepository adminConfigChangeLogRepository;
    private final AdminAuditService adminAuditService;
    private final ObjectMapper objectMapper;

    @Transactional
    public AdminConfigDefinitionResponse updateValue(String key, AdminConfigUpdateRequest request) {
        AdminConfigDefinition definition = writableDefinition(key);
        Object nextValue = normalizeValue(definition, request.value());
        Object beforeValue = currentValue(key);
        if (Objects.equals(beforeValue, nextValue)) {
            return responseFor(definition);
        }
        applyBusinessValue(key, nextValue);
        return persistVersionAndRespond(
                definition,
                beforeValue,
                currentValue(key),
                normalizeReason(request.reason(), "Updated admin config value"),
                AdminAuditAction.CONFIG_VALUE_UPDATED
        );
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminConfigHistoryResponse> history(String key, int page, int size) {
        registeredDefinition(key);
        Page<AdminConfigChangeLogEntity> result =
                adminConfigChangeLogRepository.findByConfigKeyOrderByVersionDesc(key, PageRequest.of(page, size));
        return new PageResponse<>(
                result.getContent().stream().map(this::toHistoryResponse).toList(),
                result.getTotalElements(),
                page,
                size
        );
    }

    @Transactional
    public AdminConfigDefinitionResponse rollback(String key, long version) {
        AdminConfigDefinition definition = writableDefinition(key);
        AdminConfigChangeLogEntity target = adminConfigChangeLogRepository.findFirstByConfigKeyAndVersion(key, version)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_INPUT, "config history version not found"));
        Object beforeValue = currentValue(key);
        Object rollbackValue = deserializeValue(target.getAfterValueJson());
        Object normalizedRollbackValue = normalizeValue(definition, rollbackValue);
        applyBusinessValue(key, normalizedRollbackValue);
        return persistVersionAndRespond(
                definition,
                beforeValue,
                currentValue(key),
                "Rolled back to version " + version,
                AdminAuditAction.CONFIG_VALUE_ROLLED_BACK
        );
    }

    private AdminConfigDefinitionResponse persistVersionAndRespond(AdminConfigDefinition definition,
                                                                   Object beforeValue,
                                                                   Object afterValue,
                                                                   String reason,
                                                                   AdminAuditAction action) {
        String key = definition.key();
        AdminConfigValueEntity currentVersion = adminConfigValueRepository.findByConfigKeyForUpdate(key)
                .orElseGet(() -> {
                    AdminConfigValueEntity entity = new AdminConfigValueEntity();
                    entity.setConfigKey(key);
                    entity.setVersion(0L);
                    return entity;
                });
        long nextVersion = currentVersion.getVersion() + 1L;
        ActorSnapshot actor = resolveActorSnapshot();
        currentVersion.setValueJson(serializeValue(afterValue));
        currentVersion.setVersion(nextVersion);
        currentVersion.setUpdatedByUserId(actor.userId());
        currentVersion.setUpdatedByUsername(actor.username());
        adminConfigValueRepository.save(currentVersion);

        AdminConfigChangeLogEntity changeLog = new AdminConfigChangeLogEntity();
        changeLog.setConfigKey(key);
        changeLog.setBeforeValueJson(serializeValue(beforeValue));
        changeLog.setAfterValueJson(serializeValue(afterValue));
        changeLog.setVersion(nextVersion);
        changeLog.setReason(reason);
        changeLog.setActorUserId(actor.userId());
        changeLog.setActorUsername(actor.username());
        adminConfigChangeLogRepository.save(changeLog);

        adminAuditService.record(
                action,
                TARGET_TYPE,
                currentVersion.getId(),
                action == AdminAuditAction.CONFIG_VALUE_ROLLED_BACK
                        ? "Rolled back admin config value"
                        : "Updated admin config value",
                Map.of(
                        "key", key,
                        "version", nextVersion,
                        "reason", reason
                )
        );

        return responseFor(definition);
    }

    private void applyBusinessValue(String key, Object nextValue) {
        AdminSettingsResponse current = adminConfigSnapshotService.getSettings();
        if (REGISTRATION_INVITE_REQUIRED_KEY.equals(key)) {
            adminMutableSettingsService.updateSettings(new AdminSettingsUpdateRequest(
                    null,
                    new AdminSettingsUpdateRequest.RegistrationSection(
                            (Boolean) nextValue,
                            current.registration().currentInviteCode(),
                            current.registration().managementRoles()
                    ),
                    null,
                    null,
                    null,
                    null,
                    null,
                    null
            ));
            return;
        }
        if (REGISTRATION_MANAGEMENT_ROLES_KEY.equals(key)) {
            adminMutableSettingsService.updateSettings(new AdminSettingsUpdateRequest(
                    null,
                    new AdminSettingsUpdateRequest.RegistrationSection(
                            current.registration().inviteCodeRequired(),
                            current.registration().currentInviteCode(),
                            castStringList(nextValue)
                    ),
                    null,
                    null,
                    null,
                    null,
                    null,
                    null
            ));
            return;
        }
        if (TRANSFER_OFFLINE_LIMIT_KEY.equals(key)) {
            adminMutableSettingsService.updateOfflineTransferStorageLimit(((Number) nextValue).longValue());
            return;
        }
        throw new BusinessException(ErrorCode.INVALID_INPUT, "config key does not support generic writes");
    }

    private Object currentValue(String key) {
        AdminSettingsResponse settings = adminConfigSnapshotService.getSettings();
        return registeredDefinition(key).valueResolver().apply(settings);
    }

    private AdminConfigDefinitionResponse responseFor(AdminConfigDefinition definition) {
        AdminRuntimeSettingsService.State defaults = adminRuntimeSettingsDefaults.create();
        AdminSettingsResponse settings = adminConfigSnapshotService.getSettings();
        return definition.toResponse(defaults, settings);
    }

    private AdminConfigDefinition writableDefinition(String key) {
        AdminConfigDefinition definition = registeredDefinition(key);
        if (!definition.editable()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "config key is read-only");
        }
        if (!"database".equals(definition.source())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "config key is not database-backed");
        }
        if (!supportsGenericWrite(key)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "config key does not support generic writes");
        }
        return definition;
    }

    private AdminConfigDefinition registeredDefinition(String key) {
        return adminConfigRegistry.definitions().stream()
                .filter(definition -> definition.key().equals(key))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_INPUT, "unknown config key"));
    }

    private boolean supportsGenericWrite(String key) {
        return REGISTRATION_INVITE_REQUIRED_KEY.equals(key)
                || REGISTRATION_MANAGEMENT_ROLES_KEY.equals(key)
                || TRANSFER_OFFLINE_LIMIT_KEY.equals(key);
    }

    private Object normalizeValue(AdminConfigDefinition definition, Object value) {
        return switch (definition.type()) {
            case "boolean" -> normalizeBoolean(definition.key(), value);
            case "number" -> normalizeNumber(definition.key(), value, definition.validationRules());
            case "multi_select" -> normalizeMultiSelect(definition, value);
            case "string", "select", "textarea" -> normalizeString(definition, value);
            default -> throw new BusinessException(ErrorCode.INVALID_INPUT, "unsupported config field type");
        };
    }

    private Boolean normalizeBoolean(String key, Object value) {
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        throw new BusinessException(ErrorCode.INVALID_INPUT, key + " must be boolean");
    }

    private Long normalizeNumber(String key, Object value, Map<String, Object> validationRules) {
        long number;
        if (value instanceof Number numberValue) {
            number = numberValue.longValue();
        } else if (value instanceof String stringValue && StringUtils.hasText(stringValue)) {
            try {
                number = Long.parseLong(stringValue.trim());
            } catch (NumberFormatException ex) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, key + " must be numeric");
            }
        } else {
            throw new BusinessException(ErrorCode.INVALID_INPUT, key + " must be numeric");
        }
        Object min = validationRules.get("min");
        if (min instanceof Number minNumber && number < minNumber.longValue()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, key + " is below minimum");
        }
        return number;
    }

    private List<String> normalizeMultiSelect(AdminConfigDefinition definition, Object value) {
        if (!(value instanceof List<?> rawValues)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, definition.key() + " must be an array");
        }
        List<String> allowedValues = definition.options().stream()
                .map(AdminConfigDefinitionResponse.Option::value)
                .toList();
        List<String> values = rawValues.stream()
                .map(item -> item instanceof String stringValue ? stringValue.trim() : "")
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        if (definition.required() && values.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, definition.key() + " cannot be empty");
        }
        if (!allowedValues.isEmpty() && !allowedValues.containsAll(values)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, definition.key() + " contains unsupported value");
        }
        return values;
    }

    private String normalizeString(AdminConfigDefinition definition, Object value) {
        if (!(value instanceof String stringValue)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, definition.key() + " must be a string");
        }
        String normalized = stringValue.trim();
        Object maxLength = definition.validationRules().get("maxLength");
        if (maxLength instanceof Number maxLengthNumber && normalized.length() > maxLengthNumber.intValue()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, definition.key() + " is too long");
        }
        if (definition.required() && !StringUtils.hasText(normalized)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, definition.key() + " cannot be blank");
        }
        return normalized;
    }

    @SuppressWarnings("unchecked")
    private List<String> castStringList(Object value) {
        return (List<String>) value;
    }

    private String normalizeReason(String reason, String fallback) {
        if (!StringUtils.hasText(reason)) {
            return fallback;
        }
        return reason.trim();
    }

    private String serializeValue(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("failed to serialize admin config value", ex);
        }
    }

    private Object deserializeValue(String valueJson) {
        try {
            return objectMapper.readValue(valueJson, Object.class);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("failed to deserialize admin config value", ex);
        }
    }

    private AdminConfigHistoryResponse toHistoryResponse(AdminConfigChangeLogEntity entity) {
        return new AdminConfigHistoryResponse(
                entity.getId(),
                entity.getConfigKey(),
                deserializeValue(entity.getBeforeValueJson()),
                deserializeValue(entity.getAfterValueJson()),
                entity.getVersion(),
                entity.getReason(),
                entity.getActorUserId(),
                entity.getActorUsername(),
                entity.getCreatedAt()
        );
    }

    private ActorSnapshot resolveActorSnapshot() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return new ActorSnapshot(null, "system");
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof AuthenticatedUserPrincipal authenticatedUserPrincipal) {
            return new ActorSnapshot(authenticatedUserPrincipal.getUserId(), authenticatedUserPrincipal.getUsername());
        }
        String username = authentication.getName();
        return new ActorSnapshot(null, StringUtils.hasText(username) ? username : "system");
    }

    private record ActorSnapshot(Long userId, String username) {
        private ActorSnapshot {
            username = StringUtils.hasText(username) ? username : "system";
        }
    }
}
