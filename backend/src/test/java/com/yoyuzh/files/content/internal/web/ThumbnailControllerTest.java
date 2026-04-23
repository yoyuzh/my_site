package com.yoyuzh.files.content.internal.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ThumbnailControllerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ThumbnailController()).build();
    }

    @Test
    void shouldReturnPlaceholderThumbnailWhenNoDerivativeExists() throws Exception {
        mockMvc.perform(get("/api/v2/files/{fileId}/thumbnail", 42L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.fileId").value(42))
                .andExpect(jsonPath("$.data.available").value(false))
                .andExpect(jsonPath("$.data.url").value(""));
    }
}
