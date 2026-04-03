package com.yoyuzh.admin;

import java.time.LocalDate;
import java.util.List;

public record AdminDailyActiveUserSummary(
        LocalDate metricDate,
        String label,
        long userCount,
        List<String> usernames
) {
}
