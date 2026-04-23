package com.yoyuzh.boot.web;

import com.yoyuzh.boot.web.v2.ApiV2Response;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v2/site")
public class SiteRuntimeConfigController {

    @GetMapping("/config")
    public ApiV2Response<SiteRuntimeConfigResponse> config() {
        return ApiV2Response.success(SiteRuntimeConfigResponse.defaults());
    }
}
