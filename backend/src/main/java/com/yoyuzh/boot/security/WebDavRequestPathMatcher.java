package com.yoyuzh.boot.security;

final class WebDavRequestPathMatcher {

    private WebDavRequestPathMatcher() {
    }

    static boolean isWebDavRequest(String requestUri) {
        return "/dav".equals(requestUri) || (requestUri != null && requestUri.startsWith("/dav/"));
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
}
