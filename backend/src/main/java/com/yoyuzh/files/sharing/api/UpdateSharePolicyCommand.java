package com.yoyuzh.files.sharing.api;

import java.time.LocalDateTime;

public record UpdateSharePolicyCommand(
        String password,
        LocalDateTime expiresAt,
        Integer maxDownloads,
        Boolean expireAfterConsume
) {
}
