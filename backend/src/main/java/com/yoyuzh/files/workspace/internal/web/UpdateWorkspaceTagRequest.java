package com.yoyuzh.files.workspace.internal.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateWorkspaceTagRequest(
        @NotBlank(message = "标签名称不能为空")
        @Size(max = 32, message = "标签名称不能超过32个字符")
        String name,

        @NotBlank(message = "标签颜色不能为空")
        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "标签颜色格式不正确")
        String color
) {
}
