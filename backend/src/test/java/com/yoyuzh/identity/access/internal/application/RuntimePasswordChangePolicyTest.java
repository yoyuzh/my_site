package com.yoyuzh.identity.access.internal.application;

import com.yoyuzh.identity.access.api.IdentityClientType;
import com.yoyuzh.identity.access.api.IdentityRoleName;
import com.yoyuzh.identity.access.api.IdentityUserSnapshot;
import com.yoyuzh.identity.access.internal.domain.User;
import com.yoyuzh.identity.access.internal.infra.UserRepository;
import com.yoyuzh.shared.kernel.BusinessException;
import com.yoyuzh.identity.access.api.IdentityCredentialIssuer;
import com.yoyuzh.identity.access.api.IdentityCredentialRevocationPolicy;
import com.yoyuzh.identity.access.api.IssuedAuthCredentials;
import com.yoyuzh.identity.access.api.PasswordChangeAttempt;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RuntimePasswordChangePolicyTest {

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private IdentityCredentialRevocationPolicy identityCredentialRevocationPolicy;

    @Mock
    private IdentityCredentialIssuer identityCredentialIssuer;

    @Mock
    private UserRepository userRepository;

    @Test
    void shouldRotateAllSessionsAndIssueFreshCredentialsAfterPasswordChange() {
        RuntimePasswordChangePolicy policy = new RuntimePasswordChangePolicy(
                passwordEncoder,
                identityCredentialRevocationPolicy,
                identityCredentialIssuer,
                userRepository);
        User user = createUser(1L, "alice");
        String previousDesktopSessionId = user.getDesktopActiveSessionId();
        String previousMobileSessionId = user.getMobileActiveSessionId();

        when(passwordEncoder.matches("OldPass1!", "encoded-old")).thenReturn(true);
        when(passwordEncoder.encode("NewPass1!A")).thenReturn("encoded-new");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);
        when(identityCredentialIssuer.issueFresh(user.getId(), IdentityClientType.DESKTOP))
                .thenReturn(new IssuedAuthCredentials(snapshot(user), "new-access", "new-refresh"));

        IssuedAuthCredentials issued = policy.changePassword(
                user.getId(),
                new PasswordChangeAttempt("OldPass1!", "NewPass1!A", IdentityClientType.DESKTOP));

        assertThat(user.getPasswordHash()).isEqualTo("encoded-new");
        assertThat(user.getDesktopActiveSessionId()).isEqualTo(previousDesktopSessionId);
        assertThat(user.getMobileActiveSessionId()).isEqualTo(previousMobileSessionId);
        verify(identityCredentialRevocationPolicy).revokeAll(user.getId());
        verify(identityCredentialIssuer).issueFresh(user.getId(), IdentityClientType.DESKTOP);
        assertThat(issued.accessToken()).isEqualTo("new-access");
        assertThat(issued.refreshToken()).isEqualTo("new-refresh");
    }

    @Test
    void shouldRejectPasswordChangeWhenCurrentPasswordIsWrong() {
        RuntimePasswordChangePolicy policy = new RuntimePasswordChangePolicy(
                passwordEncoder,
                identityCredentialRevocationPolicy,
                identityCredentialIssuer,
                userRepository);
        User user = createUser(2L, "bob");

        when(userRepository.findById(2L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("WrongPass1!", "encoded-old")).thenReturn(false);

        assertThatThrownBy(() -> policy.changePassword(
                user.getId(),
                new PasswordChangeAttempt("WrongPass1!", "NewPass1!A", IdentityClientType.DESKTOP)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("当前密码错误");

        verify(identityCredentialRevocationPolicy, never()).revokeAll(user.getId());
        verify(identityCredentialIssuer, never()).issueFresh(user.getId(), IdentityClientType.DESKTOP);
    }

    private static User createUser(Long id, String username) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setEmail(username + "@example.com");
        user.setPasswordHash("encoded-old");
        user.setCreatedAt(LocalDateTime.now());
        user.setDesktopActiveSessionId("desktop-" + id);
        user.setMobileActiveSessionId("mobile-" + id);
        return user;
    }

    private static IdentityUserSnapshot snapshot(User user) {
        return new IdentityUserSnapshot(
                user.getId(),
                user.getUsername(),
                user.getUsername(),
                user.getEmail(),
                null,
                null,
                "zh-CN",
                null,
                null,
                null,
                IdentityRoleName.USER,
                user.getCreatedAt(),
                user.getStorageQuotaBytes(),
                user.getMaxUploadSizeBytes()
        );
    }
}
