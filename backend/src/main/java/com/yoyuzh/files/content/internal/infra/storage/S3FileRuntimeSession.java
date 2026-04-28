package com.yoyuzh.files.content.internal.infra.storage;

import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

record S3FileRuntimeSession(
        String bucket,
        S3Client s3Client,
        S3Presigner s3Presigner
) {
}
