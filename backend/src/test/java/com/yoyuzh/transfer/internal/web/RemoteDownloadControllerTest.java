package com.yoyuzh.transfer.internal.web;

import com.yoyuzh.boot.web.GlobalExceptionHandler;
import com.yoyuzh.identity.access.api.IdentityUserDirectoryApi;
import com.yoyuzh.identity.access.api.IdentityUserProfileSummary;
import com.yoyuzh.transfer.api.RemoteDownloadApi;
import com.yoyuzh.transfer.api.RemoteDownloadDetailResponse;
import com.yoyuzh.transfer.internal.domain.DownloadEngineType;
import com.yoyuzh.transfer.api.RemoteDownloadSourceType;
import com.yoyuzh.transfer.internal.domain.RemoteDownloadStatus;
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

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RemoteDownloadControllerTest {

    private RemoteDownloadApi remoteDownloadApi;
    private IdentityUserDirectoryApi identityUserDirectoryApi;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        remoteDownloadApi = mock(RemoteDownloadApi.class);
        identityUserDirectoryApi = mock(IdentityUserDirectoryApi.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new RemoteDownloadController(remoteDownloadApi, identityUserDirectoryApi))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(authenticationPrincipalResolver())
                .build();
    }

    @Test
    void shouldCreateRemoteDownloadTaskForHttpSource() throws Exception {
        when(identityUserDirectoryApi.findProfileByUsername("alice"))
                .thenReturn(java.util.Optional.of(new IdentityUserProfileSummary(7L, "alice", "alice@example.com")));
        when(remoteDownloadApi.create(eq(7L), any())).thenReturn(new RemoteDownloadDetailResponse(
                11L,
                91L,
                RemoteDownloadStatus.PENDING.name(),
                RemoteDownloadSourceType.HTTP.name(),
                DownloadEngineType.ARIA2.name(),
                "/downloads",
                "https://example.com/demo.zip",
                "local-default",
                0,
                0,
                null,
                null,
                List.of(),
                Instant.parse("2026-04-26T08:00:00Z"),
                Instant.parse("2026-04-26T08:00:00Z"),
                null
        ));

        mockMvc.perform(multipart("/api/transfer/remote-downloads")
                        .with(user(userDetails()))
                        .param("sourceType", "HTTP")
                        .param("sourceValue", "https://example.com/demo.zip")
                        .param("targetPath", "/downloads")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.engineType").value("ARIA2"))
                .andExpect(jsonPath("$.data.targetPath").value("/downloads"));
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
}
