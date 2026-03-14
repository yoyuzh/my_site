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
}
