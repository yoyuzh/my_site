package com.yoyuzh.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityConfigTest {

    @Test
    void corsPropertiesShouldAllowProductionSiteOriginsByDefault() {
        CorsProperties corsProperties = new CorsProperties();

        assertThat(corsProperties.getAllowedOrigins())
                .contains("https://yoyuzh.xyz", "https://www.yoyuzh.xyz");
    }

    @Test
    void corsConfigurationShouldAllowPatchRequests() {
        CorsProperties corsProperties = new CorsProperties();
        corsProperties.setAllowedOrigins(java.util.List.of("https://yoyuzh.xyz"));

        SecurityConfig securityConfig = new SecurityConfig(
                null,
                null,
                new ObjectMapper(),
                corsProperties
        );

        CorsConfigurationSource source = securityConfig.corsConfigurationSource();
        CorsConfiguration configuration = source.getCorsConfiguration(
                new org.springframework.mock.web.MockHttpServletRequest("OPTIONS", "/api/files/1/rename"));

        assertThat(configuration).isNotNull();
        assertThat(configuration.getAllowedMethods()).contains("PATCH");
    }

}
