package com.yoyuzh.identity.access.internal.application;

import com.yoyuzh.auth.AuthClientType;
import com.yoyuzh.auth.AuthSessionPolicy;
import com.yoyuzh.auth.AuthTokenInvalidationService;
import com.yoyuzh.auth.JwtTokenProvider;
import com.yoyuzh.auth.User;
import com.yoyuzh.auth.UserRepository;
import com.yoyuzh.identity.access.api.IdentityRefreshTokenManager;
import com.yoyuzh.identity.access.api.IssuedAuthCredentials;
import com.yoyuzh.identity.access.api.RotatedIdentityRefreshToken;
import com.yoyuzh.identity.access.internal.domain.RandomIdentitySessionPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RuntimeIdentityCredentialIssuerTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private IdentityRefreshTokenManager identityRefreshTokenManager;

    @Mock
    private AuthTokenInvalidationService authTokenInvalidationService;

    private final AuthSessionPolicy authSessionPolicy = new AuthSessionPolicy(new RandomIdentitySessionPolicy());

    @Test
    void shouldIssueFreshCredentialsForClient() {
        RuntimeIdentityCredentialIssuer issuer = new RuntimeIdentityCredentialIssuer(
                userRepository,
                jwtTokenProvider,
                identityRefreshTokenManager,
                authTokenInvalidationService,
                authSessionPolicy);
        User user = createUser(1L, "alice");
        when(identityRefreshTokenManager.issue(user, AuthClientType.MOBILE)).thenReturn("refresh-token");
        when(userRepository.save(user)).thenReturn(user);
        when(jwtTokenProvider.generateAccessToken(eq(1L), eq("alice"), anyString(), eq(AuthClientType.MOBILE)))
                .thenReturn("access-token");

        IssuedAuthCredentials issued = issuer.issueFresh(user, AuthClientType.MOBILE);

        verify(authTokenInvalidationService).revokeAccessTokensForUser(1L, AuthClientType.MOBILE);
        verify(identityRefreshTokenManager).revokeAll(1L, AuthClientType.MOBILE);
        assertThat(issued.accessToken()).isEqualTo("access-token");
        assertThat(issued.refreshToken()).isEqualTo("refresh-token");
        assertThat(issued.user()).isSameAs(user);
    }

    @Test
    void shouldIssueCredentialsWithProvidedRefreshToken() {
        RuntimeIdentityCredentialIssuer issuer = new RuntimeIdentityCredentialIssuer(
                userRepository,
                jwtTokenProvider,
                identityRefreshTokenManager,
                authTokenInvalidationService,
                authSessionPolicy);
        User user = createUser(2L, "bob");
        String previousDesktopSessionId = user.getDesktopActiveSessionId();
        when(userRepository.save(user)).thenReturn(user);
        when(jwtTokenProvider.generateAccessToken(eq(2L), eq("bob"), anyString(), eq(AuthClientType.DESKTOP)))
                .thenReturn("access-token");

        IssuedAuthCredentials issued = issuer.issueWithRefreshToken(user, "provided-refresh", AuthClientType.DESKTOP);

        assertThat(user.getDesktopActiveSessionId()).isNotEqualTo(previousDesktopSessionId);
        assertThat(issued.refreshToken()).isEqualTo("provided-refresh");
        assertThat(issued.accessToken()).isEqualTo("access-token");
    }

    @Test
    void shouldRefreshCredentialsViaRotatedRefreshToken() {
        RuntimeIdentityCredentialIssuer issuer = new RuntimeIdentityCredentialIssuer(
                userRepository,
                jwtTokenProvider,
                identityRefreshTokenManager,
                authTokenInvalidationService,
                authSessionPolicy);
        User user = createUser(3L, "carol");
        when(identityRefreshTokenManager.rotate("old-refresh"))
                .thenReturn(new RotatedIdentityRefreshToken(user, "new-refresh", AuthClientType.DESKTOP));
        when(userRepository.save(user)).thenReturn(user);
        when(jwtTokenProvider.generateAccessToken(eq(3L), eq("carol"), anyString(), eq(AuthClientType.DESKTOP)))
                .thenReturn("new-access");

        IssuedAuthCredentials issued = issuer.refresh("old-refresh", AuthClientType.MOBILE);

        assertThat(issued.refreshToken()).isEqualTo("new-refresh");
        assertThat(issued.accessToken()).isEqualTo("new-access");
    }

    private static User createUser(Long id, String username) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setEmail(username + "@example.com");
        user.setCreatedAt(LocalDateTime.now());
        user.setDesktopActiveSessionId("desktop-" + id);
        user.setMobileActiveSessionId("mobile-" + id);
        return user;
    }
}
