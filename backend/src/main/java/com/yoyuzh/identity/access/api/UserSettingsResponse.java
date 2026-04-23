package com.yoyuzh.identity.access.api;

public record UserSettingsResponse(
        String displayName,
        String preferredLanguage,
        String preferredTheme,
        boolean disableViewSync
) {
}
