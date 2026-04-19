package com.yoyuzh.auth;

import com.yoyuzh.identity.access.api.IdentityRefreshTokenManager;
import com.yoyuzh.identity.access.api.RotatedIdentityRefreshToken;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock
    private IdentityRefreshTokenManager identityRefreshTokenManager;

    @InjectMocks
    private RefreshTokenService refreshTokenService;

    @Test
    void shouldDelegateRefreshTokenIssuance() {
        User user = createUser(1L, "alice");
        when(identityRefreshTokenManager.issue(user, AuthClientType.MOBILE)).thenReturn("refresh-token");

        String issued = refreshTokenService.issueRefreshToken(user, AuthClientType.MOBILE);

        assertThat(issued).isEqualTo("refresh-token");
        verify(identityRefreshTokenManager).issue(user, AuthClientType.MOBILE);
    }

    @Test
    void shouldDelegateRefreshTokenRotation() {
        User user = createUser(2L, "bob");
        when(identityRefreshTokenManager.rotate("old-refresh"))
                .thenReturn(new RotatedIdentityRefreshToken(user, "new-refresh", AuthClientType.DESKTOP));

        RefreshTokenService.RotatedRefreshToken rotated = refreshTokenService.rotateRefreshToken("old-refresh");

        assertThat(rotated.user()).isSameAs(user);
        assertThat(rotated.refreshToken()).isEqualTo("new-refresh");
        assertThat(rotated.clientType()).isEqualTo(AuthClientType.DESKTOP);
        verify(identityRefreshTokenManager).rotate("old-refresh");
    }

    @Test
    void shouldDelegateRefreshTokenRevocation() {
        refreshTokenService.revokeAllForUser(3L, AuthClientType.MOBILE);

        verify(identityRefreshTokenManager).revokeAll(3L, AuthClientType.MOBILE);
    }

    private static User createUser(Long id, String username) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setEmail(username + "@example.com");
        user.setPasswordHash("encoded-password");
        user.setCreatedAt(LocalDateTime.now());
        return user;
    }
}
