package com.yoyuzh.files.workspace.api;

import java.util.List;
import java.util.Map;

public record FileViewerDefinition(
        String id,
        String type,
        String displayName,
        String icon,
        List<String> extensions,
        Long maxSizeBytes,
        boolean openInNew,
        boolean recommended,
        List<FileViewerTemplate> templates,
        Map<String, Object> props
) {
}
