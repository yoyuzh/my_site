package com.yoyuzh.identity.access.api;

import java.util.Map;

public record UserSettingsResponse(
        String displayName,
        String preferredLanguage,
        String preferredTheme,
        boolean disableViewSync,
        Map<String, String> defaultOpenWithByExt
) {
}
