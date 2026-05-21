package com.yoyuzh.boot.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yoyuzh.identity.access.api.AdminAccessPolicy;
import com.yoyuzh.shared.kernel.ApiResponse;
import com.yoyuzh.shared.kernel.ErrorCode;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.firewall.HttpFirewall;
import org.springframework.security.web.firewall.StrictHttpFirewall;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.util.StringUtils;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
@Slf4j
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final ApiRequestMetricsFilter apiRequestMetricsFilter;
    private final CustomUserDetailsService userDetailsService;
    private final ObjectMapper objectMapper;
    private final CorsProperties corsProperties;
    private final AdminAccessPolicy adminAccessPolicy;
    private final WebDavBasicAuthenticationFilter webDavBasicAuthenticationFilter;
    private final OncePerRequestFilter webDavProtocolFilter;
    private static final List<String> ALLOWED_HTTP_METHODS = List.of(
            "GET",
            "POST",
            "PUT",
            "PATCH",
            "DELETE",
            "HEAD",
            "OPTIONS",
            "PROPFIND",
            "PROPPATCH",
            "MKCOL",
            "COPY",
            "MOVE",
            "LOCK",
            "UNLOCK"
    );

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, PasswordEncoder passwordEncoder) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/", "/error", "/h2-console/**")
                        .permitAll()
                        .requestMatchers("/api/auth/**", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
                        .permitAll()
                        .requestMatchers("/api/app/android/latest", "/api/app/android/download", "/api/app/android/download/*")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v2/site/ping", "/api/v2/site/config")
                        .permitAll()
                        .requestMatchers("/dav", "/dav/**")
                        .authenticated()
                        .requestMatchers("/api/v2/tasks/**")
                        .authenticated()
                        .requestMatchers("/api/v2/files/**")
                        .authenticated()
                        .requestMatchers("/api/v2/shares/shared-with-me", "/api/v2/shares/shared-with-me/*")
                        .authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v2/shares/mine")
                        .authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/api/v2/shares/*")
                        .authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v2/shares")
                        .authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v2/shares/*")
                        .permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v2/shares/*/verify-password")
                        .permitAll()
                        .requestMatchers("/api/v2/shares/**")
                        .authenticated()
                        .requestMatchers("/api/transfer/remote-downloads/**")
                        .authenticated()
                        .requestMatchers("/api/transfer/**")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/files/share-links/*")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/files/viewer/*")
                        .permitAll()
                        .requestMatchers("/api/admin/**")
                        .access((authentication, context) -> new org.springframework.security.authorization.AuthorizationDecision(
                                adminAccessPolicy.hasAdminAccess(authentication.get())
                        ))
                        .requestMatchers("/api/files/**", "/api/user/**")
                        .authenticated()
                        .anyRequest()
                        .denyAll())
                .authenticationProvider(authenticationProvider(passwordEncoder))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, e) -> {
                            logAuthProbe("unauthorized-entrypoint", request.getRequestURI(), request.getHeader("Authorization"));
                            response.setStatus(401);
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            response.setCharacterEncoding("UTF-8");
                            objectMapper.writeValue(response.getWriter(),
                                    ApiResponse.error(ErrorCode.NOT_LOGGED_IN, "用户未登录"));
                        })
                        .accessDeniedHandler((request, response, e) -> {
                            logAuthProbe("forbidden-entrypoint", request.getRequestURI(), request.getHeader("Authorization"));
                            response.setStatus(403);
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            response.setCharacterEncoding("UTF-8");
                            objectMapper.writeValue(response.getWriter(),
                                    ApiResponse.error(ErrorCode.PERMISSION_DENIED, "没有权限访问该资源"));
                        }))
                .addFilterBefore(apiRequestMetricsFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(webDavBasicAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(webDavProtocolFilter, WebDavBasicAuthenticationFilter.class)
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public AuthenticationProvider authenticationProvider(PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    public HttpFirewall httpFirewall() {
        StrictHttpFirewall firewall = new StrictHttpFirewall();
        firewall.setAllowedHttpMethods(ALLOWED_HTTP_METHODS);
        return firewall;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(corsProperties.getAllowedOrigins());
        configuration.setAllowedMethods(ALLOWED_HTTP_METHODS);
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setExposedHeaders(List.of(
                "Upload-Offset",
                "Upload-Length",
                "Location",
                "Tus-Resumable",
                "Tus-Version",
                "Tus-Extension"
        ));
        configuration.setAllowCredentials(false);
        configuration.setMaxAge(86400L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    private void logAuthProbe(String reason, String requestPath, String authorizationHeader) {
        if (!StringUtils.hasText(requestPath)) {
            return;
        }
        if (!requestPath.startsWith("/api/v2/files/upload-sessions")
                && !requestPath.startsWith("/api/auth/refresh")) {
            return;
        }
        log.info(
                "auth-probe reason={} path={} authHeaderPresent={}",
                sanitizeForLog(reason),
                sanitizeForLog(requestPath),
                StringUtils.hasText(authorizationHeader)
        );
    }

    private String sanitizeForLog(String value) {
        if (!StringUtils.hasText(value)) {
            return "-";
        }
        return value.replace('\n', '_').replace('\r', '_');
    }
}
