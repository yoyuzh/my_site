package com.yoyuzh.files;

import java.util.Map;

public record InitiateUploadResponse(
        boolean direct,
        String uploadUrl,
        String method,
        Map<String, String> headers,
        String storageName
) {
}
