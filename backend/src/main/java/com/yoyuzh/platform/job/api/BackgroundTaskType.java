package com.yoyuzh.platform.job.api;

public enum BackgroundTaskType {
    ARCHIVE,
    EXTRACT,
    SEARCH_INDEX_REBUILD,
    STORAGE_POLICY_MIGRATION,
    THUMBNAIL,
    MEDIA_META,
    WORKSPACE_MUTATION,
    BLOB_UPLOAD,
    REMOTE_DOWNLOAD,
    HLS_TRANSCODE,
    CLEANUP
}
