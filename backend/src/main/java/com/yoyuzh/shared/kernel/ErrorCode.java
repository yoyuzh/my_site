package com.yoyuzh.shared.kernel;

public enum ErrorCode {
    UNKNOWN(1000),
    NOT_LOGGED_IN(1001),
    PERMISSION_DENIED(1002),
    FILE_NOT_FOUND(1003),
    INVALID_INPUT(1004),
    SESSION_EXPIRED(1005),
    QUOTA_EXCEEDED(1006),
    DUPLICATE_NAME(1007),
    SHARE_NOT_FOUND(1008),
    TASK_NOT_FOUND(1009),
    STORAGE_POLICY_NOT_FOUND(1010),
    ARCHIVE_READ_FAILED(1011),
    SERVICE_UNAVAILABLE(1012);

    private final int code;

    ErrorCode(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
