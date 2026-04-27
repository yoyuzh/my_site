package com.yoyuzh.identity.access.api;

public interface IdentitySessionRuntimeSettingsApi {

    long accessExpirationSeconds();

    long refreshExpirationSeconds();
}
