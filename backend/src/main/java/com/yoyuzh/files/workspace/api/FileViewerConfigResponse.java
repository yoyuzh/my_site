package com.yoyuzh.files.workspace.api;

import java.util.List;
import java.util.Map;

public record FileViewerConfigResponse(
        List<FileViewerDefinition> fileViewers,
        Map<String, String> defaultViewerMapping
) {
}
