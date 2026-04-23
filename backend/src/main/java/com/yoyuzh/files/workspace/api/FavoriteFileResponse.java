package com.yoyuzh.files.workspace.api;

public record FavoriteFileResponse(
        Long fileId,
        boolean favorite
) {
}
