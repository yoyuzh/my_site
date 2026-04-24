package com.yoyuzh.identity.access.internal.web;

import com.yoyuzh.identity.access.internal.application.AvatarDownloadResult;
import com.yoyuzh.identity.access.api.UserCapacityResponse;
import com.yoyuzh.identity.access.api.UserSettingsResponse;
import com.yoyuzh.identity.access.internal.application.AuthService;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

class UserControllerSettingsTest {

    private AuthService authService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        authService = mock(AuthService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new UserController(authService))
                .setCustomArgumentResolvers(authenticationPrincipalResolver())
                .build();
    }

    @Test
    void shouldExposeCapacity() throws Exception {
        when(authService.getCapacity("demo"))
                .thenReturn(new UserCapacityResponse(1024L, 256L, 768L, 128L));

        mockMvc.perform(get("/api/user/capacity").with(user(userDetails())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalBytes").value(1024))
                .andExpect(jsonPath("$.data.usedBytes").value(256))
                .andExpect(jsonPath("$.data.availableBytes").value(768))
                .andExpect(jsonPath("$.data.maxUploadSizeBytes").value(128));
    }

    @Test
    void shouldExposeSettings() throws Exception {
        when(authService.getSettings("demo"))
                .thenReturn(new UserSettingsResponse("demo", "zh-CN", "system", false));

        mockMvc.perform(get("/api/user/settings").with(user(userDetails())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.displayName").value("demo"))
                .andExpect(jsonPath("$.data.preferredLanguage").value("zh-CN"))
                .andExpect(jsonPath("$.data.preferredTheme").value("system"))
                .andExpect(jsonPath("$.data.disableViewSync").value(false));
    }

    @Test
    void shouldAdaptInlineAvatarDownloadResult() throws Exception {
        when(authService.getAvatarContent("demo"))
                .thenReturn(AvatarDownloadResult.inline("avatar.png", MediaType.IMAGE_PNG_VALUE, "avatar".getBytes()));

        mockMvc.perform(get("/api/user/avatar/content").with(user(userDetails())))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "inline; filename*=UTF-8''avatar.png"))
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andExpect(content().bytes("avatar".getBytes()));
    }

    @Test
    void shouldAdaptRedirectAvatarDownloadResult() throws Exception {
        when(authService.getAvatarContent("demo"))
                .thenReturn(AvatarDownloadResult.redirect("https://cdn.example.com/avatar.png"));

        mockMvc.perform(get("/api/user/avatar/content").with(user(userDetails())))
                .andExpect(status().isFound())
                .andExpect(header().string(HttpHeaders.LOCATION, Matchers.equalTo("https://cdn.example.com/avatar.png")));
    }

    private UserDetails userDetails() {
        return User.withUsername("demo").password("ignored").authorities(List.of()).build();
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
