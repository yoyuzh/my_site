package com.yoyuzh.identity.access.internal.domain;

import com.yoyuzh.identity.access.api.IdentityClientType;
import com.yoyuzh.identity.access.api.IdentitySessionPolicy;
import com.yoyuzh.identity.access.api.SessionState;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RandomIdentitySessionPolicyTest {

    private final IdentitySessionPolicy identitySessionPolicy = new RandomIdentitySessionPolicy();

    @Test
    void shouldRotateOnlyRequestedClientSession() {
        SessionState current = new SessionState("legacy-old", "desktop-old", "mobile-old");

        SessionState rotated = identitySessionPolicy.rotateForClient(current, IdentityClientType.MOBILE);

        assertThat(rotated.mobileActiveSessionId()).isNotBlank().isNotEqualTo("mobile-old");
        assertThat(rotated.desktopActiveSessionId()).isEqualTo("desktop-old");
        assertThat(rotated.activeSessionId()).isEqualTo("legacy-old");
    }

    @Test
    void shouldRotateAllActiveSessions() {
        SessionState current = new SessionState("legacy-old", "desktop-old", "mobile-old");

        SessionState rotated = identitySessionPolicy.rotateAll(current);

        assertThat(rotated.activeSessionId()).isNotEqualTo("legacy-old");
        assertThat(rotated.desktopActiveSessionId()).isNotEqualTo("desktop-old");
        assertThat(rotated.mobileActiveSessionId()).isNotEqualTo("mobile-old");
    }

    @Test
    void shouldReadActiveSessionIdForRequestedClient() {
        SessionState current = new SessionState("legacy-old", "desktop-current", "mobile-current");

        assertThat(identitySessionPolicy.getActiveSessionId(current, IdentityClientType.DESKTOP))
                .isEqualTo("desktop-current");
        assertThat(identitySessionPolicy.getActiveSessionId(current, IdentityClientType.MOBILE))
                .isEqualTo("mobile-current");
    }
}
