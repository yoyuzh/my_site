package com.yoyuzh.ops.admin.internal.web;

import com.yoyuzh.ops.admin.api.AdminConfigDefinitionResponse;
import com.yoyuzh.ops.admin.api.AdminConfigSchemaApi;
import com.yoyuzh.ops.admin.api.AdminConfigSnapshotResponse;
import com.yoyuzh.shared.kernel.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminConfigController {

    private final AdminConfigSchemaApi adminConfigSchemaApi;

    @GetMapping("/config/definitions")
    public ApiResponse<List<AdminConfigDefinitionResponse>> definitions() {
        return ApiResponse.success(adminConfigSchemaApi.definitions());
    }

    @GetMapping("/config/snapshot")
    public ApiResponse<AdminConfigSnapshotResponse> snapshot() {
        return ApiResponse.success(adminConfigSchemaApi.snapshot());
    }
}
