package com.yoyuzh.admin;

import com.yoyuzh.common.ApiResponse;
import com.yoyuzh.common.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("@adminAccessEvaluator.isAdmin(authentication)")
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
