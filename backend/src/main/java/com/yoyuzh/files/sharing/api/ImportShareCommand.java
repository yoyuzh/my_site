package com.yoyuzh.files.sharing.api;

public record ImportShareCommand(
        String path,
        String password
) {
}
