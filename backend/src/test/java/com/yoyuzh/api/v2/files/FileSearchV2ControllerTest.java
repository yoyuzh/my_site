package com.yoyuzh.api.v2.files;

import com.yoyuzh.api.v2.ApiV2ExceptionHandler;
import com.yoyuzh.auth.CustomUserDetailsService;
import com.yoyuzh.auth.User;
import com.yoyuzh.shared.kernel.PageResponse;
import com.yoyuzh.files.search.api.FileSearchApi;
import com.yoyuzh.files.search.api.SearchFilesQuery;
import com.yoyuzh.files.workspace.api.FileMetadataResponse;
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
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FileSearchV2ControllerTest {

    private FileSearchApi fileSearchApi;
    private CustomUserDetailsService userDetailsService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        fileSearchApi = mock(FileSearchApi.class);
        userDetailsService = mock(CustomUserDetailsService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new FileSearchV2Controller(fileSearchApi, userDetailsService))
                .setControllerAdvice(new ApiV2ExceptionHandler())
                .setCustomArgumentResolvers(authenticationPrincipalResolver())
                .build();
    }

    @Test
    void shouldSearchFilesWithV2Envelope() throws Exception {
        User user = createUser(7L);
        when(userDetailsService.loadDomainUser("alice")).thenReturn(user);
        when(fileSearchApi.search(eq(user), any(SearchFilesQuery.class))).thenReturn(new PageResponse<>(
                List.of(new FileMetadataResponse(
                        10L,
                        "notes.txt",
                        "/docs",
                        5L,
                        "text/plain",
                        false,
                        LocalDateTime.of(2026, 4, 8, 10, 0)
                )),
                1,
                0,
                20
        ));

        mockMvc.perform(get("/api/v2/files/search")
                        .with(user(userDetails()))
                        .accept(MediaType.APPLICATION_JSON)
                        .param("name", "note")
                        .param("type", "file")
                        .param("sizeGte", "1")
                        .param("sizeLte", "100")
                        .param("createdGte", "2026-04-08T08:00:00")
                        .param("createdLte", "2026-04-08T12:00:00")
                        .param("updatedGte", "2026-04-08T09:00:00")
                        .param("updatedLte", "2026-04-08T18:00:00")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].filename").value("notes.txt"));
    }

    @Test
    void shouldRejectUnsupportedTypeFilter() throws Exception {
        mockMvc.perform(get("/api/v2/files/search")
                        .with(user(userDetails()))
                        .param("type", "image"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(2400))
                .andExpect(jsonPath("$.msg").value("文件类型筛选只支持 file 或 directory"));
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
}
