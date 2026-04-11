package com.yoyuzh.auth;

public enum UserRole {
    USER,
    MODERATOR,
    ADMIN;

    public boolean canAccessAdmin() {
        return this == MODERATOR || this == ADMIN;
    }
}
