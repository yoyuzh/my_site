package com.yoyuzh.identity.access.internal.web;

import com.jayway.jsonpath.JsonPath;
import com.yoyuzh.PortalBackendApplication;
import com.yoyuzh.identity.access.internal.domain.User;
import com.yoyuzh.identity.access.internal.domain.UserRole;
import com.yoyuzh.identity.access.internal.infra.RefreshTokenRepository;
import com.yoyuzh.identity.access.internal.infra.UserRepository;
import com.yoyuzh.support.PostgresContainerSupport;
import com.yoyuzh.support.RequiresDocker;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        classes = PortalBackendApplication.class,
        properties = {
                "app.jwt.secret=0123456789abcdef0123456789abcdef",
                "app.storage.root-dir=./target/test-storage-auth-single-device-postgres"
        }
)
@AutoConfigureMockMvc
@RequiresDocker
@Testcontainers(disabledWithoutDocker = true)
class AuthSingleDevicePostgresIntegrationTest extends PostgresContainerSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void shouldAuthenticateAgainstPostgresBackedRuntime() throws Exception {
        User user = new User();
        user.setUsername("postgres-alice");
        user.setDisplayName("Postgres Alice");
        user.setEmail("postgres-alice@example.com");
        user.setPhoneNumber("13800138001");
        user.setPasswordHash(passwordEncoder.encode("StrongPass1!"));
        user.setPreferredLanguage("zh-CN");
        user.setRole(UserRole.USER);
        user.setCreatedAt(LocalDateTime.now());
        userRepository.save(user);

        String loginRequest = """
                {
                  "username": "postgres-alice",
                  "password": "StrongPass1!"
                }
                """;

        String loginResponse = mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .header("X-Yoyuzh-Client", "desktop")
                        .content(loginRequest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String accessToken = JsonPath.read(loginResponse, "$.data.accessToken");

        mockMvc.perform(get("/api/user/profile")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("postgres-alice"));
    }
}
