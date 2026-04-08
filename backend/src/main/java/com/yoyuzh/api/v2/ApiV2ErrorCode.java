package com.yoyuzh.api.v2;

import org.springframework.http.HttpStatus;

public enum ApiV2ErrorCode {
    FILE_NOT_FOUND(2404, HttpStatus.NOT_FOUND),
    INTERNAL_ERROR(2500, HttpStatus.INTERNAL_SERVER_ERROR);

    private final int code;
    private final HttpStatus httpStatus;

    ApiV2ErrorCode(int code, HttpStatus httpStatus) {
        this.code = code;
        this.httpStatus = httpStatus;
    }

    public int getCode() {
        return code;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }
}
