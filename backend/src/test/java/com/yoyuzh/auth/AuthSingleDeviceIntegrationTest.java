package com.yoyuzh.auth;

import com.jayway.jsonpath.JsonPath;
import com.yoyuzh.PortalBackendApplication;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        classes = PortalBackendApplication.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:auth_single_device_test;MODE=MySQL;DB_CLOSE_DELAY=-1",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "app.jwt.secret=0123456789abcdef0123456789abcdef",
                "app.storage.root-dir=./target/test-storage-auth-single-device"
        }
)
@AutoConfigureMockMvc
class AuthSingleDeviceIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
    }

    @Test
    void shouldInvalidatePreviousAccessTokenAfterLoggingInAgain() throws Exception {
        User user = new User();
        user.setUsername("alice");
        user.setDisplayName("Alice");
        user.setEmail("alice@example.com");
        user.setPhoneNumber("13800138000");
        user.setPasswordHash(passwordEncoder.encode("StrongPass1!"));
        user.setPreferredLanguage("zh-CN");
        user.setRole(UserRole.USER);
        user.setCreatedAt(LocalDateTime.now());
        userRepository.save(user);

        String loginRequest = """
                {
                  "username": "alice",
                  "password": "StrongPass1!"
                }
                """;

        String firstLoginResponse = mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content(loginRequest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String secondLoginResponse = mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content(loginRequest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String firstAccessToken = JsonPath.read(firstLoginResponse, "$.data.accessToken");
        String secondAccessToken = JsonPath.read(secondLoginResponse, "$.data.accessToken");

        mockMvc.perform(get("/api/user/profile")
                        .header("Authorization", "Bearer " + firstAccessToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(1001));

        mockMvc.perform(get("/api/user/profile")
                        .header("Authorization", "Bearer " + secondAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("alice"));
    }
}
