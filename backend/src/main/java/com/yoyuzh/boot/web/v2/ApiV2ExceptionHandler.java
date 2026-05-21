package com.yoyuzh.boot.web.v2;

import com.yoyuzh.shared.kernel.BusinessException;
import com.yoyuzh.shared.kernel.ErrorCode;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackages = {
        "com.yoyuzh.boot.web",
        "com.yoyuzh.files.upload.internal.web",
        "com.yoyuzh.files.search.internal.web",
        "com.yoyuzh.files.content.internal.web",
        "com.yoyuzh.files.sharing.internal.web",
        "com.yoyuzh.platform.job.internal.web"
})
public class ApiV2ExceptionHandler {

    @ExceptionHandler(ApiV2Exception.class)
    public ResponseEntity<ApiV2Response<Void>> handleApiV2Exception(ApiV2Exception ex) {
        ApiV2ErrorCode errorCode = ex.getErrorCode();
        return ResponseEntity
                .status(errorCode.getHttpStatus())
                .body(ApiV2Response.error(errorCode, ex.getMessage()));
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiV2Response<Void>> handleBusinessException(BusinessException ex) {
        ApiV2ErrorCode errorCode = mapBusinessErrorCode(ex.getErrorCode());
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

    private ApiV2ErrorCode mapBusinessErrorCode(ErrorCode errorCode) {
        if (errorCode == ErrorCode.NOT_LOGGED_IN) {
            return ApiV2ErrorCode.NOT_LOGGED_IN;
        }
        if (errorCode == ErrorCode.PERMISSION_DENIED) {
            return ApiV2ErrorCode.PERMISSION_DENIED;
        }
        if (errorCode == ErrorCode.FILE_NOT_FOUND
                || errorCode == ErrorCode.TASK_NOT_FOUND
                || errorCode == ErrorCode.STORAGE_POLICY_NOT_FOUND) {
            return ApiV2ErrorCode.FILE_NOT_FOUND;
        }
        if (errorCode == ErrorCode.SESSION_EXPIRED) {
            return ApiV2ErrorCode.SESSION_EXPIRED;
        }
        if (errorCode == ErrorCode.INVALID_INPUT) {
            return ApiV2ErrorCode.INVALID_INPUT;
        }
        if (errorCode == ErrorCode.QUOTA_EXCEEDED) {
            return ApiV2ErrorCode.QUOTA_EXCEEDED;
        }
        if (errorCode == ErrorCode.DUPLICATE_NAME) {
            return ApiV2ErrorCode.DUPLICATE_NAME;
        }
        if (errorCode == ErrorCode.SERVICE_UNAVAILABLE) {
            return ApiV2ErrorCode.SERVICE_UNAVAILABLE;
        }
        return ApiV2ErrorCode.BAD_REQUEST;
    }
}
