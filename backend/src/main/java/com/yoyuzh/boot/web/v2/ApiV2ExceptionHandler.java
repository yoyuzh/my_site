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
        "com.yoyuzh.files.upload.internal.web",
        "com.yoyuzh.files.search.internal.web",
        "com.yoyuzh.files.sharing.internal.web",
        "com.yoyuzh.platform.job.internal.web",
        "com.yoyuzh.boot.web"
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
        return switch (errorCode) {
            case NOT_LOGGED_IN -> ApiV2ErrorCode.NOT_LOGGED_IN;
            case PERMISSION_DENIED -> ApiV2ErrorCode.PERMISSION_DENIED;
            case FILE_NOT_FOUND -> ApiV2ErrorCode.FILE_NOT_FOUND;
            case UNKNOWN -> ApiV2ErrorCode.BAD_REQUEST;
        };
    }
}
