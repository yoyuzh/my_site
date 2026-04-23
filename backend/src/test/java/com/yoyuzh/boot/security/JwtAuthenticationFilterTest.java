package com.yoyuzh.boot.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yoyuzh.identity.access.api.IdentityClientType;
import com.yoyuzh.identity.access.api.IdentityAuthenticatedUser;
import com.yoyuzh.identity.access.api.IdentityRoleName;
import com.yoyuzh.ops.admin.api.AdminRequestMetricsApi;
import com.yoyuzh.shared.kernel.BusinessException;
import com.yoyuzh.shared.kernel.ErrorCode;
import jakarta.servlet.FilterChain;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtTokenProvider jwtTokenProvider;
    @Mock
    private AuthTokenInvalidationService authTokenInvalidationService;
    @Mock
    private CustomUserDetailsService userDetailsService;
    @Mock
    private AdminRequestMetricsApi adminRequestMetricsApi;
    @Mock
    private FilterChain filterChain;

    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthenticationFilter(
                jwtTokenProvider,
                authTokenInvalidationService,
                userDetailsService,
                adminRequestMetricsApi
        );
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldPassThroughRequestWithNoAuthorizationHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(jwtTokenProvider, never()).validateToken(any());
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void shouldPassThroughRequestWithNonBearerAuthorizationHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Basic dXNlcjpwYXNz");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(jwtTokenProvider, never()).validateToken(any());
    }

    @Test
    void shouldPassThroughRequestWithInvalidToken() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer invalid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(jwtTokenProvider.validateToken("invalid-token")).thenReturn(false);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void shouldPassThroughWhenAccessTokenWasRevokedInRedis() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer valid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        Instant issuedAt = Instant.now().minusSeconds(30);
        when(jwtTokenProvider.validateToken("valid-token")).thenReturn(true);
        when(jwtTokenProvider.getUserId("valid-token")).thenReturn(1L);
        when(jwtTokenProvider.getClientType("valid-token")).thenReturn(IdentityClientType.DESKTOP);
        when(jwtTokenProvider.getIssuedAt("valid-token")).thenReturn(issuedAt);
        when(authTokenInvalidationService.isAccessTokenRevoked(1L, IdentityClientType.DESKTOP, issuedAt)).thenReturn(true);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(userDetailsService, never()).loadAuthenticatedUser(any());
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void shouldPassThroughWhenUserNotFound() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer valid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(jwtTokenProvider.validateToken("valid-token")).thenReturn(true);
        when(jwtTokenProvider.getUserId("valid-token")).thenReturn(1L);
        when(jwtTokenProvider.getClientType("valid-token")).thenReturn(IdentityClientType.DESKTOP);
        when(jwtTokenProvider.getIssuedAt("valid-token")).thenReturn(Instant.now());
        when(jwtTokenProvider.getUsername("valid-token")).thenReturn("alice");
        when(userDetailsService.loadAuthenticatedUser("alice"))
                .thenThrow(new BusinessException(ErrorCode.NOT_LOGGED_IN, "user not found"));

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void shouldPassThroughWhenSessionIdDoesNotMatch() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer valid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        IdentityAuthenticatedUser authenticatedUser = createAuthenticatedUser("alice", "session-1", null);
        when(jwtTokenProvider.validateToken("valid-token")).thenReturn(true);
        when(jwtTokenProvider.getUserId("valid-token")).thenReturn(1L);
        when(jwtTokenProvider.getClientType("valid-token")).thenReturn(IdentityClientType.DESKTOP);
        when(jwtTokenProvider.getIssuedAt("valid-token")).thenReturn(Instant.now());
        when(jwtTokenProvider.getUsername("valid-token")).thenReturn("alice");
        when(userDetailsService.loadAuthenticatedUser("alice")).thenReturn(authenticatedUser);
        when(jwtTokenProvider.hasMatchingSession("valid-token", authenticatedUser)).thenReturn(false);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void shouldPassThroughWhenUserIsDisabled() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer valid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        IdentityAuthenticatedUser authenticatedUser = createAuthenticatedUser("alice", "session-1", null);
        UserDetails disabledUserDetails = org.springframework.security.core.userdetails.User.builder()
                .username("alice")
                .password("hashed")
                .disabled(true)
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_USER")))
                .build();
        when(jwtTokenProvider.validateToken("valid-token")).thenReturn(true);
        when(jwtTokenProvider.getUserId("valid-token")).thenReturn(1L);
        when(jwtTokenProvider.getClientType("valid-token")).thenReturn(IdentityClientType.DESKTOP);
        when(jwtTokenProvider.getIssuedAt("valid-token")).thenReturn(Instant.now());
        when(jwtTokenProvider.getUsername("valid-token")).thenReturn("alice");
        when(userDetailsService.loadAuthenticatedUser("alice")).thenReturn(authenticatedUser);
        when(jwtTokenProvider.hasMatchingSession("valid-token", authenticatedUser)).thenReturn(true);
        when(userDetailsService.loadUserByUsername("alice")).thenReturn(disabledUserDetails);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void shouldSetAuthenticationWhenTokenIsValidAndUserIsActive() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer valid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        IdentityAuthenticatedUser authenticatedUser = createAuthenticatedUser("alice", "session-1", null);
        UserDetails activeUserDetails = org.springframework.security.core.userdetails.User.builder()
                .username("alice")
                .password("hashed")
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_USER")))
                .build();
        when(jwtTokenProvider.validateToken("valid-token")).thenReturn(true);
        when(jwtTokenProvider.getUserId("valid-token")).thenReturn(1L);
        when(jwtTokenProvider.getClientType("valid-token")).thenReturn(IdentityClientType.DESKTOP);
        when(jwtTokenProvider.getIssuedAt("valid-token")).thenReturn(Instant.now());
        when(jwtTokenProvider.getUsername("valid-token")).thenReturn("alice");
        when(userDetailsService.loadAuthenticatedUser("alice")).thenReturn(authenticatedUser);
        when(jwtTokenProvider.hasMatchingSession("valid-token", authenticatedUser)).thenReturn(true);
        when(userDetailsService.loadUserByUsername("alice")).thenReturn(activeUserDetails);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getName()).isEqualTo("alice");
        verify(adminRequestMetricsApi).recordUserOnline(1L, "alice");
    }

    private IdentityAuthenticatedUser createAuthenticatedUser(String username, String desktopSessionId, String mobileSessionId) {
        return new IdentityAuthenticatedUser(
                1L,
                username,
                "hashed",
                IdentityRoleName.USER,
                false,
                desktopSessionId,
                desktopSessionId,
                mobileSessionId,
                1024L,
                1024L
        );
    }

    private static <T> T any() {
        return org.mockito.ArgumentMatchers.any();
    }
}
