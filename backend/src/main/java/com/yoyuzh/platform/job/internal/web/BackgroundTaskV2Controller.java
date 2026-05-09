package com.yoyuzh.platform.job.internal.web;

import com.yoyuzh.boot.web.v2.ApiV2Response;
import com.yoyuzh.boot.security.CustomUserDetailsService;
import com.yoyuzh.shared.kernel.PageResponse;
import com.yoyuzh.platform.job.api.BackgroundTaskResponse;
import com.yoyuzh.platform.job.api.BackgroundTaskLifecycleApi;
import com.yoyuzh.platform.job.api.TaskProgressResponse;
import com.yoyuzh.platform.job.api.BackgroundTaskType;
import com.yoyuzh.platform.job.api.BackgroundTaskView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
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

    private final BackgroundTaskLifecycleApi backgroundTaskLifecycleApi;
    private final CustomUserDetailsService userDetailsService;

    @GetMapping
    public ApiV2Response<PageResponse<BackgroundTaskResponse>> list(@AuthenticationPrincipal UserDetails userDetails,
                                                                    @RequestParam(defaultValue = "0") @Min(0) int page,
                                                                    @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        PageResponse<BackgroundTaskView> result = backgroundTaskLifecycleApi.listOwnedTasks(currentUserId(userDetails), page, size);
        return ApiV2Response.success(new PageResponse<>(result.items().stream().map(this::toResponse).toList(),
                result.total(),
                result.page(),
                result.size()));
    }

    @GetMapping("/{id}")
    public ApiV2Response<BackgroundTaskResponse> get(@AuthenticationPrincipal UserDetails userDetails,
                                                     @PathVariable Long id) {
        return ApiV2Response.success(toResponse(backgroundTaskLifecycleApi.getOwnedTask(currentUserId(userDetails), id)));
    }

    @GetMapping("/{id}/progress")
    public ApiV2Response<TaskProgressResponse> progress(@AuthenticationPrincipal UserDetails userDetails,
                                                        @PathVariable Long id) {
        return ApiV2Response.success(backgroundTaskLifecycleApi.getOwnedTaskProgress(currentUserId(userDetails), id));
    }

    @DeleteMapping("/{id}")
    public ApiV2Response<BackgroundTaskResponse> cancel(@AuthenticationPrincipal UserDetails userDetails,
                                                        @PathVariable Long id) {
        return ApiV2Response.success(toResponse(backgroundTaskLifecycleApi.cancelOwnedTask(currentUserId(userDetails), id)));
    }

    @PostMapping("/{id}/retry")
    public ApiV2Response<BackgroundTaskResponse> retry(@AuthenticationPrincipal UserDetails userDetails,
                                                       @PathVariable Long id) {
        return ApiV2Response.success(toResponse(backgroundTaskLifecycleApi.retryOwnedTask(currentUserId(userDetails), id)));
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

    @PostMapping("/search-index/rebuild")
    @PreAuthorize("@adminAccessEvaluator.isAdmin(authentication)")
    public ApiV2Response<BackgroundTaskResponse> rebuildSearchIndex(@AuthenticationPrincipal UserDetails userDetails) {
        return ApiV2Response.success(toResponse(backgroundTaskLifecycleApi.createSearchIndexRebuildTask(currentUserId(userDetails))));
    }

    private BackgroundTaskResponse createTask(UserDetails userDetails,
                                              BackgroundTaskType type,
                                              CreateBackgroundTaskRequest request) {
        BackgroundTaskView task = backgroundTaskLifecycleApi.createQueuedFileTask(
                currentUserId(userDetails),
                type,
                request.fileId(),
                request.path(),
                request.correlationId()
        );
        return toResponse(task);
    }

    private Long currentUserId(UserDetails userDetails) {
        return userDetailsService.loadUserId(userDetails.getUsername());
    }

    private BackgroundTaskResponse toResponse(BackgroundTaskView task) {
        return toResponse(
                task.id(),
                task.type(),
                task.status(),
                task.userId(),
                task.publicStateJson(),
                task.correlationId(),
                task.errorMessage(),
                task.createdAt(),
                task.updatedAt(),
                task.finishedAt()
        );
    }

    private BackgroundTaskResponse toResponse(Long id,
                                              BackgroundTaskType type,
                                              com.yoyuzh.platform.job.api.BackgroundTaskStatus status,
                                              Long userId,
                                              String publicStateJson,
                                              String correlationId,
                                              String errorMessage,
                                              java.time.LocalDateTime createdAt,
                                              java.time.LocalDateTime updatedAt,
                                              java.time.LocalDateTime finishedAt) {
        return new BackgroundTaskResponse(
                id,
                type,
                status,
                userId,
                publicStateJson,
                correlationId,
                errorMessage,
                createdAt,
                updatedAt,
                finishedAt
        );
    }
}
