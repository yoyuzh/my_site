package com.yoyuzh.ops.admin.api;

import java.util.List;

public interface AdminConfigSchemaApi {

    List<AdminConfigDefinitionResponse> definitions();

    AdminConfigSnapshotResponse snapshot();
}
