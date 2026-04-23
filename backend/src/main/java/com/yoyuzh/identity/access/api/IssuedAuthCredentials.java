package com.yoyuzh.identity.access.api;

public record IssuedAuthCredentials(IdentityUserSnapshot user, String accessToken, String refreshToken) {}
