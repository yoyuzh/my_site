package com.yoyuzh.boot.security;

import com.yoyuzh.identity.access.api.IdentityAuthenticatedUser;
import com.yoyuzh.identity.access.api.IdentityWebDavCredentialApi;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

@Component
public class WebDavBasicAuthenticationFilter extends OncePerRequestFilter {

    private static final String BASIC_PREFIX = "Basic ";
    private static final String AUTHENTICATE_HEADER = "Basic realm=\"yoyuzh-webdav\"";

    private final IdentityWebDavCredentialApi identityWebDavCredentialApi;

    public WebDavBasicAuthenticationFilter(IdentityWebDavCredentialApi identityWebDavCredentialApi) {
        this.identityWebDavCredentialApi = identityWebDavCredentialApi;
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
        Optional<BasicCredential> credential = parseBasicCredential(request.getHeader("Authorization"));
        if (credential.isEmpty()) {
            reject(response);
            return;
        }
        Optional<IdentityAuthenticatedUser> authenticatedUser = identityWebDavCredentialApi.authenticate(
                credential.get().username(),
                credential.get().password()
        );
        if (authenticatedUser.isEmpty()) {
            reject(response);
            return;
        }
        setAuthentication(request, authenticatedUser.get());
        filterChain.doFilter(request, response);
    }

    private boolean isWebDavRequest(HttpServletRequest request) {
        return WebDavRequestPathMatcher.isWebDavRequest(request.getRequestURI());
    }

    private Optional<BasicCredential> parseBasicCredential(String authorizationHeader) {
        if (!StringUtils.hasText(authorizationHeader) || !authorizationHeader.startsWith(BASIC_PREFIX)) {
            return Optional.empty();
        }
        try {
            String encodedCredentials = authorizationHeader.substring(BASIC_PREFIX.length()).trim();
            String decodedCredentials = new String(Base64.getDecoder().decode(encodedCredentials), StandardCharsets.UTF_8);
            int separator = decodedCredentials.indexOf(':');
            if (separator <= 0) {
                return Optional.empty();
            }
            String username = decodedCredentials.substring(0, separator);
            String password = decodedCredentials.substring(separator + 1);
            if (!StringUtils.hasText(username) || !StringUtils.hasText(password)) {
                return Optional.empty();
            }
            return Optional.of(new BasicCredential(username, password));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    private void setAuthentication(HttpServletRequest request, IdentityAuthenticatedUser authenticatedUser) {
        AuthenticatedUserPrincipal principal = new AuthenticatedUserPrincipal(
                authenticatedUser.id(),
                authenticatedUser.username(),
                "",
                authenticatedUser.storageQuotaBytes(),
                authenticatedUser.maxUploadSizeBytes(),
                List.of(new SimpleGrantedAuthority("ROLE_" + authenticatedUser.role().name())),
                !authenticatedUser.banned()
        );
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                principal,
                null,
                principal.getAuthorities()
        );
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private void reject(HttpServletResponse response) throws IOException {
        SecurityContextHolder.clearContext();
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setHeader("WWW-Authenticate", AUTHENTICATE_HEADER);
        response.flushBuffer();
    }

    private record BasicCredential(String username, String password) {
    }
}
