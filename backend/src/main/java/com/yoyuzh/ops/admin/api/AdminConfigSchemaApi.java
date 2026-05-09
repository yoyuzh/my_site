package com.yoyuzh.ops.admin.api;

import com.yoyuzh.shared.kernel.PageResponse;

import java.util.List;

public interface AdminConfigSchemaApi {

    List<AdminConfigDefinitionResponse> definitions();

    AdminConfigSnapshotResponse snapshot();

    AdminConfigDefinitionResponse updateValue(String key, AdminConfigUpdateRequest request);

    PageResponse<AdminConfigHistoryResponse> history(String key, int page, int size);

    AdminConfigDefinitionResponse rollback(String key, long version);
}
