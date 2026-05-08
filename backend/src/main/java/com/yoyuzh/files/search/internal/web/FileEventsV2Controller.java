package com.yoyuzh.files.search.internal.web;

import com.yoyuzh.boot.security.AuthenticatedUserPrincipal;
import com.yoyuzh.boot.security.CustomUserDetailsService;
import com.yoyuzh.files.search.api.FileEventApi;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v2/files")
@RequiredArgsConstructor
public class FileEventsV2Controller {

    private final FileEventApi fileEventApi;
    private final CustomUserDetailsService userDetailsService;

    @GetMapping(value = "/events", produces = "text/event-stream")
    public SseEmitter events(@AuthenticationPrincipal UserDetails userDetails,
                             @RequestParam(required = false, defaultValue = "/") String path,
                             @RequestHeader(value = "X-Yoyuzh-Client-Id", required = false) String clientId) {
        return fileEventApi.openStream(currentUserId(userDetails), path, clientId);
    }

    private Long currentUserId(UserDetails userDetails) {
        if (userDetails instanceof AuthenticatedUserPrincipal authenticatedUserPrincipal) {
            return authenticatedUserPrincipal.getUserId();
        }
        return userDetailsService.loadUserId(userDetails.getUsername());
    }
}
