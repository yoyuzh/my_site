package com.yoyuzh.files.sharing.api;

import java.time.LocalDateTime;

public record SavedShareV2Response(
        Long id,
        LocalDateTime savedAt,
        ShareV2Response share
) {
}
