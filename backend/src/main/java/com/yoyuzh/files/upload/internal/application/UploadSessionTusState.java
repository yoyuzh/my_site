package com.yoyuzh.files.upload.internal.application;

public record UploadSessionTusState(
        long uploadOffset,
        long uploadLength
) {
}
