package com.yoyuzh.ops.admin.internal.application.config;

import com.yoyuzh.ops.admin.api.AdminConfigDefinitionResponse;
import com.yoyuzh.ops.admin.api.AdminConfigSchemaApi;
import com.yoyuzh.ops.admin.api.AdminConfigSnapshotResponse;
import com.yoyuzh.ops.admin.api.AdminSettingsResponse;
import com.yoyuzh.ops.admin.internal.application.AdminConfigSnapshotService;
import com.yoyuzh.ops.admin.internal.application.AdminRuntimeSettingsDefaults;
import com.yoyuzh.ops.admin.internal.application.AdminRuntimeSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RuntimeAdminConfigSchemaApi implements AdminConfigSchemaApi {

    private final AdminConfigRegistry adminConfigRegistry;
    private final AdminConfigSnapshotService adminConfigSnapshotService;
    private final AdminRuntimeSettingsDefaults adminRuntimeSettingsDefaults;

    @Override
    public List<AdminConfigDefinitionResponse> definitions() {
        return materializeResponses();
    }

    @Override
    public AdminConfigSnapshotResponse snapshot() {
        return new AdminConfigSnapshotResponse(materializeResponses());
    }

    private List<AdminConfigDefinitionResponse> materializeResponses() {
        AdminRuntimeSettingsService.State defaults = adminRuntimeSettingsDefaults.create();
        AdminSettingsResponse settings = adminConfigSnapshotService.getSettings();
        return adminConfigRegistry.definitions().stream()
                .map(definition -> definition.toResponse(defaults, settings))
                .toList();
    }
}
