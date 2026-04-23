package com.yoyuzh.transfer.internal.domain;

import com.yoyuzh.shared.kernel.BusinessException;
import com.yoyuzh.shared.kernel.ErrorCode;

import java.util.Locale;
import java.util.Objects;

public enum TransferRole {
    SENDER,
    RECEIVER;

    public static TransferRole from(String role) {
        String normalized = Objects.requireNonNullElse(role, "").trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "sender" -> SENDER;
            case "receiver" -> RECEIVER;
            default -> throw new BusinessException(ErrorCode.UNKNOWN, "不支持的传输角色");
        };
    }
}
