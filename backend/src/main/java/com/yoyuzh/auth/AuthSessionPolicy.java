package com.yoyuzh.auth;

import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class AuthSessionPolicy {

    public void rotateActiveSession(User user, AuthClientType clientType) {
        String nextSessionId = nextSessionId();
        if (clientType == AuthClientType.MOBILE) {
            user.setMobileActiveSessionId(nextSessionId);
            return;
        }
        user.setDesktopActiveSessionId(nextSessionId);
        user.setActiveSessionId(nextSessionId);
    }

    public void rotateAllActiveSessions(User user) {
        user.setActiveSessionId(nextSessionId());
        user.setDesktopActiveSessionId(nextSessionId());
        user.setMobileActiveSessionId(nextSessionId());
    }

    public String getActiveSessionId(User user, AuthClientType clientType) {
        return clientType == AuthClientType.MOBILE
                ? user.getMobileActiveSessionId()
                : user.getDesktopActiveSessionId();
    }

    private String nextSessionId() {
        return UUID.randomUUID().toString();
    }
}
