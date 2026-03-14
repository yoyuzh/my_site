package com.yoyuzh.common;

public enum ErrorCode {
    UNKNOWN(1000),
    NOT_LOGGED_IN(1001),
    PERMISSION_DENIED(1002),
    FILE_NOT_FOUND(1003);

    private final int code;

    ErrorCode(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
