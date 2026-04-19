package com.yoyuzh.identity.access.api;

public record IdentityUserProfileSummary(
        Long id,
        String username,
        String email
) {
}
