package com.yoyuzh.boot.web;

public record SiteRuntimeConfigResponse(
        String siteName,
        String siteDescription,
        boolean registrationEnabled,
        boolean passwordLoginEnabled,
        boolean captchaEnabled,
        String apiVersion
) {

    public static SiteRuntimeConfigResponse defaults() {
        return new SiteRuntimeConfigResponse(
                "Yoyuzh 网盘",
                "个人网盘与快速传输平台",
                true,
                true,
                false,
                "v2"
        );
    }
}
