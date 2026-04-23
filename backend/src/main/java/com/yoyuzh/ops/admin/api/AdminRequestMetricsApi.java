package com.yoyuzh.ops.admin.api;

public interface AdminRequestMetricsApi {

    void recordUserOnline(Long userId, String username);

    void incrementRequestCount();
}
