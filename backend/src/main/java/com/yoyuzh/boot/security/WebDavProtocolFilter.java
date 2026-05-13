package com.yoyuzh.boot.security;

import com.yoyuzh.files.webdav.api.WebDavProtocolGateway;
import com.yoyuzh.files.webdav.api.WebDavRequestPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class WebDavProtocolFilter extends OncePerRequestFilter {

    private final WebDavProtocolGateway gateway;

    public WebDavProtocolFilter(WebDavProtocolGateway gateway) {
        this.gateway = gateway;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!isWebDavRequest(request)) {
            filterChain.doFilter(request, response);
            return;
        }
        if (WebDavRequestPathMatcher.hasUnsafePath(request.getRequestURI())) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUserPrincipal principal)) {
            filterChain.doFilter(request, response);
            return;
        }
        gateway.dispatch(toWebDavPrincipal(principal), request, response);
    }

    private boolean isWebDavRequest(HttpServletRequest request) {
        return WebDavRequestPathMatcher.isWebDavRequest(request.getRequestURI());
    }

    private WebDavRequestPrincipal toWebDavPrincipal(AuthenticatedUserPrincipal principal) {
        return new WebDavRequestPrincipal(
                principal.getUserId(),
                principal.getUsername(),
                principal.getStorageQuotaBytes(),
                principal.getMaxUploadSizeBytes()
        );
    }
}
