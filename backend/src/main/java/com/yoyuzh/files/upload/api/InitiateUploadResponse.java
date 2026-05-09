package com.yoyuzh.files.upload.api;

import java.util.Map;

public record InitiateUploadResponse(
        boolean direct,
        String uploadUrl,
        String method,
        Map<String, String> headers,
        String storageName
) {
}
