package com.yoyuzh.ops.admin.internal.web;

import com.yoyuzh.ops.admin.api.AdminConfigDefinitionResponse;
import com.yoyuzh.ops.admin.api.AdminConfigHistoryResponse;
import com.yoyuzh.ops.admin.api.AdminConfigSchemaApi;
import com.yoyuzh.ops.admin.api.AdminConfigSnapshotResponse;
import com.yoyuzh.ops.admin.api.AdminConfigUpdateRequest;
import com.yoyuzh.shared.kernel.ApiResponse;
import com.yoyuzh.shared.kernel.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestBody;

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

    @PatchMapping("/config/values/{key}")
    public ApiResponse<AdminConfigDefinitionResponse> updateValue(@PathVariable String key,
                                                                  @Valid @RequestBody AdminConfigUpdateRequest request) {
        return ApiResponse.success(adminConfigSchemaApi.updateValue(key, request));
    }

    @GetMapping("/config/values/{key}/history")
    public ApiResponse<PageResponse<AdminConfigHistoryResponse>> history(@PathVariable String key,
                                                                         @RequestParam(defaultValue = "0") int page,
                                                                         @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.success(adminConfigSchemaApi.history(key, page, size));
    }

    @PostMapping("/config/values/{key}/rollback/{version}")
    public ApiResponse<AdminConfigDefinitionResponse> rollback(@PathVariable String key,
                                                               @PathVariable long version) {
        return ApiResponse.success(adminConfigSchemaApi.rollback(key, version));
    }
}
