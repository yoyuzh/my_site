package com.yoyuzh.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yoyuzh.auth.dto.AuthResponse;
import com.yoyuzh.auth.dto.UserProfileResponse;
import com.yoyuzh.common.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AuthControllerValidationTest {

    @Mock
    private AuthService authService;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AuthController(authService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void shouldReturnReadablePasswordValidationMessage() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "alice",
                                  "email": "alice@example.com",
                                  "phoneNumber": "13800138000",
                                  "password": "weakpass",
                                  "confirmPassword": "weakpass",
                                  "inviteCode": "invite-code"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1000))
                .andExpect(jsonPath("$.msg").value("密码至少10位，且必须包含大写字母、小写字母、数字和特殊字符"));
    }

    @Test
    void shouldReturnReadablePhoneValidationMessage() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "alice",
                                  "email": "alice@example.com",
                                  "phoneNumber": "12345",
                                  "password": "StrongPass1!",
                                  "confirmPassword": "StrongPass1!",
                                  "inviteCode": "invite-code"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1000))
                .andExpect(jsonPath("$.msg").value("请输入有效的11位手机号"));
    }

    @Test
    void shouldReturnReadablePasswordConfirmationMessage() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "alice",
                                  "email": "alice@example.com",
                                  "phoneNumber": "13800138000",
                                  "password": "StrongPass1!",
                                  "confirmPassword": "StrongPass2!",
                                  "inviteCode": "invite-code"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1000))
                .andExpect(jsonPath("$.msg").value("两次输入的密码不一致"));
    }

    @Test
    void shouldReturnReadableInviteCodeMessage() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "alice",
                                  "email": "alice@example.com",
                                  "phoneNumber": "13800138000",
                                  "password": "StrongPass1!",
                                  "confirmPassword": "StrongPass1!",
                                  "inviteCode": ""
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1000))
                .andExpect(jsonPath("$.msg").value("请输入邀请码"));
    }

    @Test
    void shouldExposeRefreshEndpointContract() throws Exception {
        AuthResponse response = AuthResponse.issued(
                "new-access-token",
                "new-refresh-token",
                new UserProfileResponse(7L, "alice", "alice@example.com", LocalDateTime.now())
        );
        when(authService.refresh("refresh-1")).thenReturn(response);

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new Object() {
                            public final String refreshToken = "refresh-1";
                        })))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.token").value("new-access-token"))
                .andExpect(jsonPath("$.data.accessToken").value("new-access-token"))
                .andExpect(jsonPath("$.data.refreshToken").value("new-refresh-token"))
                .andExpect(jsonPath("$.data.user.username").value("alice"));

        verify(authService).refresh("refresh-1");
    }
}
