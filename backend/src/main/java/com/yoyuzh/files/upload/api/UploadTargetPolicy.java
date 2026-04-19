package com.yoyuzh.files.upload.api;

import com.yoyuzh.auth.User;

public interface UploadTargetPolicy {

    ValidatedUploadTarget validateUpload(User user, String path, String filename, long size);
}
