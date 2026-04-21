package com.yoyuzh.boot.web;

import com.yoyuzh.boot.web.v2.ApiV2Response;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v2/site")
public class SiteV2Controller {

    @GetMapping("/ping")
    public ApiV2Response<SiteV2PingResponse> ping() {
        return ApiV2Response.success(new SiteV2PingResponse("ok", "v2"));
    }
}
