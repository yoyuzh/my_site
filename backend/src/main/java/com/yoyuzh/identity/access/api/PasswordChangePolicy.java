package com.yoyuzh.identity.access.api;

import com.yoyuzh.auth.User;

public interface PasswordChangePolicy {

    IssuedAuthCredentials changePassword(User user, PasswordChangeAttempt attempt);
}
