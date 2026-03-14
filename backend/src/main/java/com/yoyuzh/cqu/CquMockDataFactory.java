package com.yoyuzh.cqu;

import java.util.List;
import java.util.Map;

public final class CquMockDataFactory {

    private CquMockDataFactory() {
    }

    public static List<Map<String, Object>> createSchedule(String semester, String studentId) {
        return List.of(
                Map.of(
                        "studentId", studentId,
                        "semester", semester,
                        "courseName", "高级 Java 程序设计",
                        "teacher", "李老师",
                        "classroom", "D1131",
                        "dayOfWeek", 1,
                        "startTime", 1,
                        "endTime", 2
                ),
                Map.of(
                        "studentId", studentId,
                        "semester", semester,
                        "courseName", "计算机网络",
                        "teacher", "王老师",
                        "classroom", "A2204",
                        "dayOfWeek", 3,
                        "startTime", 3,
                        "endTime", 4
                ),
                Map.of(
                        "studentId", studentId,
                        "semester", semester,
                        "courseName", "软件工程",
                        "teacher", "周老师",
                        "classroom", "B3102",
                        "dayOfWeek", 5,
                        "startTime", 5,
                        "endTime", 6
                )
        );
    }

    public static List<Map<String, Object>> createGrades(String semester, String studentId) {
        return List.of(
                Map.of(
                        "studentId", studentId,
                        "semester", semester,
                        "courseName", "高级 Java 程序设计",
                        "grade", 92.0
                ),
                Map.of(
                        "studentId", studentId,
                        "semester", semester,
                        "courseName", "计算机网络",
                        "grade", 88.5
                ),
                Map.of(
                        "studentId", studentId,
                        "semester", semester,
                        "courseName", "软件工程",
                        "grade", 90.0
                )
        );
    }
}
