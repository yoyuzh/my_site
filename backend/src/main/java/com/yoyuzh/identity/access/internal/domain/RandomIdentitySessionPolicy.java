package com.yoyuzh.identity.access.internal.domain;

import com.yoyuzh.identity.access.api.IdentityClientType;
import com.yoyuzh.identity.access.api.IdentitySessionPolicy;
import com.yoyuzh.identity.access.api.SessionState;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class RandomIdentitySessionPolicy implements IdentitySessionPolicy {

    @Override
    public SessionState rotateForClient(SessionState current, IdentityClientType clientType) {
        if (clientType == IdentityClientType.MOBILE) {
            return new SessionState(
                    current.activeSessionId(),
                    current.desktopActiveSessionId(),
                    nextSessionId());
        }
        String nextDesktopSessionId = nextSessionId();
        return new SessionState(
                nextDesktopSessionId,
                nextDesktopSessionId,
                current.mobileActiveSessionId());
    }

    @Override
    public SessionState rotateAll(SessionState current) {
        return new SessionState(nextSessionId(), nextSessionId(), nextSessionId());
    }

    @Override
    public String getActiveSessionId(SessionState current, IdentityClientType clientType) {
        return clientType == IdentityClientType.MOBILE
                ? current.mobileActiveSessionId()
                : current.desktopActiveSessionId();
    }

    private String nextSessionId() {
        return UUID.randomUUID().toString();
    }
}
