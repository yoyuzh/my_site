package com.yoyuzh.identity.access.api;

public record IdentityWebDavCredentialIssueResult(
        Long userId,
        String plaintextPassword
) {
}
