package com.yoyuzh.identity.access.internal.application;

import com.yoyuzh.identity.access.internal.application.AuthSessionPolicy;
import com.yoyuzh.identity.access.internal.domain.User;
import com.yoyuzh.boot.security.AuthTokenInvalidationService;
import com.yoyuzh.identity.access.api.IdentityRefreshTokenManager;
import com.yoyuzh.identity.access.internal.domain.RandomIdentitySessionPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RuntimeIdentityCredentialRevocationPolicyTest {

    @Mock
    private AuthTokenInvalidationService authTokenInvalidationService;

    @Mock
    private IdentityRefreshTokenManager identityRefreshTokenManager;

    private final AuthSessionPolicy authSessionPolicy = new AuthSessionPolicy(new RandomIdentitySessionPolicy());

    @Test
    void shouldRevokeAllCredentialsAndRotateAllSessions() {
        RuntimeIdentityCredentialRevocationPolicy policy = new RuntimeIdentityCredentialRevocationPolicy(
                authTokenInvalidationService,
                identityRefreshTokenManager,
                authSessionPolicy);
        User user = createUser(1L, "alice");
        String previousActiveSessionId = user.getActiveSessionId();
        String previousDesktopSessionId = user.getDesktopActiveSessionId();
        String previousMobileSessionId = user.getMobileActiveSessionId();

        policy.revokeAll(user);

        assertThat(user.getActiveSessionId()).isNotEqualTo(previousActiveSessionId);
        assertThat(user.getDesktopActiveSessionId()).isNotEqualTo(previousDesktopSessionId);
        assertThat(user.getMobileActiveSessionId()).isNotEqualTo(previousMobileSessionId);
        verify(authTokenInvalidationService).revokeAccessTokensForUser(1L);
        verify(identityRefreshTokenManager).revokeAll(1L);
    }

    private static User createUser(Long id, String username) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setEmail(username + "@example.com");
        user.setCreatedAt(LocalDateTime.now());
        user.setActiveSessionId("active-" + id);
        user.setDesktopActiveSessionId("desktop-" + id);
        user.setMobileActiveSessionId("mobile-" + id);
        return user;
    }
}
