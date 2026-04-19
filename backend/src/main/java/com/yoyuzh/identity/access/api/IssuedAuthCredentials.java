package com.yoyuzh.identity.access.api;

import com.yoyuzh.auth.User;

public record IssuedAuthCredentials(User user, String accessToken, String refreshToken) {}
