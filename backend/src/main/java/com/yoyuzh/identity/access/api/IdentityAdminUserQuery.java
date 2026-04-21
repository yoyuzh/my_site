package com.yoyuzh.identity.access.api;

public record IdentityAdminUserQuery(
        int page,
        int size,
        String query
) {
}
