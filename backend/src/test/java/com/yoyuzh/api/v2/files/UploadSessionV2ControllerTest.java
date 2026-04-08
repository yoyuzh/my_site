package com.yoyuzh.api.v2.files;

import com.yoyuzh.auth.CustomUserDetailsService;
import com.yoyuzh.auth.User;
import com.yoyuzh.files.UploadSession;
import com.yoyuzh.files.UploadSessionService;
import com.yoyuzh.files.UploadSessionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UploadSessionV2ControllerTest {

    private UploadSessionService uploadSessionService;
    private CustomUserDetailsService userDetailsService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        uploadSessionService = mock(UploadSessionService.class);
        userDetailsService = mock(CustomUserDetailsService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(
                new UploadSessionV2Controller(uploadSessionService, userDetailsService)
        ).setCustomArgumentResolvers(authenticationPrincipalResolver()).build();
    }

    @Test
    void shouldCreateUploadSessionWithV2Envelope() throws Exception {
        User user = createUser(7L);
        UploadSession session = createSession(user);
        when(userDetailsService.loadDomainUser("alice")).thenReturn(user);
        when(uploadSessionService.createSession(eq(user), any())).thenReturn(session);

        mockMvc.perform(post("/api/v2/files/upload-sessions")
                        .with(user(userDetails()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "path": "/docs",
                                  "filename": "movie.mp4",
                                  "contentType": "video/mp4",
                                  "size": 20971520
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.sessionId").value("session-1"))
                .andExpect(jsonPath("$.data.objectKey").value("blobs/session-1"))
                .andExpect(jsonPath("$.data.status").value("CREATED"))
                .andExpect(jsonPath("$.data.chunkSize").value(8388608))
                .andExpect(jsonPath("$.data.chunkCount").value(3));
    }

    @Test
    void shouldReturnOwnedUploadSessionWithV2Envelope() throws Exception {
        User user = createUser(7L);
        UploadSession session = createSession(user);
        when(userDetailsService.loadDomainUser("alice")).thenReturn(user);
        when(uploadSessionService.getOwnedSession(user, "session-1")).thenReturn(session);

        mockMvc.perform(get("/api/v2/files/upload-sessions/session-1")
                        .with(user(userDetails())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.sessionId").value("session-1"))
                .andExpect(jsonPath("$.data.status").value("CREATED"));
    }

    private UserDetails userDetails() {
        return org.springframework.security.core.userdetails.User
                .withUsername("alice")
                .password("encoded")
                .authorities("ROLE_USER")
                .build();
    }

    private HandlerMethodArgumentResolver authenticationPrincipalResolver() {
        UserDetails userDetails = userDetails();
        return new HandlerMethodArgumentResolver() {
            @Override
            public boolean supportsParameter(MethodParameter parameter) {
                return parameter.hasParameterAnnotation(AuthenticationPrincipal.class)
                        && UserDetails.class.isAssignableFrom(parameter.getParameterType());
            }

            @Override
            public Object resolveArgument(MethodParameter parameter,
                                          ModelAndViewContainer mavContainer,
                                          NativeWebRequest webRequest,
                                          WebDataBinderFactory binderFactory) {
                return userDetails;
            }
        };
    }

    private User createUser(Long id) {
        User user = new User();
        user.setId(id);
        user.setUsername("alice");
        user.setEmail("alice@example.com");
        return user;
    }

    private UploadSession createSession(User user) {
        UploadSession session = new UploadSession();
        session.setId(100L);
        session.setSessionId("session-1");
        session.setUser(user);
        session.setTargetPath("/docs");
        session.setFilename("movie.mp4");
        session.setContentType("video/mp4");
        session.setSize(20L * 1024 * 1024);
        session.setObjectKey("blobs/session-1");
        session.setChunkSize(8L * 1024 * 1024);
        session.setChunkCount(3);
        session.setStatus(UploadSessionStatus.CREATED);
        session.setExpiresAt(LocalDateTime.of(2026, 4, 9, 6, 0));
        session.setCreatedAt(LocalDateTime.of(2026, 4, 8, 6, 0));
        session.setUpdatedAt(LocalDateTime.of(2026, 4, 8, 6, 0));
        return session;
    }
}
