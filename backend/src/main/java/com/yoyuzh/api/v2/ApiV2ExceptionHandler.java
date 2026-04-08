package com.yoyuzh.api.v2;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "com.yoyuzh.api.v2")
public class ApiV2ExceptionHandler {

    @ExceptionHandler(ApiV2Exception.class)
    public ResponseEntity<ApiV2Response<Void>> handleApiV2Exception(ApiV2Exception ex) {
        ApiV2ErrorCode errorCode = ex.getErrorCode();
        return ResponseEntity
                .status(errorCode.getHttpStatus())
                .body(ApiV2Response.error(errorCode, ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiV2Response<Void>> handleUnknownException(Exception ex) {
        return ResponseEntity
                .status(ApiV2ErrorCode.INTERNAL_ERROR.getHttpStatus())
                .body(ApiV2Response.error(ApiV2ErrorCode.INTERNAL_ERROR, "服务器内部错误"));
    }
}
