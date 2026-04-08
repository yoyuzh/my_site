package com.yoyuzh.api.v2.shares;

import jakarta.validation.constraints.NotBlank;

public record VerifySharePasswordV2Request(
        @NotBlank String password
) {
}
