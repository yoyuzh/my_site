package com.yoyuzh.ops.admin.api;

import java.util.List;

public record AdminConfigSnapshotResponse(
        List<AdminConfigDefinitionResponse> fields
) {
}
