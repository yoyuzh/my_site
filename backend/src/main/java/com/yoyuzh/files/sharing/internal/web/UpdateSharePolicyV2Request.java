package com.yoyuzh.files.sharing.internal.web;

import java.time.LocalDateTime;

import jakarta.validation.constraints.Positive;

public record UpdateSharePolicyV2Request(
        String password,
        LocalDateTime expiresAt,
        @Positive Integer maxDownloads,
        Boolean expireAfterConsume
) {
}
