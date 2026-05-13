package com.yoyuzh.boot.security;

import com.yoyuzh.identity.access.api.IdentityAuthenticatedUser;
import com.yoyuzh.identity.access.api.IdentityRoleName;
import com.yoyuzh.identity.access.api.IdentityWebDavCredentialApi;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WebDavBasicAuthenticationFilterTest {

    @Mock
    private IdentityWebDavCredentialApi identityWebDavCredentialApi;

    @Mock
    private FilterChain filterChain;

    private WebDavBasicAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        filter = new WebDavBasicAuthenticationFilter(identityWebDavCredentialApi);
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldIgnoreNonDavRequests() throws Exception {
        MockHttpServletRequest request = request("/api/files/list");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(identityWebDavCredentialApi);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void shouldRejectDavRequestWithoutBasicHeader() throws Exception {
        MockHttpServletRequest request = request("/dav");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getHeader("WWW-Authenticate")).contains("Basic");
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verifyNoInteractions(identityWebDavCredentialApi);
    }

    @Test
    void shouldRejectDavPathTraversalBeforeAuthentication() throws Exception {
        MockHttpServletRequest request = request("/dav/../api/files/list");
        request.addHeader("Authorization", basic("alice", "webdav-password"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verifyNoInteractions(identityWebDavCredentialApi);
    }

    @Test
    void shouldRejectDavRequestWithInvalidCredential() throws Exception {
        MockHttpServletRequest request = request("/dav/Docs");
        request.addHeader("Authorization", basic("alice", "wrong-password"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(identityWebDavCredentialApi.authenticate("alice", "wrong-password")).thenReturn(Optional.empty());

        filter.doFilterInternal(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getHeader("WWW-Authenticate")).contains("Basic");
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void shouldAuthenticateDavRequestWithValidCredential() throws Exception {
        MockHttpServletRequest request = request("/dav/Docs");
        request.addHeader("Authorization", basic("alice", "webdav-password"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(identityWebDavCredentialApi.authenticate("alice", "webdav-password"))
                .thenReturn(Optional.of(authenticatedUser()));

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getName()).isEqualTo("alice");
        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal())
                .isInstanceOfSatisfying(AuthenticatedUserPrincipal.class, principal ->
                        assertThat(principal.getPassword()).isEmpty());
    }

    private MockHttpServletRequest request(String requestUri) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI(requestUri);
        return request;
    }

    private String basic(String username, String password) {
        String credentials = username + ":" + password;
        return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }

    private IdentityAuthenticatedUser authenticatedUser() {
        return new IdentityAuthenticatedUser(
                1L,
                "alice",
                "password-hash",
                IdentityRoleName.USER,
                false,
                "session",
                "desktop-session",
                "mobile-session",
                1024L,
                512L
        );
    }
}
