package com.yoyuzh.boot.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;

final class WebDavRequestPathMatcher {

    private WebDavRequestPathMatcher() {
    }

    static boolean isWebDavRequest(String requestUri) {
        return "/dav".equals(requestUri)
                || "/api/dav".equals(requestUri)
                || (requestUri != null && (requestUri.startsWith("/dav/") || requestUri.startsWith("/api/dav/")));
    }

    static boolean isWebDavRequest(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        return isWebDavRequest(requestUri)
                || isMicrosoftDiscoveryRequest(request)
                || isMicrosoftSharePointProbe(request);
    }

    static boolean isMicrosoftOfficeProbeWithoutWebDavCredentials(HttpServletRequest request) {
        return !hasBasicAuthorization(request.getHeader("Authorization"))
                && (isMicrosoftOfficeWebDavHeadProbe(request) || isMicrosoftSharePointProbe(request));
    }

    private static boolean isMicrosoftOfficeWebDavHeadProbe(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        return "HEAD".equals(request.getMethod())
                && isWebDavRequest(requestUri)
                && isMicrosoftOfficeUserAgent(request.getHeader("User-Agent"));
    }

    static boolean hasUnsafePath(String requestUri) {
        if (!isWebDavRequest(requestUri)) {
            return false;
        }
        for (String segment : requestUri.split("/")) {
            if (".".equals(segment) || "..".equals(segment)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isMicrosoftDiscoveryRequest(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        if (!"/".equals(requestUri) && !"/api".equals(requestUri) && !"/api/".equals(requestUri)) {
            return false;
        }
        String method = request.getMethod();
        if (!"OPTIONS".equals(method) && !"PROPFIND".equals(method)) {
            return false;
        }
        return isMicrosoftWebDavUserAgent(request.getHeader("User-Agent"));
    }

    private static boolean isMicrosoftSharePointProbe(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        return requestUri != null
                && requestUri.startsWith("/_api/v2.0/shares/")
                && isMicrosoftOfficeUserAgent(request.getHeader("User-Agent"));
    }

    private static boolean isMicrosoftWebDavUserAgent(String userAgent) {
        if (!StringUtils.hasText(userAgent)) {
            return false;
        }
        return userAgent.contains("Microsoft-WebDAV-MiniRedir")
                || userAgent.contains("Microsoft Office");
    }

    private static boolean isMicrosoftOfficeUserAgent(String userAgent) {
        return StringUtils.hasText(userAgent) && userAgent.contains("Microsoft Office");
    }

    private static boolean hasBasicAuthorization(String authorizationHeader) {
        return StringUtils.hasText(authorizationHeader) && authorizationHeader.startsWith("Basic ");
    }
}
