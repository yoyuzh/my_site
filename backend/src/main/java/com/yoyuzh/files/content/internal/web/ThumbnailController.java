package com.yoyuzh.files.content.internal.web;

import com.yoyuzh.boot.web.v2.ApiV2Response;
import com.yoyuzh.files.content.api.ThumbnailResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v2/files")
public class ThumbnailController {

    @GetMapping("/{fileId}/thumbnail")
    public ApiV2Response<ThumbnailResponse> thumbnail(@PathVariable Long fileId) {
        return ApiV2Response.success(ThumbnailResponse.unavailable(fileId));
    }
}
