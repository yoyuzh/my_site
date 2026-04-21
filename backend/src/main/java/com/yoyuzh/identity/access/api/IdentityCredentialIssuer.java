package com.yoyuzh.identity.access.api;

import com.yoyuzh.identity.access.api.IdentityClientType;
import com.yoyuzh.identity.access.internal.domain.User;

public interface IdentityCredentialIssuer {

    IssuedAuthCredentials issueFresh(User user, IdentityClientType clientType);

    IssuedAuthCredentials issueWithRefreshToken(User user, String refreshToken, IdentityClientType clientType);

    IssuedAuthCredentials refresh(String rawRefreshToken, IdentityClientType defaultClientType);
}
