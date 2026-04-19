package com.yoyuzh.infra.cache;

import java.util.Set;

public final class RedisCacheNames {

    public static final String FILES_LIST = "files:list";
    public static final String ADMIN_SUMMARY = "admin:summary";
    public static final String STORAGE_POLICIES = "admin:storage-policies";
    public static final String ANDROID_RELEASE = "android:release";

    public static final Set<String> ALL = Set.of(
            FILES_LIST,
            ADMIN_SUMMARY,
            STORAGE_POLICIES,
            ANDROID_RELEASE
    );

    private RedisCacheNames() {
    }
}
