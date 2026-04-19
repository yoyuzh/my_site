package com.yoyuzh.config;

import com.yoyuzh.app.android.api.AndroidReleaseQueryApi;
import com.yoyuzh.common.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AndroidReleaseControllerTest {

    @Mock
    private AndroidReleaseQueryApi androidReleaseQueryApi;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AndroidReleaseController(androidReleaseQueryApi))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void shouldExposeLatestAndroidReleaseMetadataWithoutAuthentication() throws Exception {
        AndroidReleaseResponse response = new AndroidReleaseResponse(
                "https://api.yoyuzh.xyz/api/app/android/download/yoyuzh-portal-2026.04.03.1754.apk",
                "yoyuzh-portal-2026.04.03.1754.apk",
                "260931754",
                "2026.04.03.1754",
                "2026-04-03T08:33:54Z"
        );
        when(androidReleaseQueryApi.getLatestRelease()).thenReturn(response);

        mockMvc.perform(get("/api/app/android/latest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.downloadUrl").value("https://api.yoyuzh.xyz/api/app/android/download/yoyuzh-portal-2026.04.03.1754.apk"))
                .andExpect(jsonPath("$.data.fileName").value("yoyuzh-portal-2026.04.03.1754.apk"))
                .andExpect(jsonPath("$.data.versionCode").value("260931754"))
                .andExpect(jsonPath("$.data.versionName").value("2026.04.03.1754"))
                .andExpect(jsonPath("$.data.publishedAt").value("2026-04-03T08:33:54Z"));

        verify(androidReleaseQueryApi).getLatestRelease();
    }

    @Test
    void shouldRedirectAndroidDownloadWithoutAuthentication() throws Exception {
        when(androidReleaseQueryApi.downloadLatestRelease())
                .thenReturn(new AndroidReleaseDownload("yoyuzh-portal-2026.04.03.1754.apk", "apk-binary".getBytes()));

        mockMvc.perform(get("/api/app/android/download"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, org.hamcrest.Matchers.containsString("filename*=UTF-8''yoyuzh-portal-2026.04.03.1754.apk")));

        verify(androidReleaseQueryApi).downloadLatestRelease();
    }
}
