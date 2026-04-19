package com.yoyuzh.identity.access.api;

import com.yoyuzh.auth.AuthClientType;
import com.yoyuzh.auth.User;

public interface IdentityCredentialIssuer {

    IssuedAuthCredentials issueFresh(User user, AuthClientType clientType);

    IssuedAuthCredentials issueWithRefreshToken(User user, String refreshToken, AuthClientType clientType);

    IssuedAuthCredentials refresh(String rawRefreshToken, AuthClientType defaultClientType);
}
