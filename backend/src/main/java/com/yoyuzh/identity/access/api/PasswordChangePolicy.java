package com.yoyuzh.identity.access.api;

import com.yoyuzh.identity.access.internal.domain.User;

public interface PasswordChangePolicy {

    IssuedAuthCredentials changePassword(User user, PasswordChangeAttempt attempt);
}
