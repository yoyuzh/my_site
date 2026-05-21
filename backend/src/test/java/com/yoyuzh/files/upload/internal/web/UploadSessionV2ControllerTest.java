package com.yoyuzh.files.upload.internal.web;

import com.yoyuzh.identity.access.api.IdentityAuthenticatedUser;
import com.yoyuzh.identity.access.api.IdentityAuthenticationApi;
import com.yoyuzh.identity.access.api.IdentityRoleName;
import com.yoyuzh.files.upload.api.UploadSessionUploadMode;
import com.yoyuzh.files.upload.internal.application.UploadSessionCreateCommand;
import com.yoyuzh.files.upload.internal.application.UploadSessionRuntimeState;
import com.yoyuzh.files.upload.internal.application.UploadSessionService;
import com.yoyuzh.files.upload.internal.application.UploadSessionTusService;
import com.yoyuzh.files.upload.internal.application.UploadSessionView;
import com.yoyuzh.files.upload.internal.domain.UploadSessionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UploadSessionV2ControllerTest {

    private UploadSessionService uploadSessionService;
    private UploadSessionTusService uploadSessionTusService;
    private IdentityAuthenticationApi identityAuthenticationApi;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        uploadSessionService = mock(UploadSessionService.class);
        uploadSessionTusService = mock(UploadSessionTusService.class);
        identityAuthenticationApi = mock(IdentityAuthenticationApi.class);
        when(identityAuthenticationApi.findByUsername("alice")).thenReturn(Optional.of(createAuthenticatedUser(7L)));
        when(uploadSessionTusService.tusResumableVersion()).thenReturn("1.0.0");
        mockMvc = MockMvcBuilders.standaloneSetup(
                new UploadSessionV2Controller(uploadSessionService, uploadSessionTusService, identityAuthenticationApi)
        ).setCustomArgumentResolvers(authenticationPrincipalResolver()).build();
    }

    @Test
    void shouldCreateUploadSessionWithV2Envelope() throws Exception {
        UploadSessionView session = createSessionView(UploadSessionUploadMode.DIRECT_MULTIPART, UploadSessionStatus.CREATED, null, false, 3, 8L * 1024 * 1024);
        IdentityAuthenticatedUser authenticatedUser = createAuthenticatedUser(7L);
        when(uploadSessionService.createSession(eq(authenticatedUser), any(UploadSessionCreateCommand.class))).thenReturn(session);

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
                .andExpect(jsonPath("$.data.directUpload").value(true))
                .andExpect(jsonPath("$.data.multipartUpload").value(true))
                .andExpect(jsonPath("$.data.uploadMode").value("DIRECT_MULTIPART"))
                .andExpect(jsonPath("$.data.strategy.partPrepareUrlTemplate").value("/api/v2/files/upload-sessions/session-1/parts/{partIndex}/prepare"))
                .andExpect(jsonPath("$.data.strategy.partRecordUrlTemplate").value("/api/v2/files/upload-sessions/session-1/parts/{partIndex}"))
                .andExpect(jsonPath("$.data.strategy.completeUrl").value("/api/v2/files/upload-sessions/session-1/complete"))
                .andExpect(jsonPath("$.data.chunkSize").value(8388608))
                .andExpect(jsonPath("$.data.chunkCount").value(3));
    }

    @Test
    void shouldReturnOwnedUploadSessionWithV2Envelope() throws Exception {
        UploadSessionView session = createSessionView(UploadSessionUploadMode.DIRECT_MULTIPART, UploadSessionStatus.CREATED, null, false, 3, 8L * 1024 * 1024);
        when(uploadSessionService.getOwnedSession(7L, "session-1")).thenReturn(session);

        mockMvc.perform(get("/api/v2/files/upload-sessions/session-1")
                        .with(user(userDetails())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.sessionId").value("session-1"))
                .andExpect(jsonPath("$.data.status").value("CREATED"))
                .andExpect(jsonPath("$.data.directUpload").value(true))
                .andExpect(jsonPath("$.data.uploadMode").value("DIRECT_MULTIPART"))
                .andExpect(jsonPath("$.data.multipartUpload").value(true))
                .andExpect(jsonPath("$.data.strategy.partPrepareUrlTemplate").value("/api/v2/files/upload-sessions/session-1/parts/{partIndex}/prepare"))
                .andExpect(jsonPath("$.data.strategy.partRecordUrlTemplate").value("/api/v2/files/upload-sessions/session-1/parts/{partIndex}"))
                .andExpect(jsonPath("$.data.strategy.completeUrl").value("/api/v2/files/upload-sessions/session-1/complete"));
    }

    @Test
    void shouldExposeRuntimeStateWhenRedisUploadStateExists() throws Exception {
        UploadSessionView session = createSessionView(
                UploadSessionUploadMode.DIRECT_MULTIPART,
                UploadSessionStatus.CREATED,
                new UploadSessionRuntimeState(
                        "uploading",
                        1024L,
                        2,
                        25,
                        LocalDateTime.of(2026, 4, 10, 12, 0),
                        LocalDateTime.of(2026, 4, 11, 12, 0)
                ),
                false,
                3,
                8L * 1024 * 1024
        );
        when(uploadSessionService.getOwnedSession(7L, "session-1")).thenReturn(session);

        mockMvc.perform(get("/api/v2/files/upload-sessions/session-1")
                        .with(user(userDetails())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.runtime.phase").value("uploading"))
                .andExpect(jsonPath("$.data.runtime.uploadedBytes").value(1024))
                .andExpect(jsonPath("$.data.runtime.uploadedPartCount").value(2))
                .andExpect(jsonPath("$.data.runtime.progressPercent").value(25));
    }

    @Test
    void shouldReturnDirectSingleStrategyInSessionResponse() throws Exception {
        when(uploadSessionService.getOwnedSession(7L, "session-1"))
                .thenReturn(createSessionView(UploadSessionUploadMode.DIRECT_SINGLE, UploadSessionStatus.CREATED, null, false, 1, 8L * 1024 * 1024));

        mockMvc.perform(get("/api/v2/files/upload-sessions/session-1")
                        .with(user(userDetails())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.uploadMode").value("DIRECT_SINGLE"))
                .andExpect(jsonPath("$.data.strategy.prepareUrl").value("/api/v2/files/upload-sessions/session-1/prepare"))
                .andExpect(jsonPath("$.data.strategy.completeUrl").value("/api/v2/files/upload-sessions/session-1/complete"));
    }

    @Test
    void shouldPrepareSingleUploadWithV2Envelope() throws Exception {
        when(uploadSessionService.prepareOwnedUpload(7L, "session-1"))
                .thenReturn(new com.yoyuzh.files.content.api.PreparedUpload(
                        true,
                        "https://upload.example.com/session-1",
                        "PUT",
                        Map.of("Content-Type", "video/mp4"),
                        "blobs/session-1"
                ));

        mockMvc.perform(get("/api/v2/files/upload-sessions/session-1/prepare")
                        .with(user(userDetails())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.direct").value(true))
                .andExpect(jsonPath("$.data.uploadUrl").value("https://upload.example.com/session-1"))
                .andExpect(jsonPath("$.data.method").value("PUT"))
                .andExpect(jsonPath("$.data.headers['Content-Type']").value("video/mp4"));
    }

    @Test
    void shouldUploadProxyContentWithV2Envelope() throws Exception {
        UploadSessionView session = createSessionView(UploadSessionUploadMode.PROXY, UploadSessionStatus.UPLOADING, null, false, 1, 20L);
        when(uploadSessionService.uploadOwnedContent(eq(7L), eq("session-1"), any())).thenReturn(session);

        mockMvc.perform(multipart("/api/v2/files/upload-sessions/session-1/content")
                        .file("file", "payload".getBytes())
                        .with(user(userDetails())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.sessionId").value("session-1"))
                .andExpect(jsonPath("$.data.status").value("UPLOADING"))
                .andExpect(jsonPath("$.data.uploadMode").value("PROXY"))
                .andExpect(jsonPath("$.data.strategy.proxyContentUrl").value("/api/v2/files/upload-sessions/session-1/content"))
                .andExpect(jsonPath("$.data.strategy.proxyFormField").value("file"))
                .andExpect(jsonPath("$.data.strategy.completeUrl").value("/api/v2/files/upload-sessions/session-1/complete"));
    }

    @Test
    void shouldKeepRegularProxyStrategyWithoutTusUrl() throws Exception {
        UploadSessionView session = createSessionView(UploadSessionUploadMode.PROXY, UploadSessionStatus.UPLOADING, null, false, 1, 20L);
        when(uploadSessionService.getOwnedSession(7L, "session-1")).thenReturn(session);

        mockMvc.perform(get("/api/v2/files/upload-sessions/session-1")
                        .with(user(userDetails())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.uploadMode").value("PROXY"))
                .andExpect(jsonPath("$.data.strategy.proxyContentUrl").value("/api/v2/files/upload-sessions/session-1/content"))
                .andExpect(jsonPath("$.data.strategy.proxyFormField").value("file"))
                .andExpect(jsonPath("$.data.strategy.tusUrl").doesNotExist());
    }

    @Test
    void shouldCompleteUploadSessionWithV2Envelope() throws Exception {
        UploadSessionView session = createSessionView(UploadSessionUploadMode.DIRECT_MULTIPART, UploadSessionStatus.COMPLETED, null, false, 3, 8L * 1024 * 1024);
        when(uploadSessionService.completeOwnedSession(7L, "session-1")).thenReturn(session);

        mockMvc.perform(post("/api/v2/files/upload-sessions/session-1/complete")
                        .with(user(userDetails())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.sessionId").value("session-1"))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));
    }

    @Test
    void shouldRecordUploadSessionPartWithV2Envelope() throws Exception {
        UploadSessionView session = createSessionView(UploadSessionUploadMode.DIRECT_MULTIPART, UploadSessionStatus.UPLOADING, null, false, 3, 8L * 1024 * 1024);
        when(uploadSessionService.recordUploadedPart(eq(7L), eq("session-1"), eq(1), any())).thenReturn(session);

        mockMvc.perform(put("/api/v2/files/upload-sessions/session-1/parts/1")
                        .with(user(userDetails()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "etag": "etag-1",
                                  "size": 8388608
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.sessionId").value("session-1"))
                .andExpect(jsonPath("$.data.status").value("UPLOADING"));
    }

    @Test
    void shouldPrepareMultipartPartUploadWithV2Envelope() throws Exception {
        when(uploadSessionService.prepareOwnedPartUpload(7L, "session-1", 1))
                .thenReturn(new com.yoyuzh.files.content.api.PreparedUpload(
                        true,
                        "https://upload.example.com/session-1/part-2",
                        "PUT",
                        Map.of("Content-Type", "video/mp4"),
                        "blobs/session-1"
                ));

        mockMvc.perform(get("/api/v2/files/upload-sessions/session-1/parts/1/prepare")
                        .with(user(userDetails())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.direct").value(true))
                .andExpect(jsonPath("$.data.uploadUrl").value("https://upload.example.com/session-1/part-2"))
                .andExpect(jsonPath("$.data.method").value("PUT"))
                .andExpect(jsonPath("$.data.headers['Content-Type']").value("video/mp4"));
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

    private IdentityAuthenticatedUser createAuthenticatedUser(Long id) {
        return new IdentityAuthenticatedUser(
                id,
                "alice",
                "encoded",
                IdentityRoleName.USER,
                false,
                "session-1",
                "session-1",
                null,
                1024L * 1024 * 1024,
                100L * 1024 * 1024
        );
    }

    private UploadSessionView createSessionView(UploadSessionUploadMode uploadMode,
                                                UploadSessionStatus status,
                                                UploadSessionRuntimeState runtimeState,
                                                boolean tusBacked,
                                                Integer chunkCount,
                                                Long chunkSize) {
        return new UploadSessionView(
                "session-1",
                "blobs/session-1",
                "/docs",
                "movie.mp4",
                "video/mp4",
                20L * 1024 * 1024,
                42L,
                status,
                chunkSize,
                chunkCount,
                LocalDateTime.of(2026, 4, 9, 6, 0),
                LocalDateTime.of(2026, 4, 8, 6, 0),
                LocalDateTime.of(2026, 4, 8, 6, 0),
                runtimeState,
                uploadMode,
                tusBacked
        );
    }
}
