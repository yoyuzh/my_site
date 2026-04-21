package com.yoyuzh.identity.access.internal.application;

import com.yoyuzh.identity.access.api.*;
import com.yoyuzh.identity.access.internal.application.*;
import com.yoyuzh.identity.access.internal.domain.*;
import com.yoyuzh.identity.access.internal.infra.*;
import com.yoyuzh.files.workspace.internal.application.FileService;
import com.yoyuzh.files.workspace.internal.infra.StoredFileRepository;

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
        when(identityRefreshTokenManager.issue(user, IdentityClientType.MOBILE)).thenReturn("refresh-token");

        String issued = refreshTokenService.issueRefreshToken(user, IdentityClientType.MOBILE);

        assertThat(issued).isEqualTo("refresh-token");
        verify(identityRefreshTokenManager).issue(user, IdentityClientType.MOBILE);
    }

    @Test
    void shouldDelegateRefreshTokenRotation() {
        User user = createUser(2L, "bob");
        when(identityRefreshTokenManager.rotate("old-refresh"))
                .thenReturn(new RotatedIdentityRefreshToken(user, "new-refresh", IdentityClientType.DESKTOP));

        RefreshTokenService.RotatedRefreshToken rotated = refreshTokenService.rotateRefreshToken("old-refresh");

        assertThat(rotated.user()).isSameAs(user);
        assertThat(rotated.refreshToken()).isEqualTo("new-refresh");
        assertThat(rotated.clientType()).isEqualTo(IdentityClientType.DESKTOP);
        verify(identityRefreshTokenManager).rotate("old-refresh");
    }

    @Test
    void shouldDelegateRefreshTokenRevocation() {
        refreshTokenService.revokeAllForUser(3L, IdentityClientType.MOBILE);

        verify(identityRefreshTokenManager).revokeAll(3L, IdentityClientType.MOBILE);
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
