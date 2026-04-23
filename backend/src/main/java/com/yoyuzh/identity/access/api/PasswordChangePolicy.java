package com.yoyuzh.identity.access.api;

public interface PasswordChangePolicy {

    IssuedAuthCredentials changePassword(Long userId, PasswordChangeAttempt attempt);
}
