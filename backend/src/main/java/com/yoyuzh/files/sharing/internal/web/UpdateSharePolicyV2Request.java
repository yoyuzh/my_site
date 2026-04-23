package com.yoyuzh.files.sharing.internal.web;

import jakarta.validation.constraints.Positive;

public record UpdateSharePolicyV2Request(
        @Positive Integer maxDownloads
) {
}
