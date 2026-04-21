package com.yoyuzh.identity.access.api;

import com.yoyuzh.identity.access.internal.domain.User;

public interface IdentityCredentialRevocationPolicy {

    void revokeAll(User user);
}
