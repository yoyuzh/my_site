package com.yoyuzh.boot.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yoyuzh.ops.admin.internal.web.AdminAuditController;
import com.yoyuzh.ops.admin.internal.web.AdminOverviewController;
import com.yoyuzh.ops.admin.internal.web.AdminResourceController;
import com.yoyuzh.ops.admin.internal.web.AdminSettingsController;
import com.yoyuzh.ops.admin.internal.web.AdminStoragePolicyController;
import com.yoyuzh.ops.admin.internal.web.AdminTaskController;
import com.yoyuzh.ops.admin.internal.web.AdminUserController;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.web.firewall.StrictHttpFirewall;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

class SecurityConfigTest {

    @Test
    void corsPropertiesShouldAllowProductionSiteOriginsByDefault() {
        CorsProperties corsProperties = new CorsProperties();

        assertThat(corsProperties.getAllowedOrigins())
                .contains(
                        "http://localhost",
                        "https://localhost",
                        "http://127.0.0.1",
                        "https://127.0.0.1",
                        "capacitor://localhost",
                        "https://yoyuzh.xyz",
                        "https://www.yoyuzh.xyz"
                );
    }

    @Test
    void corsConfigurationShouldAllowPatchRequests() {
        CorsProperties corsProperties = new CorsProperties();
        corsProperties.setAllowedOrigins(java.util.List.of("https://yoyuzh.xyz"));

        SecurityConfig securityConfig = new SecurityConfig(
                null,
                null,
                null,
                new ObjectMapper(),
                corsProperties,
                authentication -> false,
                null,
                null
        );

        CorsConfigurationSource source = securityConfig.corsConfigurationSource();
        CorsConfiguration configuration = source.getCorsConfiguration(
                new org.springframework.mock.web.MockHttpServletRequest("OPTIONS", "/api/files/1/rename"));

        assertThat(configuration).isNotNull();
        assertThat(configuration.getAllowedMethods()).contains("PATCH");
        assertThat(configuration.getAllowedMethods()).contains("HEAD");
        assertThat(configuration.getAllowedMethods()).contains("PROPFIND", "MKCOL", "COPY", "MOVE", "LOCK", "UNLOCK");
        assertThat(configuration.getExposedHeaders())
                .contains("Upload-Offset", "Upload-Length", "Location", "Tus-Resumable");
    }

    @Test
    void httpFirewallShouldAllowWebDavMethods() {
        SecurityConfig securityConfig = new SecurityConfig(
                null,
                null,
                null,
                new ObjectMapper(),
                new CorsProperties(),
                authentication -> false,
                null,
                null
        );

        assertThat(securityConfig.httpFirewall()).isInstanceOf(StrictHttpFirewall.class);
        assertThatCode(() -> securityConfig.httpFirewall().getFirewalledRequest(
                new org.springframework.mock.web.MockHttpServletRequest("PROPFIND", "/dav")))
                .doesNotThrowAnyException();
        assertThatCode(() -> securityConfig.httpFirewall().getFirewalledRequest(
                new org.springframework.mock.web.MockHttpServletRequest("MKCOL", "/dav/Docs")))
                .doesNotThrowAnyException();
    }

    @Test
    void adminControllersShouldRelyOnUrlSecurityForAdminRouteFamily() {
        assertThat(AdminUserController.class.isAnnotationPresent(PreAuthorize.class)).isFalse();
        assertThat(AdminResourceController.class.isAnnotationPresent(PreAuthorize.class)).isFalse();
        assertThat(AdminAuditController.class.isAnnotationPresent(PreAuthorize.class)).isFalse();
        assertThat(AdminSettingsController.class.isAnnotationPresent(PreAuthorize.class)).isFalse();
        assertThat(AdminTaskController.class.isAnnotationPresent(PreAuthorize.class)).isFalse();
        assertThat(AdminStoragePolicyController.class.isAnnotationPresent(PreAuthorize.class)).isFalse();
        assertThat(AdminOverviewController.class.isAnnotationPresent(PreAuthorize.class)).isFalse();
    }
}
