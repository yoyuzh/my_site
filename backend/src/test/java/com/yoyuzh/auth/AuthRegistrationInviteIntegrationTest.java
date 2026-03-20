package com.yoyuzh.auth;

import com.yoyuzh.PortalBackendApplication;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        classes = PortalBackendApplication.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:auth_invite_test;MODE=MySQL;DB_CLOSE_DELAY=-1",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "app.jwt.secret=0123456789abcdef0123456789abcdef",
                "app.registration.invite-code=invite-code",
                "app.storage.root-dir=./target/test-storage-auth-invite"
        }
)
@AutoConfigureMockMvc
class AuthRegistrationInviteIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
    }

    @Test
    void shouldRejectReusingInviteCodeAfterSuccessfulRegistration() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType("application/json")
                        .content("""
                                {
                                  "username": "alice",
                                  "email": "alice@example.com",
                                  "phoneNumber": "13800138000",
                                  "password": "StrongPass1!",
                                  "confirmPassword": "StrongPass1!",
                                  "inviteCode": "invite-code"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(post("/api/auth/register")
                        .contentType("application/json")
                        .content("""
                                {
                                  "username": "bob",
                                  "email": "bob@example.com",
                                  "phoneNumber": "13900139000",
                                  "password": "StrongPass1!",
                                  "confirmPassword": "StrongPass1!",
                                  "inviteCode": "invite-code"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.msg").value("邀请码错误"));
    }
}
