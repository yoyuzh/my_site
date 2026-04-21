package com.yoyuzh.files.upload.internal.web;

import java.util.Map;

public record PreparedUploadV2Response(
        boolean direct,
        String uploadUrl,
        String method,
        Map<String, String> headers,
        String storageName
) {
}
