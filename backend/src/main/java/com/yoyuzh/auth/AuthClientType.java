package com.yoyuzh.auth;

import org.springframework.util.StringUtils;

public enum AuthClientType {
    DESKTOP,
    MOBILE;

    public static final String HEADER_NAME = "X-Yoyuzh-Client";

    public static AuthClientType fromHeader(String rawValue) {
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
