package com.yoyuzh.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminRegistrationInviteCodeUpdateRequest(
        @NotBlank(message = "邀请码不能为空")
        @Size(max = 64, message = "邀请码长度不能超过 64 个字符")
        String inviteCode
) {
}
