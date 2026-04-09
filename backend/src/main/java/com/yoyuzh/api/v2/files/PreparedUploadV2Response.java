package com.yoyuzh.api.v2.files;

import java.util.Map;

public record PreparedUploadV2Response(
        boolean direct,
        String uploadUrl,
        String method,
        Map<String, String> headers,
        String storageName
) {
}
