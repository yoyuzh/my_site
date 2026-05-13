package com.yoyuzh.boot.security;

import com.yoyuzh.files.webdav.api.WebDavProtocolGateway;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class WebDavProtocolFilterTest {

    @Test
    void shouldDispatchAuthenticatedWebDavRequestWithoutContinuingFilterChain() throws Exception {
        WebDavProtocolGateway gateway = mock(WebDavProtocolGateway.class);
        WebDavProtocolFilter filter = new WebDavProtocolFilter(gateway);
        MockHttpServletRequest request = new MockHttpServletRequest("PROPFIND", "/dav/Docs");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                userPrincipal(),
                null,
                userPrincipal().getAuthorities()
        ));

        filter.doFilter(request, response, chain);

        verify(gateway).dispatch(any(), any(), any());
        verify(chain, never()).doFilter(any(), any());
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldDispatchApiDavCompatibilityRequest() throws Exception {
        WebDavProtocolGateway gateway = mock(WebDavProtocolGateway.class);
        WebDavProtocolFilter filter = new WebDavProtocolFilter(gateway);
        MockHttpServletRequest request = new MockHttpServletRequest("PROPFIND", "/api/dav");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                userPrincipal(),
                null,
                userPrincipal().getAuthorities()
        ));

        filter.doFilter(request, response, chain);

        verify(gateway).dispatch(any(), any(), any());
        verify(chain, never()).doFilter(any(), any());
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldDispatchMicrosoftMiniRedirectorParentDiscoveryRequest() throws Exception {
        WebDavProtocolGateway gateway = mock(WebDavProtocolGateway.class);
        WebDavProtocolFilter filter = new WebDavProtocolFilter(gateway);
        MockHttpServletRequest request = new MockHttpServletRequest("PROPFIND", "/api");
        request.addHeader("User-Agent", "Microsoft-WebDAV-MiniRedir/10.0.26120");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                userPrincipal(),
                null,
                userPrincipal().getAuthorities()
        ));

        filter.doFilter(request, response, chain);

        verify(gateway).dispatch(any(), any(), any());
        verify(chain, never()).doFilter(any(), any());
        SecurityContextHolder.clearContext();
    }

    private AuthenticatedUserPrincipal userPrincipal() {
        return new AuthenticatedUserPrincipal(
                7L,
                "alice",
                "encoded",
                1024L,
                2048L,
                List.of(() -> "ROLE_USER"),
                true
        );
    }
}
