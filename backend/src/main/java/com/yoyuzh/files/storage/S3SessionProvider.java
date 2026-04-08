package com.yoyuzh.files.storage;

@FunctionalInterface
interface S3SessionProvider extends AutoCloseable {

    S3FileRuntimeSession currentSession();

    @Override
    default void close() {
    }
}
