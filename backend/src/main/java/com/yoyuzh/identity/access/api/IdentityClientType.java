package com.yoyuzh.identity.access.api;

import org.springframework.util.StringUtils;

public enum IdentityClientType {
    DESKTOP,
    MOBILE;

    public static final String HEADER_NAME = "X-Yoyuzh-Client";

    public static IdentityClientType fromHeader(String rawValue) {
        if (!StringUtils.hasText(rawValue)) {
            return DESKTOP;
        }

        String normalized = rawValue.trim().toUpperCase();
        if ("MOBILE".equals(normalized)) {
            return MOBILE;
        }

        return DESKTOP;
    }
}
