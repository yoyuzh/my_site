package com.yoyuzh.files.upload.internal.web;

import com.yoyuzh.boot.web.v2.ApiV2Response;
import com.yoyuzh.identity.access.api.IdentityAuthenticatedUser;
import com.yoyuzh.identity.access.api.IdentityAuthenticationApi;
import com.yoyuzh.files.content.api.PreparedUpload;
import com.yoyuzh.files.upload.api.UploadSessionUploadMode;
import com.yoyuzh.files.upload.internal.application.UploadSessionCreateCommand;
import com.yoyuzh.files.upload.internal.application.UploadSessionPartCommand;
import com.yoyuzh.files.upload.internal.application.UploadSessionRuntimeState;
import com.yoyuzh.files.upload.internal.application.UploadSessionService;
import com.yoyuzh.files.upload.internal.application.UploadSessionTusService;
import com.yoyuzh.files.upload.internal.application.UploadSessionView;
import com.yoyuzh.shared.kernel.BusinessException;
import com.yoyuzh.shared.kernel.ErrorCode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v2/files/upload-sessions")
@RequiredArgsConstructor
public class UploadSessionV2Controller {

    private final UploadSessionService uploadSessionService;
    private final UploadSessionTusService uploadSessionTusService;
    private final IdentityAuthenticationApi identityAuthenticationApi;

