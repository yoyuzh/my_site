package com.yoyuzh.admin;

public record AdminSchoolSnapshotResponse(
        Long id,
        Long userId,
        String username,
        String email,
        String studentId,
        String semester,
        long scheduleCount,
        long gradeCount
) {
}
