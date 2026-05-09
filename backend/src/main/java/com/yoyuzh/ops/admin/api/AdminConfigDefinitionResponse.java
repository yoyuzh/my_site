package com.yoyuzh.ops.admin.api;

import java.util.List;
import java.util.Map;

public record AdminConfigDefinitionResponse(
        String key,
        String group,
        String subgroup,
        String title,
        String description,
        String type,
        Object defaultValue,
        Object value,
        List<Option> options,
        boolean required,
        boolean editable,
        boolean sensitive,
        boolean restartRequired,
        Map<String, Object> validationRules,
        String permissionCode,
        String source
) {

    public record Option(String label, String value) {
    }
}
