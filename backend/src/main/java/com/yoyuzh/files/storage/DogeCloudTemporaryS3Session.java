package com.yoyuzh.files.storage;

import java.time.Instant;

record DogeCloudTemporaryS3Session(
        String bucket,
        String endpoint,
        String accessKeyId,
        String secretAccessKey,
        String sessionToken,
        Instant expiresAt
) {
}
