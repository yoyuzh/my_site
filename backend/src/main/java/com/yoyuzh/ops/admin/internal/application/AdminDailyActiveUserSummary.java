package com.yoyuzh.ops.admin.internal.application;

import java.time.LocalDate;
import java.util.List;

public record AdminDailyActiveUserSummary(
        LocalDate metricDate,
        String label,
        long userCount,
        List<String> usernames
) {
}
