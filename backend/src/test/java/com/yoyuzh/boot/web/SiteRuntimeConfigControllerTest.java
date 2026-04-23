package com.yoyuzh.boot.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SiteRuntimeConfigControllerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new SiteRuntimeConfigController()).build();
    }

    @Test
    void shouldExposePublicRuntimeConfig() throws Exception {
        mockMvc.perform(get("/api/v2/site/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.msg").value("success"))
                .andExpect(jsonPath("$.data.siteName").value("Yoyuzh 网盘"))
                .andExpect(jsonPath("$.data.registrationEnabled").value(true))
                .andExpect(jsonPath("$.data.passwordLoginEnabled").value(true))
                .andExpect(jsonPath("$.data.captchaEnabled").value(false))
                .andExpect(jsonPath("$.data.apiVersion").value("v2"));
    }
}
