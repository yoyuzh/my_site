package com.yoyuzh.ops.admin.internal.web;

import com.yoyuzh.ops.admin.internal.application.AdminConfigSnapshotService;
import com.yoyuzh.ops.admin.internal.application.AdminFilesystemResponse;
import com.yoyuzh.ops.admin.internal.application.AdminInspectionQueryService;
import com.yoyuzh.ops.admin.internal.application.AdminSummaryResponse;
import com.yoyuzh.shared.kernel.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("@adminAccessEvaluator.isAdmin(authentication)")
public class AdminOverviewController {

    private final AdminInspectionQueryService adminInspectionQueryService;
    private final AdminConfigSnapshotService adminConfigSnapshotService;

    @GetMapping("/summary")
    public ApiResponse<AdminSummaryResponse> summary() {
        return ApiResponse.success(adminInspectionQueryService.getSummary());
    }

    @GetMapping("/filesystem")
    public ApiResponse<AdminFilesystemResponse> filesystem() {
        return ApiResponse.success(adminConfigSnapshotService.getFilesystem());
    }
}
