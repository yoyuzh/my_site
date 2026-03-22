package com.yoyuzh.config;

import com.yoyuzh.auth.CustomUserDetailsService;
import com.yoyuzh.auth.JwtTokenProvider;
import com.yoyuzh.auth.User;
import com.yoyuzh.common.BusinessException;
import com.yoyuzh.common.ErrorCode;
import jakarta.servlet.FilterChain;
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

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtTokenProvider jwtTokenProvider;
    @Mock
    private CustomUserDetailsService userDetailsService;
    @Mock
    private FilterChain filterChain;

    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthenticationFilter(jwtTokenProvider, userDetailsService);
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
    void shouldPassThroughWhenUserNotFound() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer valid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(jwtTokenProvider.validateToken("valid-token")).thenReturn(true);
        when(jwtTokenProvider.getUsername("valid-token")).thenReturn("alice");
        when(userDetailsService.loadDomainUser("alice"))
                .thenThrow(new BusinessException(ErrorCode.NOT_LOGGED_IN, "用户不存在"));

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void shouldPassThroughWhenSessionIdDoesNotMatch() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer valid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        User domainUser = createDomainUser("alice", "session-1");
        when(jwtTokenProvider.validateToken("valid-token")).thenReturn(true);
        when(jwtTokenProvider.getUsername("valid-token")).thenReturn("alice");
        when(userDetailsService.loadDomainUser("alice")).thenReturn(domainUser);
        when(jwtTokenProvider.hasMatchingSession("valid-token", "session-1")).thenReturn(false);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void shouldPassThroughWhenUserIsDisabled() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer valid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        User domainUser = createDomainUser("alice", "session-1");
        UserDetails disabledUserDetails = org.springframework.security.core.userdetails.User.builder()
                .username("alice")
                .password("hashed")
                .disabled(true)
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_USER")))
                .build();
        when(jwtTokenProvider.validateToken("valid-token")).thenReturn(true);
        when(jwtTokenProvider.getUsername("valid-token")).thenReturn("alice");
        when(userDetailsService.loadDomainUser("alice")).thenReturn(domainUser);
        when(jwtTokenProvider.hasMatchingSession("valid-token", "session-1")).thenReturn(true);
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
        User domainUser = createDomainUser("alice", "session-1");
        UserDetails activeUserDetails = org.springframework.security.core.userdetails.User.builder()
                .username("alice")
                .password("hashed")
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_USER")))
                .build();
        when(jwtTokenProvider.validateToken("valid-token")).thenReturn(true);
        when(jwtTokenProvider.getUsername("valid-token")).thenReturn("alice");
        when(userDetailsService.loadDomainUser("alice")).thenReturn(domainUser);
        when(jwtTokenProvider.hasMatchingSession("valid-token", "session-1")).thenReturn(true);
        when(userDetailsService.loadUserByUsername("alice")).thenReturn(activeUserDetails);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getName()).isEqualTo("alice");
    }

    private User createDomainUser(String username, String sessionId) {
        User user = new User();
        user.setId(1L);
        user.setUsername(username);
        user.setEmail(username + "@example.com");
        user.setPasswordHash("hashed");
        user.setActiveSessionId(sessionId);
        user.setCreatedAt(LocalDateTime.now());
        return user;
    }

    // Helper to avoid unused import warning on Mockito.any()
    private static <T> T any() {
        return org.mockito.ArgumentMatchers.any();
    }
}
