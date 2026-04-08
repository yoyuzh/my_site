package com.yoyuzh.files.storage;

import java.util.Map;

public record PreparedUpload(
        boolean direct,
        String uploadUrl,
        String method,
        Map<String, String> headers,
        String storageName
) {
}
