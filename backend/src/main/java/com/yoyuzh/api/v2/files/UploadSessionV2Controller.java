package com.yoyuzh.api.v2.files;

import com.yoyuzh.api.v2.ApiV2Response;
import com.yoyuzh.auth.CustomUserDetailsService;
import com.yoyuzh.auth.User;
import com.yoyuzh.files.UploadSession;
import com.yoyuzh.files.UploadSessionCreateCommand;
import com.yoyuzh.files.UploadSessionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v2/files/upload-sessions")
@RequiredArgsConstructor
public class UploadSessionV2Controller {

    private final UploadSessionService uploadSessionService;
    private final CustomUserDetailsService userDetailsService;

    @PostMapping
    public ApiV2Response<UploadSessionV2Response> createSession(@AuthenticationPrincipal UserDetails userDetails,
                                                                @Valid @RequestBody CreateUploadSessionV2Request request) {
        User user = userDetailsService.loadDomainUser(userDetails.getUsername());
        UploadSession session = uploadSessionService.createSession(user, new UploadSessionCreateCommand(
                request.path(),
                request.filename(),
                request.contentType(),
                request.size()
        ));
        return ApiV2Response.success(toResponse(session));
    }

    @GetMapping("/{sessionId}")
    public ApiV2Response<UploadSessionV2Response> getSession(@AuthenticationPrincipal UserDetails userDetails,
                                                             @PathVariable String sessionId) {
        User user = userDetailsService.loadDomainUser(userDetails.getUsername());
        return ApiV2Response.success(toResponse(uploadSessionService.getOwnedSession(user, sessionId)));
    }

    @DeleteMapping("/{sessionId}")
    public ApiV2Response<UploadSessionV2Response> cancelSession(@AuthenticationPrincipal UserDetails userDetails,
                                                                @PathVariable String sessionId) {
        User user = userDetailsService.loadDomainUser(userDetails.getUsername());
        return ApiV2Response.success(toResponse(uploadSessionService.cancelOwnedSession(user, sessionId)));
    }

    @PostMapping("/{sessionId}/complete")
    public ApiV2Response<UploadSessionV2Response> completeSession(@AuthenticationPrincipal UserDetails userDetails,
                                                                  @PathVariable String sessionId) {
        User user = userDetailsService.loadDomainUser(userDetails.getUsername());
        return ApiV2Response.success(toResponse(uploadSessionService.completeOwnedSession(user, sessionId)));
    }

    private UploadSessionV2Response toResponse(UploadSession session) {
        return new UploadSessionV2Response(
                session.getSessionId(),
                session.getObjectKey(),
                session.getTargetPath(),
                session.getFilename(),
                session.getContentType(),
                session.getSize(),
                session.getStatus().name(),
                session.getChunkSize(),
                session.getChunkCount(),
                session.getExpiresAt(),
                session.getCreatedAt(),
                session.getUpdatedAt()
        );
    }
}
