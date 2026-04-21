package com.yoyuzh.files.upload.internal.web;

public record UploadSessionV2StrategyResponse(
        String prepareUrl,
        String proxyContentUrl,
        String partPrepareUrlTemplate,
        String partRecordUrlTemplate,
        String completeUrl,
        String proxyFormField
) {
}
