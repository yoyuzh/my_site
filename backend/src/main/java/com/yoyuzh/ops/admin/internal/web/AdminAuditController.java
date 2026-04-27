package com.yoyuzh.ops.admin.internal.web;

import com.yoyuzh.ops.admin.internal.application.AdminAuditLogResponse;
import com.yoyuzh.ops.admin.internal.application.AdminAuditQueryService;
import com.yoyuzh.shared.kernel.ApiResponse;
import com.yoyuzh.shared.kernel.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminAuditController {

    private final AdminAuditQueryService adminAuditQueryService;

    @GetMapping("/audits")
    public ApiResponse<PageResponse<AdminAuditLogResponse>> audits(@RequestParam(defaultValue = "0") int page,
                                                                   @RequestParam(defaultValue = "10") int size,
                                                                   @RequestParam(defaultValue = "") String actorQuery,
                                                                   @RequestParam(defaultValue = "") String actionType,
                                                                   @RequestParam(defaultValue = "") String targetType,
                                                                   @RequestParam(required = false) Long targetId) {
        return ApiResponse.success(adminAuditQueryService.listAuditLogs(
                page,
                size,
                actorQuery,
                actionType,
                targetType,
                targetId
        ));
    }
}
