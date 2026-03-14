package com.yoyuzh.cqu;

public record CourseResponse(
        String courseName,
        String teacher,
        String classroom,
        Integer dayOfWeek,
        Integer startTime,
        Integer endTime
) {
}
