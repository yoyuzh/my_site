package com.yoyuzh.transfer;

import com.yoyuzh.common.BusinessException;
import com.yoyuzh.common.ErrorCode;

import java.util.Locale;
import java.util.Objects;

enum TransferRole {
    SENDER,
    RECEIVER;

    static TransferRole from(String role) {
        String normalized = Objects.requireNonNullElse(role, "").trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "sender" -> SENDER;
            case "receiver" -> RECEIVER;
            default -> throw new BusinessException(ErrorCode.UNKNOWN, "不支持的传输角色");
        };
    }
}
