package com.yoyuzh.admin;

import com.yoyuzh.common.ApiResponse;
import com.yoyuzh.common.PageResponse;
import com.yoyuzh.files.tasks.BackgroundTaskFailureCategory;
import com.yoyuzh.files.tasks.BackgroundTaskStatus;
import com.yoyuzh.files.tasks.BackgroundTaskType;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("@adminAccessEvaluator.isAdmin(authentication)")
public class AdminTaskController {

    private final AdminTaskQueryService adminTaskQueryService;

    @GetMapping("/tasks")
    public ApiResponse<PageResponse<AdminTaskResponse>> tasks(@RequestParam(defaultValue = "0") int page,
                                                              @RequestParam(defaultValue = "10") int size,
                                                              @RequestParam(defaultValue = "") String userQuery,
                                                              @RequestParam(required = false) BackgroundTaskType type,
                                                              @RequestParam(required = false) BackgroundTaskStatus status,
                                                              @RequestParam(required = false) BackgroundTaskFailureCategory failureCategory,
                                                              @RequestParam(required = false) AdminTaskLeaseState leaseState) {
        return ApiResponse.success(adminTaskQueryService.listTasks(page, size, userQuery, type, status, failureCategory, leaseState));
    }

    @GetMapping("/tasks/{taskId}")
    public ApiResponse<AdminTaskResponse> task(@PathVariable Long taskId) {
        return ApiResponse.success(adminTaskQueryService.getTask(taskId));
    }
}
