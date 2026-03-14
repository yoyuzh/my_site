package com.yoyuzh.cqu;

import com.yoyuzh.auth.User;
import com.yoyuzh.config.CquApiProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CquDataServiceTest {

    @Mock
    private CquApiClient cquApiClient;

    @Mock
    private CourseRepository courseRepository;

    @Mock
    private GradeRepository gradeRepository;

    @InjectMocks
    private CquDataService cquDataService;

    @Test
    void shouldNormalizeScheduleFromRemoteApi() {
        CquApiProperties properties = new CquApiProperties();
        properties.setRequireLogin(false);
        cquDataService = new CquDataService(cquApiClient, courseRepository, gradeRepository, properties);
        when(cquApiClient.fetchSchedule("2025-2026-1", "20230001")).thenReturn(List.of(Map.of(
                "courseName", "Java",
                "teacher", "Zhang",
                "classroom", "A101",
                "dayOfWeek", 1,
                "startTime", 1,
                "endTime", 2
        )));

        List<CourseResponse> response = cquDataService.getSchedule(null, "2025-2026-1", "20230001");

        assertThat(response).hasSize(1);
        assertThat(response.get(0).courseName()).isEqualTo("Java");
        assertThat(response.get(0).teacher()).isEqualTo("Zhang");
    }

    @Test
    void shouldPersistGradesForLoggedInUserWhenAvailable() {
        CquApiProperties properties = new CquApiProperties();
        properties.setRequireLogin(true);
        cquDataService = new CquDataService(cquApiClient, courseRepository, gradeRepository, properties);
        User user = new User();
        user.setId(1L);
        user.setUsername("alice");
        user.setEmail("alice@example.com");
        user.setPasswordHash("encoded");
        user.setCreatedAt(LocalDateTime.now());
        when(cquApiClient.fetchGrades("2025-2026-1", "20230001")).thenReturn(List.of(Map.of(
                "courseName", "Java",
                "grade", 95,
                "semester", "2025-2026-1"
        )));
        Grade persisted = new Grade();
        persisted.setUser(user);
        persisted.setCourseName("Java");
        persisted.setGrade(95D);
        persisted.setSemester("2025-2026-1");
        persisted.setStudentId("20230001");
        when(gradeRepository.saveAll(anyList())).thenReturn(List.of(persisted));
        when(gradeRepository.findByUserIdAndStudentIdOrderBySemesterAscGradeDesc(1L, "20230001"))
                .thenReturn(List.of(persisted));

        List<GradeResponse> response = cquDataService.getGrades(user, "2025-2026-1", "20230001");

        assertThat(response).hasSize(1);
        assertThat(response.get(0).grade()).isEqualTo(95D);
    }
}
