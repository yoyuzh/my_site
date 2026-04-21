package com.yoyuzh.boot.web.v2;

public record ApiV2Response<T>(int code, String msg, T data) {

    public static <T> ApiV2Response<T> success(T data) {
        return new ApiV2Response<>(0, "success", data);
    }

    public static ApiV2Response<Void> error(ApiV2ErrorCode errorCode, String msg) {
        return new ApiV2Response<>(errorCode.getCode(), msg, null);
    }
}
