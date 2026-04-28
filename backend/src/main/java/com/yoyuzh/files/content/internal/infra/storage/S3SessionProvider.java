package com.yoyuzh.files.content.internal.infra.storage;

@FunctionalInterface
interface S3SessionProvider extends AutoCloseable {

    S3FileRuntimeSession currentSession();

    @Override
    default void close() {
    }
}
