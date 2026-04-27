package com.yoyuzh.identity.access.internal.application;

import com.yoyuzh.boot.security.JwtProperties;
import com.yoyuzh.identity.access.api.IdentitySessionRuntimeSettingsApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RuntimeIdentitySessionRuntimeSettingsApi implements IdentitySessionRuntimeSettingsApi {

    private final JwtProperties jwtProperties;

    @Override
    public long accessExpirationSeconds() {
        return jwtProperties.getAccessExpirationSeconds();
    }

    @Override
    public long refreshExpirationSeconds() {
        return jwtProperties.getRefreshExpirationSeconds();
    }
}
