package com.yoyuzh.platform.job.internal.application;

public final class BlobUploadTaskState {

    public static final String MODE = "mode";
    public static final String CREATE = "CREATE";
    public static final String REPLACE = "REPLACE";
    public static final String BLOB_ID = "blobId";
    public static final String OBJECT_KEY = "objectKey";
    public static final String LOCAL_TEMP_PATH = "localTempPath";
    public static final String CONTENT_TYPE = "contentType";
    public static final String SIZE = "size";
    public static final String FILE_ID = "fileId";
    public static final String PATH = "path";
    public static final String FILENAME = "filename";
    public static final String TARGET_FILE_ID = "targetFileId";
    public static final String OLD_BLOB_ID = "oldBlobId";
    public static final String OLD_PRIMARY_ENTITY_ID = "oldPrimaryEntityId";

    private BlobUploadTaskState() {
    }
}