    @PostMapping
    public ApiV2Response<UploadSessionV2Response> createSession(@AuthenticationPrincipal UserDetails userDetails,
                                                                @Valid @RequestBody CreateUploadSessionV2Request request) {
        IdentityAuthenticatedUser user = loadAuthenticatedUser(userDetails);
        UploadSessionView session = uploadSessionService.createSession(user, new UploadSessionCreateCommand(
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
        Long user = loadAuthenticatedUser(userDetails).id();
        return ApiV2Response.success(toResponse(uploadSessionService.getOwnedSession(user, sessionId)));
    }

    @GetMapping("/{sessionId}/prepare")
    public ApiV2Response<PreparedUploadV2Response> prepareUpload(@AuthenticationPrincipal UserDetails userDetails,
                                                                 @PathVariable String sessionId) {
        Long user = loadAuthenticatedUser(userDetails).id();
        PreparedUpload preparedUpload = uploadSessionService.prepareOwnedUpload(user, sessionId);
        return ApiV2Response.success(new PreparedUploadV2Response(
                preparedUpload.direct(),
                preparedUpload.uploadUrl(),
                preparedUpload.method(),
                preparedUpload.headers(),
                preparedUpload.storageName()
        ));
    }

    @DeleteMapping("/{sessionId}")
    public ApiV2Response<UploadSessionV2Response> cancelSession(@AuthenticationPrincipal UserDetails userDetails,
                                                                @PathVariable String sessionId) {
        Long user = loadAuthenticatedUser(userDetails).id();
        return ApiV2Response.success(toResponse(uploadSessionService.cancelOwnedSession(user, sessionId)));
    }

    @PostMapping("/{sessionId}/complete")
    public ApiV2Response<UploadSessionV2Response> completeSession(@AuthenticationPrincipal UserDetails userDetails,
                                                                  @PathVariable String sessionId) {
        Long user = loadAuthenticatedUser(userDetails).id();
        return ApiV2Response.success(toResponse(uploadSessionService.completeOwnedSession(user, sessionId)));
    }

    @PutMapping("/{sessionId}/parts/{partIndex}")
    public ApiV2Response<UploadSessionV2Response> recordPart(@AuthenticationPrincipal UserDetails userDetails,
                                                             @PathVariable String sessionId,
                                                             @PathVariable int partIndex,
                                                             @Valid @RequestBody MarkUploadSessionPartV2Request request) {
        Long user = loadAuthenticatedUser(userDetails).id();
        UploadSessionView session = uploadSessionService.recordUploadedPart(
                user,
                sessionId,
                partIndex,
                new UploadSessionPartCommand(request.etag(), request.size())
        );
        return ApiV2Response.success(toResponse(session));
    }

    @PostMapping("/{sessionId}/content")
    public ApiV2Response<UploadSessionV2Response> uploadContent(@AuthenticationPrincipal UserDetails userDetails,
                                                                @PathVariable String sessionId,
                                                                @RequestPart("file") MultipartFile file) {
        Long user = loadAuthenticatedUser(userDetails).id();
        return ApiV2Response.success(toResponse(uploadSessionService.uploadOwnedContent(user, sessionId, file)));
    }

    @GetMapping("/{sessionId}/parts/{partIndex}/prepare")
    public ApiV2Response<PreparedUploadV2Response> preparePartUpload(@AuthenticationPrincipal UserDetails userDetails,
                                                                     @PathVariable String sessionId,
                                                                     @PathVariable int partIndex) {
        Long user = loadAuthenticatedUser(userDetails).id();
        PreparedUpload preparedUpload = uploadSessionService.prepareOwnedPartUpload(user, sessionId, partIndex);
        return ApiV2Response.success(new PreparedUploadV2Response(
                preparedUpload.direct(),
                preparedUpload.uploadUrl(),
                preparedUpload.method(),
                preparedUpload.headers(),
                preparedUpload.storageName()
        ));
    }

    private UploadSessionV2Response toResponse(UploadSessionView session) {
        UploadSessionUploadMode uploadMode = session.uploadMode();
        return new UploadSessionV2Response(
                session.sessionId(),
                session.objectKey(),
                uploadMode != UploadSessionUploadMode.PROXY,
                uploadMode == UploadSessionUploadMode.DIRECT_MULTIPART,
                uploadMode.name(),
                session.targetPath(),
                session.filename(),
                session.contentType(),
                session.size(),
                session.storagePolicyId(),
                session.status().name(),
                session.chunkSize(),
                session.chunkCount(),
                session.expiresAt(),
                session.createdAt(),
                session.updatedAt(),
                session.runtimeState() == null ? null : toRuntimeResponse(session.runtimeState()),
                toStrategyResponse(session, uploadMode)
        );
    }

    private UploadSessionRuntimeStateV2Response toRuntimeResponse(UploadSessionRuntimeState runtimeState) {
        return new UploadSessionRuntimeStateV2Response(
                runtimeState.phase(),
                runtimeState.uploadedBytes(),
                runtimeState.uploadedPartCount(),
                runtimeState.progressPercent(),
                runtimeState.lastUpdatedAt(),
                runtimeState.expiresAt()
        );
    }

    private UploadSessionV2StrategyResponse toStrategyResponse(UploadSessionView session, UploadSessionUploadMode uploadMode) {
        String sessionBasePath = "/api/v2/files/upload-sessions/" + session.sessionId();
        boolean tusBacked = session.tusBacked();
        return switch (uploadMode) {
            case PROXY -> new UploadSessionV2StrategyResponse(
                    null,
                    tusBacked ? null : sessionBasePath + "/content",
                    null,
                    null,
                    sessionBasePath + "/complete",
                    tusBacked ? null : "file",
                    tusBacked ? sessionBasePath + "/tus" : null,
                    tusBacked ? java.util.Map.of("Tus-Resumable", uploadSessionTusService.tusResumableVersion()) : null
            );
            case DIRECT_SINGLE -> new UploadSessionV2StrategyResponse(
                    sessionBasePath + "/prepare",
                    null,
                    null,
                    null,
                    sessionBasePath + "/complete",
                    null,
                    null,
                    null
            );
            case DIRECT_MULTIPART -> new UploadSessionV2StrategyResponse(
                    null,
                    null,
                    sessionBasePath + "/parts/{partIndex}/prepare",
                    sessionBasePath + "/parts/{partIndex}",
                    sessionBasePath + "/complete",
                    null,
                    null,
                    null
            );
        };
    }

    private IdentityAuthenticatedUser loadAuthenticatedUser(UserDetails userDetails) {
        return identityAuthenticationApi.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_LOGGED_IN, "user not found"));
    }
}
