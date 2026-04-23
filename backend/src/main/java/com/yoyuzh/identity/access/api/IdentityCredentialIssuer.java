package com.yoyuzh.identity.access.api;

public interface IdentityCredentialIssuer {

    IssuedAuthCredentials issueFresh(Long userId, IdentityClientType clientType);

    IssuedAuthCredentials issueWithRefreshToken(Long userId, String refreshToken, IdentityClientType clientType);

    IssuedAuthCredentials refresh(String rawRefreshToken, IdentityClientType defaultClientType);
}
