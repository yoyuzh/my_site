package com.yoyuzh.api.v2.shares;

import jakarta.validation.constraints.NotBlank;

public record ImportShareV2Request(
        @NotBlank String path,
        String password
) {
}
