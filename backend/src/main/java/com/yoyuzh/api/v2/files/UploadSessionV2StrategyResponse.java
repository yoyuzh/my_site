package com.yoyuzh.api.v2.files;

public record UploadSessionV2StrategyResponse(
        String prepareUrl,
        String proxyContentUrl,
        String partPrepareUrlTemplate,
        String partRecordUrlTemplate,
        String completeUrl,
        String proxyFormField
) {
}
