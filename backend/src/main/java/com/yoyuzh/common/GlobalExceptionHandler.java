package com.yoyuzh.common;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Objects;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException ex) {
        HttpStatus status = switch (ex.getErrorCode()) {
            case NOT_LOGGED_IN -> HttpStatus.UNAUTHORIZED;
            case PERMISSION_DENIED -> HttpStatus.FORBIDDEN;
            case FILE_NOT_FOUND -> HttpStatus.NOT_FOUND;
            default -> HttpStatus.BAD_REQUEST;
        };
        return ResponseEntity.status(status).body(ApiResponse.error(ex.getErrorCode(), ex.getMessage()));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, ConstraintViolationException.class})
    public ResponseEntity<ApiResponse<Void>> handleValidationException(Exception ex) {
        if (ex instanceof MethodArgumentNotValidException validationException) {
            String message = validationException.getBindingResult().getAllErrors().stream()
                    .map(ObjectError::getDefaultMessage)
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(msg -> !msg.isEmpty())
                    .findFirst()
                    .orElse("请求参数不合法");
            return ResponseEntity.badRequest().body(ApiResponse.error(ErrorCode.UNKNOWN, message));
        }
        if (ex instanceof ConstraintViolationException validationException) {
            String message = validationException.getConstraintViolations().stream()
                    .map(ConstraintViolation::getMessage)
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(msg -> !msg.isEmpty())
                    .findFirst()
                    .orElse("请求参数不合法");
            return ResponseEntity.badRequest().body(ApiResponse.error(ErrorCode.UNKNOWN, message));
        }
        return ResponseEntity.badRequest().body(ApiResponse.error(ErrorCode.UNKNOWN, "请求参数不合法"));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error(ErrorCode.PERMISSION_DENIED, "没有权限访问该资源"));
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadCredentials(BadCredentialsException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error(ErrorCode.NOT_LOGGED_IN, "用户名或密码错误"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnknown(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(ErrorCode.UNKNOWN, "服务器内部错误"));
    }
}
