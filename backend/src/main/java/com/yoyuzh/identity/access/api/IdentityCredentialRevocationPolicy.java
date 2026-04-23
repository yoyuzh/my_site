package com.yoyuzh.identity.access.api;

public interface IdentityCredentialRevocationPolicy {

    void revokeAll(Long userId);
}
