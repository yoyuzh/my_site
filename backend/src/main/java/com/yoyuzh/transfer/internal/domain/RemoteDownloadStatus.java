package com.yoyuzh.transfer.internal.domain;

public enum RemoteDownloadStatus {
    PENDING,
    SUBMITTED,
    FETCHING_METADATA,
    AWAITING_FILE_SELECTION,
    DOWNLOADING,
    IMPORTING,
    COMPLETED,
    FAILED,
    CANCELED
}
