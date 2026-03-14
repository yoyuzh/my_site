package com.yoyuzh.cqu;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CquMockDataFactoryTest {

    @Test
    void shouldCreateMockScheduleForStudentAndSemester() {
        List<Map<String, Object>> result = CquMockDataFactory.createSchedule("2025-2026-1", "20230001");

        assertThat(result).isNotEmpty();
        assertThat(result.get(0)).containsEntry("courseName", "高级 Java 程序设计");
        assertThat(result.get(0)).containsEntry("semester", "2025-2026-1");
    }

    @Test
    void shouldCreateMockGradesForStudentAndSemester() {
        List<Map<String, Object>> result = CquMockDataFactory.createGrades("2025-2026-1", "20230001");

        assertThat(result).isNotEmpty();
        assertThat(result.get(0)).containsEntry("studentId", "20230001");
        assertThat(result.get(0)).containsKey("grade");
    }

    @Test
    void shouldReturnDifferentMockDataForDifferentStudents() {
        List<Map<String, Object>> firstSchedule = CquMockDataFactory.createSchedule("2025-2026-1", "2023123456");
        List<Map<String, Object>> secondSchedule = CquMockDataFactory.createSchedule("2025-2026-1", "2022456789");
        List<Map<String, Object>> firstGrades = CquMockDataFactory.createGrades("2025-2026-1", "2023123456");
        List<Map<String, Object>> secondGrades = CquMockDataFactory.createGrades("2025-2026-1", "2022456789");

        assertThat(firstSchedule).extracting(item -> item.get("courseName"))
                .isNotEqualTo(secondSchedule.stream().map(item -> item.get("courseName")).toList());
        assertThat(firstGrades).extracting(item -> item.get("grade"))
                .isNotEqualTo(secondGrades.stream().map(item -> item.get("grade")).toList());
    }
}
