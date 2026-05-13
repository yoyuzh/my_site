package com.yoyuzh.identity.access.internal.application;

import com.yoyuzh.identity.access.api.IdentityAuthenticatedUser;
import com.yoyuzh.identity.access.api.IdentityWebDavCredentialIssueResult;
import com.yoyuzh.identity.access.internal.domain.User;
import com.yoyuzh.identity.access.internal.domain.UserRole;
import com.yoyuzh.identity.access.internal.domain.WebDavCredential;
import com.yoyuzh.identity.access.internal.infra.UserRepository;
import com.yoyuzh.identity.access.internal.infra.WebDavCredentialRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RuntimeIdentityWebDavCredentialApiTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private WebDavCredentialRepository credentialRepository;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Test
    void shouldIssueAndValidateWebDavCredential() {
        User user = createUser(1L, "alice", false);
        ArgumentCaptor<WebDavCredential> credentialCaptor = ArgumentCaptor.forClass(WebDavCredential.class);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(credentialRepository.findByUserId(1L)).thenReturn(Optional.empty());
        when(credentialRepository.save(credentialCaptor.capture())).thenAnswer(invocation -> invocation.getArgument(0));
        RuntimeIdentityWebDavCredentialApi api = new RuntimeIdentityWebDavCredentialApi(
                userRepository,
                credentialRepository,
                passwordEncoder
        );
        IdentityWebDavCredentialIssueResult issued = api.issueOrReplaceCredential(1L);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(credentialRepository.findByUserId(1L)).thenReturn(Optional.of(credentialCaptor.getValue()));

        Optional<IdentityAuthenticatedUser> authenticated = api.authenticate("alice", issued.plaintextPassword());

        assertThat(authenticated).isPresent();
        assertThat(authenticated.orElseThrow().id()).isEqualTo(1L);
    }

    @Test
    void shouldExposeMissingCredentialStatus() {
        User user = createUser(1L, "alice", false);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(credentialRepository.findByUserId(1L)).thenReturn(Optional.empty());
        RuntimeIdentityWebDavCredentialApi api = new RuntimeIdentityWebDavCredentialApi(
                userRepository,
                credentialRepository,
                passwordEncoder
        );

        var status = api.getCredentialStatus(1L);

        assertThat(status.userId()).isEqualTo(1L);
        assertThat(status.enabled()).isFalse();
        assertThat(status.createdAt()).isNull();
        assertThat(status.updatedAt()).isNull();
    }

    @Test
    void shouldExposeExistingCredentialStatusWithoutPasswordHash() {
        User user = createUser(1L, "alice", false);
        WebDavCredential credential = WebDavCredential.create(1L, passwordEncoder.encode("correct-password"), now());
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(credentialRepository.findByUserId(1L)).thenReturn(Optional.of(credential));
        RuntimeIdentityWebDavCredentialApi api = new RuntimeIdentityWebDavCredentialApi(
                userRepository,
                credentialRepository,
                passwordEncoder
        );

        var status = api.getCredentialStatus(1L);

        assertThat(status.userId()).isEqualTo(1L);
        assertThat(status.enabled()).isTrue();
        assertThat(status.createdAt()).isEqualTo(now());
        assertThat(status.updatedAt()).isEqualTo(now());
    }

    @Test
    void shouldRejectInvalidWebDavCredential() {
        User user = createUser(1L, "alice", false);
        WebDavCredential credential = WebDavCredential.create(1L, passwordEncoder.encode("correct-password"), now());
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(credentialRepository.findByUserId(1L)).thenReturn(Optional.of(credential));
        RuntimeIdentityWebDavCredentialApi api = new RuntimeIdentityWebDavCredentialApi(
                userRepository,
                credentialRepository,
                passwordEncoder
        );

        Optional<IdentityAuthenticatedUser> authenticated = api.authenticate("alice", "wrong-password");

        assertThat(authenticated).isEmpty();
    }

    @Test
    void shouldRejectBannedUser() {
        User user = createUser(1L, "alice", true);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        RuntimeIdentityWebDavCredentialApi api = new RuntimeIdentityWebDavCredentialApi(
                userRepository,
                credentialRepository,
                passwordEncoder
        );

        Optional<IdentityAuthenticatedUser> authenticated = api.authenticate("alice", "webdav-password");

        assertThat(authenticated).isEmpty();
    }

    @Test
    void shouldStoreOnlyHashedCredential() {
        User user = createUser(1L, "alice", false);
        ArgumentCaptor<WebDavCredential> credentialCaptor = ArgumentCaptor.forClass(WebDavCredential.class);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(credentialRepository.findByUserId(1L)).thenReturn(Optional.empty());
        when(credentialRepository.save(credentialCaptor.capture())).thenAnswer(invocation -> invocation.getArgument(0));
        RuntimeIdentityWebDavCredentialApi api = new RuntimeIdentityWebDavCredentialApi(
                userRepository,
                credentialRepository,
                passwordEncoder
        );

        IdentityWebDavCredentialIssueResult issued = api.issueOrReplaceCredential(1L);
        WebDavCredential stored = credentialCaptor.getValue();

        assertThat(stored.getPasswordHash()).isNotEqualTo(issued.plaintextPassword());
        assertThat(passwordEncoder.matches(issued.plaintextPassword(), stored.getPasswordHash())).isTrue();
    }

    @Test
    void shouldReplaceExistingCredential() {
        User user = createUser(1L, "alice", false);
        WebDavCredential credential = WebDavCredential.create(1L, passwordEncoder.encode("old-password"), now());
        ArgumentCaptor<WebDavCredential> credentialCaptor = ArgumentCaptor.forClass(WebDavCredential.class);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(credentialRepository.findByUserId(1L)).thenReturn(Optional.of(credential));
        when(credentialRepository.save(credentialCaptor.capture())).thenAnswer(invocation -> invocation.getArgument(0));
        RuntimeIdentityWebDavCredentialApi api = new RuntimeIdentityWebDavCredentialApi(
                userRepository,
                credentialRepository,
                passwordEncoder
        );

        IdentityWebDavCredentialIssueResult issued = api.issueOrReplaceCredential(1L);
        WebDavCredential stored = credentialCaptor.getValue();

        assertThat(passwordEncoder.matches("old-password", stored.getPasswordHash())).isFalse();
        assertThat(passwordEncoder.matches(issued.plaintextPassword(), stored.getPasswordHash())).isTrue();
        assertThat(stored.isEnabled()).isTrue();
    }

    private static User createUser(Long id, String username, boolean banned) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setEmail(username + "@example.com");
        user.setPasswordHash("password-hash");
        user.setCreatedAt(now());
        user.setRole(UserRole.USER);
        user.setBanned(banned);
        user.setActiveSessionId("session-" + id);
        user.setDesktopActiveSessionId("desktop-" + id);
        user.setMobileActiveSessionId("mobile-" + id);
        user.setStorageQuotaBytes(1024L);
        user.setMaxUploadSizeBytes(512L);
        return user;
    }

    private static LocalDateTime now() {
        return LocalDateTime.of(2026, 5, 12, 10, 0);
    }
}
