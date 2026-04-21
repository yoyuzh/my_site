package com.yoyuzh.files.sharing.internal.web;

import jakarta.validation.constraints.NotBlank;

public record VerifySharePasswordV2Request(
        @NotBlank String password
) {
}
