package com.yoyuzh.boot.web.v2;

public class ApiV2Exception extends RuntimeException {

    private final ApiV2ErrorCode errorCode;

    public ApiV2Exception(ApiV2ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ApiV2ErrorCode getErrorCode() {
        return errorCode;
    }
}
