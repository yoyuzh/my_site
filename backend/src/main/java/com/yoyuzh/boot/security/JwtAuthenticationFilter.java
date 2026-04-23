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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@RequiredArgsConstructor
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
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            if (jwtTokenProvider.validateToken(token)
                    && SecurityContextHolder.getContext().getAuthentication() == null) {
                Long userId = jwtTokenProvider.getUserId(token);
                IdentityClientType clientType = jwtTokenProvider.getClientType(token);
                if (authTokenInvalidationService.isAccessTokenRevoked(
                        userId,
                        clientType,
                        jwtTokenProvider.getIssuedAt(token))) {
                    filterChain.doFilter(request, response);
                    return;
                }
                String username = jwtTokenProvider.getUsername(token);
                IdentityAuthenticatedUser authenticatedUser;
                try {
                    authenticatedUser = userDetailsService.loadAuthenticatedUser(username);
                } catch (BusinessException ex) {
                    filterChain.doFilter(request, response);
                    return;
                }
                if (!jwtTokenProvider.hasMatchingSession(token, authenticatedUser)) {
                    filterChain.doFilter(request, response);
                    return;
                }
                UserDetails userDetails = userDetailsService.loadUserByUsername(username);
                if (!userDetails.isEnabled()) {
                    filterChain.doFilter(request, response);
                    return;
                }
                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        userDetails, null, userDetails.getAuthorities());
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
                adminRequestMetricsApi.recordUserOnline(authenticatedUser.id(), authenticatedUser.username());
            }
        }
        filterChain.doFilter(request, response);
    }
}
