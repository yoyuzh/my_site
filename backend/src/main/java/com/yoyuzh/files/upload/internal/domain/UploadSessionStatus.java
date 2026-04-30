package com.yoyuzh.files.upload.internal.domain;

public enum UploadSessionStatus {
    CREATED,
    UPLOADING,
    COMPLETING,
    COMPLETED,
    CANCELLED,
    EXPIRED,
    FAILED
}
