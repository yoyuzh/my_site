package com.yoyuzh.identity.access.api;

import java.util.Map;

public record UpdateUserSettingsRequest(
        String preferredLanguage,
        String preferredTheme,
        Boolean disableViewSync,
        Map<String, String> defaultOpenWithByExt
) {
}
