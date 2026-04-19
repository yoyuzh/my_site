package com.yoyuzh.auth;

import com.yoyuzh.identity.access.api.IdentityClientType;
import com.yoyuzh.identity.access.api.IdentitySessionPolicy;
import com.yoyuzh.identity.access.api.SessionState;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AuthSessionPolicy {

    private final IdentitySessionPolicy identitySessionPolicy;

    public void rotateActiveSession(User user, AuthClientType clientType) {
        apply(user, identitySessionPolicy.rotateForClient(toSessionState(user), toIdentityClientType(clientType)));
    }

    public void rotateAllActiveSessions(User user) {
        apply(user, identitySessionPolicy.rotateAll(toSessionState(user)));
    }

    public String getActiveSessionId(User user, AuthClientType clientType) {
        return identitySessionPolicy.getActiveSessionId(toSessionState(user), toIdentityClientType(clientType));
    }

    private SessionState toSessionState(User user) {
        return new SessionState(
                user.getActiveSessionId(),
                user.getDesktopActiveSessionId(),
                user.getMobileActiveSessionId());
    }

    private IdentityClientType toIdentityClientType(AuthClientType clientType) {
        return clientType == AuthClientType.MOBILE ? IdentityClientType.MOBILE : IdentityClientType.DESKTOP;
    }

    private void apply(User user, SessionState sessionState) {
        user.setActiveSessionId(sessionState.activeSessionId());
        user.setDesktopActiveSessionId(sessionState.desktopActiveSessionId());
        user.setMobileActiveSessionId(sessionState.mobileActiveSessionId());
    }
}
