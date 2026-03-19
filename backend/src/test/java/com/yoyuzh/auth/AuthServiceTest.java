package com.yoyuzh.auth;

import com.yoyuzh.auth.dto.AuthResponse;
import com.yoyuzh.auth.dto.LoginRequest;
import com.yoyuzh.auth.dto.RegisterRequest;
import com.yoyuzh.common.BusinessException;
import com.yoyuzh.files.FileService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private FileService fileService;

    @InjectMocks
    private AuthService authService;

    @Test
    void shouldRegisterUserWithEncryptedPassword() {
        RegisterRequest request = new RegisterRequest("alice", "alice@example.com", "StrongPass1!");
        when(userRepository.existsByUsername("alice")).thenReturn(false);
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(false);
        when(passwordEncoder.encode("StrongPass1!")).thenReturn("encoded-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(1L);
            user.setCreatedAt(LocalDateTime.now());
            return user;
        });
        when(jwtTokenProvider.generateAccessToken(1L, "alice")).thenReturn("access-token");
        when(refreshTokenService.issueRefreshToken(any(User.class))).thenReturn("refresh-token");

        AuthResponse response = authService.register(request);

        assertThat(response.token()).isEqualTo("access-token");
        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isEqualTo("refresh-token");
        assertThat(response.user().username()).isEqualTo("alice");
        verify(passwordEncoder).encode("StrongPass1!");
        verify(fileService).ensureDefaultDirectories(any(User.class));
    }

    @Test
    void shouldRejectDuplicateUsernameOnRegister() {
        RegisterRequest request = new RegisterRequest("alice", "alice@example.com", "StrongPass1!");
        when(userRepository.existsByUsername("alice")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("用户名已存在");
    }

    @Test
    void shouldLoginAndReturnToken() {
        LoginRequest request = new LoginRequest("alice", "plain-password");
        User user = new User();
        user.setId(1L);
        user.setUsername("alice");
        user.setEmail("alice@example.com");
        user.setPasswordHash("encoded-password");
        user.setCreatedAt(LocalDateTime.now());
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(jwtTokenProvider.generateAccessToken(1L, "alice")).thenReturn("access-token");
        when(refreshTokenService.issueRefreshToken(user)).thenReturn("refresh-token");

        AuthResponse response = authService.login(request);

        verify(authenticationManager).authenticate(
                new UsernamePasswordAuthenticationToken("alice", "plain-password"));
        assertThat(response.token()).isEqualTo("access-token");
        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isEqualTo("refresh-token");
        assertThat(response.user().email()).isEqualTo("alice@example.com");
        verify(fileService).ensureDefaultDirectories(user);
    }

    @Test
    void shouldRotateRefreshTokenAndReturnNewCredentials() {
        User user = new User();
        user.setId(1L);
        user.setUsername("alice");
        user.setEmail("alice@example.com");
        user.setCreatedAt(LocalDateTime.now());
        when(refreshTokenService.rotateRefreshToken("old-refresh"))
                .thenReturn(new RefreshTokenService.RotatedRefreshToken(user, "new-refresh"));
        when(jwtTokenProvider.generateAccessToken(1L, "alice")).thenReturn("new-access");

        AuthResponse response = authService.refresh("old-refresh");

        assertThat(response.token()).isEqualTo("new-access");
        assertThat(response.accessToken()).isEqualTo("new-access");
        assertThat(response.refreshToken()).isEqualTo("new-refresh");
        assertThat(response.user().username()).isEqualTo("alice");
    }

    @Test
    void shouldThrowBusinessExceptionWhenAuthenticationFails() {
        LoginRequest request = new LoginRequest("alice", "wrong-password");
        when(authenticationManager.authenticate(any()))
                .thenThrow(new BadCredentialsException("bad credentials"));

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("用户名或密码错误");
    }

    @Test
    void shouldCreateDefaultDirectoriesForDevLoginUser() {
        when(userRepository.findByUsername("demo")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("1")).thenReturn("encoded-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(9L);
            user.setCreatedAt(LocalDateTime.now());
            return user;
        });
        when(jwtTokenProvider.generateAccessToken(9L, "demo")).thenReturn("access-token");
        when(refreshTokenService.issueRefreshToken(any(User.class))).thenReturn("refresh-token");

        AuthResponse response = authService.devLogin("demo");

        assertThat(response.user().username()).isEqualTo("demo");
        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isEqualTo("refresh-token");
        verify(fileService).ensureDefaultDirectories(any(User.class));
    }
}
