package com.yoyuzh.files.upload.internal.web;

import com.yoyuzh.files.upload.internal.application.UploadSessionTusIngressService;
import com.yoyuzh.files.upload.internal.application.UploadSessionService;
import com.yoyuzh.files.upload.internal.application.UploadSessionTusService;
import com.yoyuzh.files.upload.internal.application.UploadSessionTusState;
import com.yoyuzh.identity.access.api.IdentityAuthenticatedUser;
import com.yoyuzh.identity.access.api.IdentityAuthenticationApi;
import com.yoyuzh.identity.access.api.IdentityRoleName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UploadSessionTusControllerTest {

    private UploadSessionService uploadSessionService;
    private UploadSessionTusIngressService uploadSessionTusIngressService;
    private UploadSessionTusService uploadSessionTusService;
    private IdentityAuthenticationApi identityAuthenticationApi;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        uploadSessionService = mock(UploadSessionService.class);
        uploadSessionTusIngressService = mock(UploadSessionTusIngressService.class);
        uploadSessionTusService = mock(UploadSessionTusService.class);
        identityAuthenticationApi = mock(IdentityAuthenticationApi.class);
        when(identityAuthenticationApi.findByUsername("alice")).thenReturn(Optional.of(authenticatedUser()));
        when(uploadSessionTusService.tusResumableVersion()).thenReturn("1.0.0");
        mockMvc = MockMvcBuilders.standaloneSetup(
                new UploadSessionTusController(
                        uploadSessionService,
                        uploadSessionTusIngressService,
                        uploadSessionTusService,
                        identityAuthenticationApi
                )
        ).setCustomArgumentResolvers(authenticationPrincipalResolver()).build();
    }

    @Test
    void shouldExposeTusCapabilitiesOnOptions() throws Exception {
        mockMvc.perform(options("/api/v2/files/upload-sessions/session-1/tus")
                        .with(user(userDetails())))
                .andExpect(status().isNoContent())
                .andExpect(header().string("Tus-Resumable", "1.0.0"))
                .andExpect(header().string("Tus-Version", "1.0.0"))
                .andExpect(header().string("Tus-Extension", "creation,termination"));
    }

    @Test
    void shouldCreateAndReportTusUploadOffset() throws Exception {
        when(uploadSessionService.startTusSession(7L, "session-1", 20L))
                .thenReturn(new UploadSessionTusState(0L, 20L));
        when(uploadSessionService.getTusSessionState(7L, "session-1"))
                .thenReturn(new UploadSessionTusState(12L, 20L));

        mockMvc.perform(post("/api/v2/files/upload-sessions/session-1/tus")
                        .with(user(userDetails()))
                        .header("Upload-Length", 20))
                .andExpect(status().isCreated())
                .andExpect(header().string("Tus-Resumable", "1.0.0"))
                .andExpect(header().string("Location", "/api/v2/files/upload-sessions/session-1/tus"))
                .andExpect(header().string("Upload-Offset", "0"))
                .andExpect(header().string("Upload-Length", "20"));

        mockMvc.perform(head("/api/v2/files/upload-sessions/session-1/tus")
                        .with(user(userDetails())))
                .andExpect(status().isNoContent())
                .andExpect(header().string("Tus-Resumable", "1.0.0"))
                .andExpect(header().string("Upload-Offset", "12"))
                .andExpect(header().string("Upload-Length", "20"));
    }

    @Test
    void shouldRequireUploadLengthWhenCreatingTusSession() throws Exception {
        mockMvc.perform(post("/api/v2/files/upload-sessions/session-1/tus")
                        .with(user(userDetails())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldAppendAndDeleteTusUpload() throws Exception {
        when(uploadSessionTusIngressService.appendSession(eq(7L), eq("session-1"), eq(0L), any(), eq(7L)))
                .thenReturn(new UploadSessionTusState(7L, 20L));

        mockMvc.perform(patch("/api/v2/files/upload-sessions/session-1/tus")
                        .with(user(userDetails()))
                        .contentType("application/offset+octet-stream")
                        .header("Upload-Offset", 0)
                        .content("payload"))
                .andExpect(status().isNoContent())
                .andExpect(header().string("Tus-Resumable", "1.0.0"))
                .andExpect(header().string("Upload-Offset", "7"));

        mockMvc.perform(delete("/api/v2/files/upload-sessions/session-1/tus")
                        .with(user(userDetails())))
                .andExpect(status().isNoContent())
                .andExpect(header().string("Tus-Resumable", "1.0.0"));

        verify(uploadSessionService).cancelTusSession(7L, "session-1");
    }

    @Test
    void shouldReturnTusStatusViaHeadersOnly() throws Exception {
        when(uploadSessionService.getTusSessionState(7L, "session-1"))
                .thenReturn(new UploadSessionTusState(12L, 20L));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v2/files/upload-sessions/session-1/tus/status")
                        .with(user(userDetails())))
                .andExpect(status().isNoContent())
                .andExpect(header().string("Tus-Resumable", "1.0.0"))
                .andExpect(header().string("Upload-Offset", "12"))
                .andExpect(header().string("Upload-Length", "20"));
    }

    private UserDetails userDetails() {
        return org.springframework.security.core.userdetails.User
                .withUsername("alice")
                .password("encoded")
                .authorities("ROLE_USER")
                .build();
    }

    private IdentityAuthenticatedUser authenticatedUser() {
        return new IdentityAuthenticatedUser(
                7L,
                "alice",
                "encoded",
                IdentityRoleName.USER,
                false,
                "session",
                "desktop-session",
                "mobile-session",
                1024L,
                2048L
        );
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
}
