package com.yoyuzh.files.workspace.internal.web;

import com.yoyuzh.boot.security.CustomUserDetailsService;
import com.yoyuzh.files.workspace.api.DownloadUrlResponse;
import com.yoyuzh.files.workspace.api.FileDetailResponse;
import com.yoyuzh.files.workspace.api.FavoriteFileResponse;
import com.yoyuzh.files.workspace.internal.application.FileViewerConfigService;
import com.yoyuzh.files.workspace.internal.application.WorkspaceTagService;
import com.yoyuzh.files.workspace.internal.application.FileService;
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
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FileProductCapabilityControllerTest {

    private FileService fileService;
    private CustomUserDetailsService userDetailsService;
    private WorkspaceTagService workspaceTagService;
    private FileViewerConfigService fileViewerConfigService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        fileService = mock(FileService.class);
        userDetailsService = mock(CustomUserDetailsService.class);
        workspaceTagService = mock(WorkspaceTagService.class);
        fileViewerConfigService = new FileViewerConfigService();
        when(userDetailsService.loadUserId("alice")).thenReturn(7L);
        when(workspaceTagService.listFileTags(eq(7L), eq(1L))).thenReturn(List.of());
        mockMvc = MockMvcBuilders.standaloneSetup(new FileController(
                        fileService,
                        userDetailsService,
                        workspaceTagService,
                        fileViewerConfigService
                ))
                .setCustomArgumentResolvers(authenticationPrincipalResolver())
                .build();
    }

    @Test
    void shouldExposeViewerConfig() throws Exception {
        mockMvc.perform(get("/api/files/viewers/config").with(user(userDetails())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fileViewers[?(@.id == 'markdown')]").exists())
                .andExpect(jsonPath("$.data.fileViewers[?(@.id == 'microsoft-office')]").exists())
                .andExpect(jsonPath("$.data.defaultViewerMapping.md").value("markdown"))
                .andExpect(jsonPath("$.data.defaultViewerMapping.docx").value("microsoft-office"));
    }

    @Test
    void shouldUseViewerSourceUrlWhenViewerQueryIsEnabled() throws Exception {
        when(fileService.getViewerSourceUrl(eq(7L), eq(1L))).thenReturn(new DownloadUrlResponse("https://cdn.yoyuzh.xyz/files/blob-1"));

        mockMvc.perform(get("/api/files/download/{fileId}/url", 1L)
                        .param("viewer", "true")
                        .with(user(userDetails())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.url").value("https://cdn.yoyuzh.xyz/files/blob-1"));

        verify(fileService).getViewerSourceUrl(eq(7L), eq(1L));
    }

    @Test
    void shouldExposeFileDetail() throws Exception {
        when(fileService.detail(eq(7L), eq(1L))).thenReturn(new FileDetailResponse(
                1L,
                "notes.txt",
                "/docs",
                5L,
                "text/plain",
                false,
                false,
                false,
                LocalDateTime.of(2026, 4, 21, 10, 0),
                LocalDateTime.of(2026, 4, 21, 11, 0),
                null,
                null,
                List.of()
        ));

        mockMvc.perform(get("/api/files/{fileId}/detail", 1L).with(user(userDetails())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.favorite").value(false));
    }

    @Test
    void shouldBatchDeleteFiles() throws Exception {
        mockMvc.perform(post("/api/files/batch/delete")
                        .with(user(userDetails()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fileIds": [1, 2]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(fileService).batchDelete(eq(7L), eq(List.of(1L, 2L)));
    }

    @Test
    void shouldFavoriteAndUnfavoriteFile() throws Exception {
        when(fileService.setFavorite(eq(7L), eq(1L), eq(true)))
                .thenReturn(new FavoriteFileResponse(1L, true));
        when(fileService.setFavorite(eq(7L), eq(1L), eq(false)))
                .thenReturn(new FavoriteFileResponse(1L, false));

        mockMvc.perform(put("/api/files/{fileId}/favorite", 1L).with(user(userDetails())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fileId").value(1))
                .andExpect(jsonPath("$.data.favorite").value(true));

        mockMvc.perform(delete("/api/files/{fileId}/favorite", 1L).with(user(userDetails())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fileId").value(1))
                .andExpect(jsonPath("$.data.favorite").value(false));
    }

    @Test
    void shouldListFavorites() throws Exception {
        when(fileService.listFavorites(eq(7L))).thenReturn(List.of(
                new FavoriteFileResponse(1L, true),
                new FavoriteFileResponse(2L, true)
        ));

        mockMvc.perform(get("/api/files/favorites").with(user(userDetails())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].fileId").value(1))
                .andExpect(jsonPath("$.data[1].fileId").value(2));
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
