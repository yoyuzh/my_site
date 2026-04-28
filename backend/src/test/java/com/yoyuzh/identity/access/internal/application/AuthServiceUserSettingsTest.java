package com.yoyuzh.identity.access.internal.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yoyuzh.boot.security.AuthTokenInvalidationService;
import com.yoyuzh.files.content.api.FileContentStorage;
import com.yoyuzh.files.workspace.api.WorkspaceBootstrapApi;
import com.yoyuzh.identity.access.api.DevLoginRoleResolver;
import com.yoyuzh.identity.access.api.IdentityCredentialIssuer;
import com.yoyuzh.identity.access.api.IdentityStorageUsageQuery;
import com.yoyuzh.identity.access.api.LoginAdmissionPolicy;
import com.yoyuzh.identity.access.api.PasswordChangePolicy;
import com.yoyuzh.identity.access.api.ProfileUpdateAdmissionPolicy;
import com.yoyuzh.identity.access.api.RegistrationAdmissionPolicy;
import com.yoyuzh.identity.access.api.UpdateUserSettingsRequest;
import com.yoyuzh.identity.access.internal.domain.User;
import com.yoyuzh.identity.access.internal.infra.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceUserSettingsTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private WorkspaceBootstrapApi workspaceBootstrapApi;

    @Mock
    private FileContentStorage fileContentStorage;

    @Mock
    private RegistrationAdmissionPolicy registrationAdmissionPolicy;

    @Mock
    private DevLoginRoleResolver devLoginRoleResolver;

    @Mock
    private ProfileUpdateAdmissionPolicy profileUpdateAdmissionPolicy;

    @Mock
    private LoginAdmissionPolicy loginAdmissionPolicy;

    @Mock
    private PasswordChangePolicy passwordChangePolicy;

    @Mock
    private AuthTokenInvalidationService authTokenInvalidationService;

    @Mock
    private IdentityCredentialIssuer identityCredentialIssuer;

    @Mock
    private IdentityStorageUsageQuery identityStorageUsageQuery;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private AuthService authService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(authService, "avatarService", new AvatarService(userRepository, fileContentStorage));
    }

    @Test
    void shouldReturnEmptyOpenWithDefaultsWhenUserHasNoPreference() {
        User user = user("alice");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));

        var response = authService.getSettings("alice");

        assertThat(response.defaultOpenWithByExt()).isEmpty();
    }

    @Test
    void shouldReplaceOpenWithDefaultsWhenUpdatingSettings() {
        User user = user("alice");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);

        var response = authService.updateSettings(
                "alice",
                new UpdateUserSettingsRequest(
                        "en-US",
                        "dark",
                        true,
                        Map.of("md", "markdown", "txt", "code-monaco")
                )
        );

        assertThat(response.preferredLanguage()).isEqualTo("en-US");
        assertThat(response.preferredTheme()).isEqualTo("dark");
        assertThat(response.disableViewSync()).isTrue();
        assertThat(response.defaultOpenWithByExt()).containsEntry("md", "markdown");
        assertThat(response.defaultOpenWithByExt()).containsEntry("txt", "code-monaco");
        verify(userRepository).save(user);
    }

    @Test
    void shouldClearOpenWithDefaultsWhenUpdatingWithEmptyMap() {
        User user = user("alice");
        user.setDefaultOpenWithByExtJson("{\"md\":\"markdown\"}");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);

        var response = authService.updateSettings(
                "alice",
                new UpdateUserSettingsRequest("zh-CN", "system", false, Map.of())
        );

        assertThat(response.defaultOpenWithByExt()).isEmpty();
        assertThat(user.getDefaultOpenWithByExtJson()).isEqualTo("{}");
    }

    private User user(String username) {
        User user = new User();
        user.setId(1L);
        user.setUsername(username);
        user.setDisplayName(username);
        user.setEmail(username + "@example.com");
        user.setPreferredLanguage("zh-CN");
        user.setPreferredTheme("system");
        return user;
    }
}
