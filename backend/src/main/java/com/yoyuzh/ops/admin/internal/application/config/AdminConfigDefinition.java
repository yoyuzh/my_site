package com.yoyuzh.ops.admin.internal.application.config;

import com.yoyuzh.ops.admin.api.AdminConfigDefinitionResponse;
import com.yoyuzh.ops.admin.api.AdminSettingsResponse;
import com.yoyuzh.ops.admin.internal.application.AdminRuntimeSettingsService;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

public record AdminConfigDefinition(
        String key,
        String group,
        String subgroup,
        String title,
        String description,
        String type,
        Function<AdminRuntimeSettingsService.State, Object> defaultValueResolver,
        Function<AdminSettingsResponse, Object> valueResolver,
        List<AdminConfigDefinitionResponse.Option> options,
        boolean required,
        boolean editable,
        boolean sensitive,
        boolean restartRequired,
        Map<String, Object> validationRules,
        String permissionCode,
        String source
) {

    public AdminConfigDefinitionResponse toResponse(AdminRuntimeSettingsService.State defaults,
                                                    AdminSettingsResponse settings) {
        return new AdminConfigDefinitionResponse(
                key,
                group,
                subgroup,
                title,
                description,
                type,
                defaultValueResolver == null ? null : defaultValueResolver.apply(defaults),
                valueResolver == null ? null : valueResolver.apply(settings),
                options,
                required,
                editable,
                sensitive,
                restartRequired,
                validationRules,
                permissionCode,
                source
        );
    }
}
