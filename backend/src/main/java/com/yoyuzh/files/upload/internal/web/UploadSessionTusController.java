package com.yoyuzh.files.upload.internal.web;

import com.yoyuzh.files.upload.internal.application.UploadSessionTusIngressService;
import com.yoyuzh.files.upload.internal.application.UploadSessionTusService;
import com.yoyuzh.files.upload.internal.application.UploadSessionTusState;
import com.yoyuzh.files.upload.internal.application.UploadSessionService;
import com.yoyuzh.identity.access.api.IdentityAuthenticatedUser;
import com.yoyuzh.identity.access.api.IdentityAuthenticationApi;
import com.yoyuzh.shared.kernel.BusinessException;
import com.yoyuzh.shared.kernel.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v2/files/upload-sessions/{sessionId}/tus")
public class UploadSessionTusController {

    private final UploadSessionService uploadSessionService;
    private final UploadSessionTusIngressService uploadSessionTusIngressService;
    private final UploadSessionTusService uploadSessionTusService;
    private final IdentityAuthenticationApi identityAuthenticationApi;

    public UploadSessionTusController(UploadSessionService uploadSessionService,
                                      UploadSessionTusIngressService uploadSessionTusIngressService,
                                      UploadSessionTusService uploadSessionTusService,
                                      IdentityAuthenticationApi identityAuthenticationApi) {
        this.uploadSessionService = uploadSessionService;
        this.uploadSessionTusIngressService = uploadSessionTusIngressService;
        this.uploadSessionTusService = uploadSessionTusService;
        this.identityAuthenticationApi = identityAuthenticationApi;
    }

    @RequestMapping(method = RequestMethod.OPTIONS)
    public ResponseEntity<Void> options() {
        return withTusHeaders(ResponseEntity.status(204))
                .header("Tus-Version", uploadSessionTusService.tusResumableVersion())
                .header("Tus-Extension", "creation,termination")
                .build();
    }

    @PostMapping
    public ResponseEntity<Void> create(@AuthenticationPrincipal UserDetails userDetails,
                                       @PathVariable String sessionId,
                                       @RequestHeader("Upload-Length") Long uploadLength,
                                       HttpServletRequest request) {
        Long userId = loadAuthenticatedUser(userDetails).id();
        UploadSessionTusState session = uploadSessionService.startTusSession(userId, sessionId, uploadLength);
        return withTusHeaders(ResponseEntity.status(201))
                .header(HttpHeaders.LOCATION, request.getRequestURI())
                .header("Upload-Offset", String.valueOf(session.uploadOffset()))
                .header("Upload-Length", String.valueOf(session.uploadLength()))
                .build();
    }

    @RequestMapping(method = RequestMethod.HEAD)
    public ResponseEntity<Void> head(@AuthenticationPrincipal UserDetails userDetails,
                                     @PathVariable String sessionId) {
        Long userId = loadAuthenticatedUser(userDetails).id();
        UploadSessionTusState session = uploadSessionService.getTusSessionState(userId, sessionId);
        return withTusHeaders(ResponseEntity.status(204))
                .header("Upload-Offset", String.valueOf(session.uploadOffset()))
                .header("Upload-Length", String.valueOf(session.uploadLength()))
                .build();
    }

    @PatchMapping(consumes = "application/offset+octet-stream")
    public ResponseEntity<Void> patch(@AuthenticationPrincipal UserDetails userDetails,
                                      @PathVariable String sessionId,
                                      @RequestHeader("Upload-Offset") long uploadOffset,
                                      HttpServletRequest request) throws java.io.IOException {
        Long userId = loadAuthenticatedUser(userDetails).id();
        UploadSessionTusState session = uploadSessionTusIngressService.appendSession(
                userId,
                sessionId,
                uploadOffset,
                request.getInputStream(),
                request.getContentLengthLong()
        );
        return withTusHeaders(ResponseEntity.status(204))
                .header("Upload-Offset", String.valueOf(session.uploadOffset()))
                .build();
    }

    @DeleteMapping
    public ResponseEntity<Void> delete(@AuthenticationPrincipal UserDetails userDetails,
                                       @PathVariable String sessionId) {
        Long userId = loadAuthenticatedUser(userDetails).id();
        uploadSessionService.cancelTusSession(userId, sessionId);
        return withTusHeaders(ResponseEntity.status(204)).build();
    }

    @GetMapping("/status")
    public ResponseEntity<Void> status(@AuthenticationPrincipal UserDetails userDetails,
                                       @PathVariable String sessionId) {
        Long userId = loadAuthenticatedUser(userDetails).id();
        UploadSessionTusState session = uploadSessionService.getTusSessionState(userId, sessionId);
        return withTusHeaders(ResponseEntity.status(204))
                .header("Upload-Offset", String.valueOf(session.uploadOffset()))
                .header("Upload-Length", String.valueOf(session.uploadLength()))
                .build();
    }

    private ResponseEntity.BodyBuilder withTusHeaders(ResponseEntity.BodyBuilder builder) {
        return builder.header("Tus-Resumable", uploadSessionTusService.tusResumableVersion());
    }

    private IdentityAuthenticatedUser loadAuthenticatedUser(UserDetails userDetails) {
        return identityAuthenticationApi.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_LOGGED_IN, "user not found"));
    }
}
