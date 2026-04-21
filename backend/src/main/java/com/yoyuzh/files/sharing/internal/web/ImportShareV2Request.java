package com.yoyuzh.files.sharing.internal.web;

import jakarta.validation.constraints.NotBlank;

public record ImportShareV2Request(
        @NotBlank String path,
        String password
) {
}
