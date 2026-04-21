package com.yoyuzh.identity.access.internal.domain;

public enum UserRole {
    USER,
    MODERATOR,
    ADMIN;

    public boolean canAccessAdmin() {
        return this == MODERATOR || this == ADMIN;
    }
}
