package com.yoyuzh.identity.access.api;

public interface IdentitySessionPolicy {

    SessionState rotateForClient(SessionState current, IdentityClientType clientType);

    SessionState rotateAll(SessionState current);

    String getActiveSessionId(SessionState current, IdentityClientType clientType);
}
