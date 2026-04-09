package com.yoyuzh.api.v2.files;

import com.yoyuzh.auth.CustomUserDetailsService;
import com.yoyuzh.auth.User;
import com.yoyuzh.files.events.FileEventService;
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

    private final FileEventService fileEventService;
    private final CustomUserDetailsService userDetailsService;

    @GetMapping(value = "/events", produces = "text/event-stream")
    public SseEmitter events(@AuthenticationPrincipal UserDetails userDetails,
                             @RequestParam(required = false, defaultValue = "/") String path,
                             @RequestHeader(value = "X-Yoyuzh-Client-Id", required = false) String clientId) {
        User user = userDetailsService.loadDomainUser(userDetails.getUsername());
        return fileEventService.openStream(user, path, clientId);
    }
}
