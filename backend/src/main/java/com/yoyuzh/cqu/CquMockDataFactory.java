package com.yoyuzh.cqu;

import java.util.List;
import java.util.Map;

public final class CquMockDataFactory {

    private CquMockDataFactory() {
    }

    public static List<Map<String, Object>> createSchedule(String semester, String studentId) {
        StudentProfile profile = StudentProfile.fromStudentId(studentId);
        return profile.schedule().stream()
                .map(item -> Map.<String, Object>of(
                        "studentId", studentId,
                        "semester", semester,
                        "courseName", item.courseName(),
                        "teacher", item.teacher(),
                        "classroom", item.classroom(),
                        "dayOfWeek", item.dayOfWeek(),
                        "startTime", item.startTime(),
                        "endTime", item.endTime()
                ))
                .toList();
    }

    public static List<Map<String, Object>> createGrades(String semester, String studentId) {
        StudentProfile profile = StudentProfile.fromStudentId(studentId);
        return profile.grades().stream()
                .map(item -> Map.<String, Object>of(
                        "studentId", studentId,
                        "semester", semester,
                        "courseName", item.courseName(),
                        "grade", item.score()
                ))
                .toList();
    }

    private record ScheduleEntry(
            String courseName,
            String teacher,
            String classroom,
            Integer dayOfWeek,
            Integer startTime,
            Integer endTime
    ) {
    }

    private record GradeEntry(String courseName, Double score) {
    }

    private record StudentProfile(
            List<ScheduleEntry> schedule,
            List<GradeEntry> grades
    ) {
        private static StudentProfile fromStudentId(String studentId) {
            return switch (studentId) {
                case "2023123456" -> new StudentProfile(
                        List.of(
                                new ScheduleEntry("高级 Java 程序设计", "李老师", "D1131", 1, 1, 2),
                                new ScheduleEntry("计算机网络", "王老师", "A2204", 3, 3, 4),
                                new ScheduleEntry("软件工程", "周老师", "B3102", 5, 5, 6)
                        ),
                        List.of(
                                new GradeEntry("高级 Java 程序设计", 92.0),
                                new GradeEntry("计算机网络", 88.5),
                                new GradeEntry("软件工程", 90.0)
                        )
                );
                case "2022456789" -> new StudentProfile(
                        List.of(
                                new ScheduleEntry("数据挖掘", "陈老师", "A1408", 2, 1, 2),
                                new ScheduleEntry("机器学习基础", "赵老师", "B2201", 4, 3, 4),
                                new ScheduleEntry("信息检索", "孙老师", "C1205", 5, 7, 8)
                        ),
                        List.of(
                                new GradeEntry("数据挖掘", 94.0),
                                new GradeEntry("机器学习基础", 91.0),
                                new GradeEntry("信息检索", 89.0)
                        )
                );
                case "2021789012" -> new StudentProfile(
                        List.of(
                                new ScheduleEntry("交互设计", "刘老师", "艺设楼201", 1, 3, 4),
                                new ScheduleEntry("视觉传达专题", "黄老师", "艺设楼305", 3, 5, 6),
                                new ScheduleEntry("数字媒体项目实践", "许老师", "创意工坊101", 4, 7, 8)
                        ),
                        List.of(
                                new GradeEntry("交互设计", 96.0),
                                new GradeEntry("视觉传达专题", 93.0),
                                new GradeEntry("数字媒体项目实践", 97.0)
                        )
                );
                default -> new StudentProfile(
                        List.of(
                                new ScheduleEntry("高级 Java 程序设计", "李老师", "D1131", 1, 1, 2),
                                new ScheduleEntry("计算机网络", "王老师", "A2204", 3, 3, 4),
                                new ScheduleEntry("软件工程", "周老师", "B3102", 5, 5, 6)
                        ),
                        List.of(
                                new GradeEntry("高级 Java 程序设计", 92.0),
                                new GradeEntry("计算机网络", 88.5),
                                new GradeEntry("软件工程", 90.0)
                        )
                );
            };
        }
    }
}
