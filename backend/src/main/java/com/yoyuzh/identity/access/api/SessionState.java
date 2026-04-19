package com.yoyuzh.identity.access.api;

public record SessionState(
        String activeSessionId,
        String desktopActiveSessionId,
        String mobileActiveSessionId) {}
