package com.yoyuzh.identity.access.internal.application;

import com.yoyuzh.identity.access.api.*;
import com.yoyuzh.identity.access.internal.application.*;
import com.yoyuzh.identity.access.internal.domain.*;
import com.yoyuzh.identity.access.internal.infra.*;
import com.yoyuzh.files.workspace.internal.application.FileService;
import com.yoyuzh.files.workspace.internal.infra.StoredFileRepository;

import com.yoyuzh.identity.access.internal.domain.RandomIdentitySessionPolicy;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AuthSessionPolicyTest {

    private final AuthSessionPolicy authSessionPolicy = new AuthSessionPolicy(new RandomIdentitySessionPolicy());

    @Test
    void shouldRotateOnlyRequestedClientSession() {
        User user = new User();
        user.setDesktopActiveSessionId("desktop-old");
        user.setMobileActiveSessionId("mobile-old");

        authSessionPolicy.rotateActiveSession(user, IdentityClientType.MOBILE);

        assertThat(user.getMobileActiveSessionId()).isNotBlank().isNotEqualTo("mobile-old");
        assertThat(user.getDesktopActiveSessionId()).isEqualTo("desktop-old");
    }

    @Test
    void shouldRotateAllActiveSessions() {
        User user = new User();
        user.setActiveSessionId("legacy-old");
        user.setDesktopActiveSessionId("desktop-old");
        user.setMobileActiveSessionId("mobile-old");

        authSessionPolicy.rotateAllActiveSessions(user);

        assertThat(user.getActiveSessionId()).isNotEqualTo("legacy-old");
        assertThat(user.getDesktopActiveSessionId()).isNotEqualTo("desktop-old");
        assertThat(user.getMobileActiveSessionId()).isNotEqualTo("mobile-old");
    }
}
