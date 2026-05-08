package com.yoyuzh.boot.security;

import com.yoyuzh.identity.access.api.IdentityClientType;
import com.yoyuzh.identity.access.api.IdentityAuthenticatedUser;
import com.yoyuzh.ops.admin.api.AdminRequestMetricsApi;
import com.yoyuzh.shared.kernel.BusinessException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final AuthTokenInvalidationService authTokenInvalidationService;
    private final CustomUserDetailsService userDetailsService;
    private final AdminRequestMetricsApi adminRequestMetricsApi;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        String requestPath = request.getRequestURI();
        boolean shouldLogAuthProbe = shouldLogAuthProbe(requestPath);
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            ParsedToken parsedToken = jwtTokenProvider.parseToken(token);
            if (parsedToken == null) {
                logAuthProbe("token-parse-failed", requestPath, null, null, null);
            }
            if (parsedToken != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                if (authTokenInvalidationService.isAccessTokenRevoked(
                        parsedToken.userId(),
                        parsedToken.clientType(),
                        parsedToken.issuedAt())) {
                    logAuthProbe("token-revoked", requestPath, parsedToken.username(), parsedToken.userId(), parsedToken.clientType());
                    filterChain.doFilter(request, response);
                    return;
                }
                IdentityAuthenticatedUser authenticatedUser;
                try {
                    authenticatedUser = userDetailsService.loadAuthenticatedUser(parsedToken.username());
                } catch (BusinessException ex) {
                    logAuthProbe("user-not-found", requestPath, parsedToken.username(), parsedToken.userId(), parsedToken.clientType());
                    filterChain.doFilter(request, response);
                    return;
                }
                if (!jwtTokenProvider.hasMatchingSession(parsedToken, authenticatedUser)) {
                    logAuthProbe("session-mismatch", requestPath, parsedToken.username(), parsedToken.userId(), parsedToken.clientType());
                    filterChain.doFilter(request, response);
                    return;
                }
                if (authenticatedUser.banned()) {
                    logAuthProbe("user-banned", requestPath, parsedToken.username(), parsedToken.userId(), parsedToken.clientType());
                    filterChain.doFilter(request, response);
                    return;
                }
                UserDetails userDetails = userDetailsService.toUserDetails(authenticatedUser);
                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        userDetails, null, userDetails.getAuthorities());
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
                adminRequestMetricsApi.recordUserOnline(authenticatedUser.id(), authenticatedUser.username());
            }
        } else if (shouldLogAuthProbe) {
            log.info("auth-probe reason=missing-authorization path={}", sanitizeForLog(requestPath));
        }
        filterChain.doFilter(request, response);
    }

    private boolean shouldLogAuthProbe(String requestPath) {
        if (!StringUtils.hasText(requestPath)) {
            return false;
        }
        return requestPath.startsWith("/api/v2/files/upload-sessions")
                || requestPath.startsWith("/api/auth/refresh");
    }

    private void logAuthProbe(String reason,
                              String requestPath,
                              String username,
                              Long userId,
                              IdentityClientType clientType) {
        if (!shouldLogAuthProbe(requestPath)) {
            return;
        }
        log.info(
                "auth-probe reason={} path={} username={} userId={} clientType={}",
                sanitizeForLog(reason),
                sanitizeForLog(requestPath),
                sanitizeForLog(username),
                userId == null ? "-" : userId,
                clientType == null ? "-" : clientType.name()
        );
    }

    private String sanitizeForLog(String value) {
        if (!StringUtils.hasText(value)) {
            return "-";
        }
        return value.replace('\n', '_').replace('\r', '_');
    }
}
