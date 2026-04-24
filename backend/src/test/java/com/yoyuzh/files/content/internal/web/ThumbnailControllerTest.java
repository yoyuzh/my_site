package com.yoyuzh.files.content.internal.web;

import com.yoyuzh.boot.web.v2.ApiV2ExceptionHandler;
import com.yoyuzh.shared.kernel.BusinessException;
import com.yoyuzh.shared.kernel.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
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

    @Test
    void shouldApplyV2ExceptionHandlerToFilesContentControllers() throws Exception {
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new ThrowingThumbnailController())
                .setControllerAdvice(new ApiV2ExceptionHandler())
                .build();

        mvc.perform(get("/api/v2/files/{fileId}/thumbnail-error", 42L))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value(2429))
                .andExpect(jsonPath("$.msg").value("quota exceeded"));
    }

    @RestController
    static class ThrowingThumbnailController {

        @GetMapping("/api/v2/files/{fileId}/thumbnail-error")
        public void thumbnailError() {
            throw new BusinessException(ErrorCode.QUOTA_EXCEEDED, "quota exceeded");
        }
    }
}
