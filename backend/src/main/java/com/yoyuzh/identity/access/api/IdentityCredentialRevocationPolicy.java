package com.yoyuzh.identity.access.api;

import com.yoyuzh.auth.User;

public interface IdentityCredentialRevocationPolicy {

    void revokeAll(User user);
}
