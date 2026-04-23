package com.yoyuzh.files.workspace.api;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record BatchFileOperationRequest(
        @NotEmpty List<Long> fileIds,
        String targetPath
) {
}
