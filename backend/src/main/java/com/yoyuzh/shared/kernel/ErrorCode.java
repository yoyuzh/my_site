package com.yoyuzh.shared.kernel;

public enum ErrorCode {
    UNKNOWN(1000),
    NOT_LOGGED_IN(1001),
    PERMISSION_DENIED(1002),
    FILE_NOT_FOUND(1003),
    INVALID_INPUT(1004),
    SESSION_EXPIRED(1005),
    QUOTA_EXCEEDED(1006),
    DUPLICATE_NAME(1007);

    private final int code;

    ErrorCode(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
