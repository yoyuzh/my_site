package com.yoyuzh.api.v2.tasks;

import com.yoyuzh.api.v2.ApiV2Response;
import com.yoyuzh.auth.CustomUserDetailsService;
import com.yoyuzh.auth.User;
import com.yoyuzh.common.PageResponse;
import com.yoyuzh.files.tasks.BackgroundTask;
import com.yoyuzh.files.tasks.BackgroundTaskCommandService;
import com.yoyuzh.files.tasks.BackgroundTaskType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v2/tasks")
@RequiredArgsConstructor
public class BackgroundTaskV2Controller {

    private final BackgroundTaskCommandService backgroundTaskCommandService;
    private final CustomUserDetailsService userDetailsService;

    @GetMapping
    public ApiV2Response<PageResponse<BackgroundTaskResponse>> list(@AuthenticationPrincipal UserDetails userDetails,
                                                                    @RequestParam(defaultValue = "0") @Min(0) int page,
                                                                    @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        User user = userDetailsService.loadDomainUser(userDetails.getUsername());
        var result = backgroundTaskCommandService.listOwnedTasks(
                user,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
        );
        return ApiV2Response.success(new PageResponse<>(
                result.getContent().stream().map(this::toResponse).toList(),
                result.getTotalElements(),
                result.getNumber(),
                result.getSize()
        ));
    }

    @GetMapping("/{id}")
    public ApiV2Response<BackgroundTaskResponse> get(@AuthenticationPrincipal UserDetails userDetails,
                                                     @PathVariable Long id) {
        User user = userDetailsService.loadDomainUser(userDetails.getUsername());
        return ApiV2Response.success(toResponse(backgroundTaskCommandService.getOwnedTask(user, id)));
    }

    @DeleteMapping("/{id}")
    public ApiV2Response<BackgroundTaskResponse> cancel(@AuthenticationPrincipal UserDetails userDetails,
                                                        @PathVariable Long id) {
        User user = userDetailsService.loadDomainUser(userDetails.getUsername());
        return ApiV2Response.success(toResponse(backgroundTaskCommandService.cancelOwnedTask(user, id)));
    }

    @PostMapping("/{id}/retry")
    public ApiV2Response<BackgroundTaskResponse> retry(@AuthenticationPrincipal UserDetails userDetails,
                                                       @PathVariable Long id) {
        User user = userDetailsService.loadDomainUser(userDetails.getUsername());
        return ApiV2Response.success(toResponse(backgroundTaskCommandService.retryOwnedTask(user, id)));
    }

    @PostMapping("/archive")
    public ApiV2Response<BackgroundTaskResponse> createArchiveTask(@AuthenticationPrincipal UserDetails userDetails,
                                                                   @Valid @RequestBody CreateBackgroundTaskRequest request) {
        return ApiV2Response.success(createTask(userDetails, BackgroundTaskType.ARCHIVE, request));
    }

    @PostMapping("/extract")
    public ApiV2Response<BackgroundTaskResponse> createExtractTask(@AuthenticationPrincipal UserDetails userDetails,
                                                                   @Valid @RequestBody CreateBackgroundTaskRequest request) {
        return ApiV2Response.success(createTask(userDetails, BackgroundTaskType.EXTRACT, request));
    }

    @PostMapping("/media-metadata")
    public ApiV2Response<BackgroundTaskResponse> createMediaMetadataTask(@AuthenticationPrincipal UserDetails userDetails,
                                                                          @Valid @RequestBody CreateBackgroundTaskRequest request) {
        return ApiV2Response.success(createTask(userDetails, BackgroundTaskType.MEDIA_META, request));
    }

    private BackgroundTaskResponse createTask(UserDetails userDetails,
                                              BackgroundTaskType type,
                                              CreateBackgroundTaskRequest request) {
        User user = userDetailsService.loadDomainUser(userDetails.getUsername());
        BackgroundTask task = backgroundTaskCommandService.createQueuedFileTask(
                user,
                type,
                request.fileId(),
                request.path(),
                request.correlationId()
        );
        return toResponse(task);
    }

    private BackgroundTaskResponse toResponse(BackgroundTask task) {
        return new BackgroundTaskResponse(
                task.getId(),
                task.getType(),
                task.getStatus(),
                task.getUserId(),
                task.getPublicStateJson(),
                task.getCorrelationId(),
                task.getErrorMessage(),
                task.getCreatedAt(),
                task.getUpdatedAt(),
                task.getFinishedAt()
        );
    }
}
