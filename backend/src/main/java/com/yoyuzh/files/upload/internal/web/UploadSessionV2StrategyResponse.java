package com.yoyuzh.files.upload.internal.web;

import java.util.Map;

public record UploadSessionV2StrategyResponse(
        String prepareUrl,
        String proxyContentUrl,
        String partPrepareUrlTemplate,
        String partRecordUrlTemplate,
        String completeUrl,
        String proxyFormField,
        String tusUrl,
        Map<String, String> tusHeaders
) {
}
