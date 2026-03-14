package com.yoyuzh.cqu;

import com.yoyuzh.auth.User;
import com.yoyuzh.auth.UserRepository;
import com.yoyuzh.config.CquApiProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CquDataServiceTest {

    @Mock
    private CquApiClient cquApiClient;

    @Mock
    private CourseRepository courseRepository;

    @Mock
    private GradeRepository gradeRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private CquDataService cquDataService;

    @Test
    void shouldNormalizeScheduleFromRemoteApi() {
        CquApiProperties properties = new CquApiProperties();
        properties.setRequireLogin(false);
        cquDataService = new CquDataService(cquApiClient, courseRepository, gradeRepository, userRepository, properties);
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
        cquDataService = new CquDataService(cquApiClient, courseRepository, gradeRepository, userRepository, properties);
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

    @Test
    void shouldReturnPersistedScheduleWithoutCallingRemoteApiWhenRefreshIsDisabled() {
        CquApiProperties properties = new CquApiProperties();
        properties.setRequireLogin(true);
        cquDataService = new CquDataService(cquApiClient, courseRepository, gradeRepository, userRepository, properties);

        User user = new User();
        user.setId(1L);
        user.setUsername("alice");

        Course persisted = new Course();
        persisted.setUser(user);
        persisted.setCourseName("Java");
        persisted.setTeacher("Zhang");
        persisted.setClassroom("A101");
        persisted.setDayOfWeek(1);
        persisted.setStartTime(1);
        persisted.setEndTime(2);
        persisted.setSemester("2025-spring");
        persisted.setStudentId("20230001");

        when(courseRepository.findByUserIdAndStudentIdAndSemesterOrderByDayOfWeekAscStartTimeAsc(1L, "20230001", "2025-spring"))
                .thenReturn(List.of(persisted));

        List<CourseResponse> response = cquDataService.getSchedule(user, "2025-spring", "20230001", false);

        assertThat(response).extracting(CourseResponse::courseName).containsExactly("Java");
        verifyNoInteractions(cquApiClient);
    }

    @Test
    void shouldReturnLatestStoredSchoolDataFromPersistedUserContext() {
        CquApiProperties properties = new CquApiProperties();
        properties.setRequireLogin(true);
        cquDataService = new CquDataService(cquApiClient, courseRepository, gradeRepository, userRepository, properties);

        User user = new User();
        user.setId(1L);
        user.setUsername("alice");
        user.setLastSchoolStudentId("20230001");
        user.setLastSchoolSemester("2025-spring");

        Course course = new Course();
        course.setUser(user);
        course.setCourseName("Java");
        course.setTeacher("Zhang");
        course.setClassroom("A101");
        course.setDayOfWeek(1);
        course.setStartTime(1);
        course.setEndTime(2);
        course.setSemester("2025-spring");
        course.setStudentId("20230001");

        Grade grade = new Grade();
        grade.setUser(user);
        grade.setCourseName("Java");
        grade.setGrade(95D);
        grade.setSemester("2025-spring");
        grade.setStudentId("20230001");

        when(courseRepository.findByUserIdAndStudentIdAndSemesterOrderByDayOfWeekAscStartTimeAsc(1L, "20230001", "2025-spring"))
                .thenReturn(List.of(course));
        when(gradeRepository.findByUserIdAndStudentIdOrderBySemesterAscGradeDesc(1L, "20230001"))
                .thenReturn(List.of(grade));

        LatestSchoolDataResponse response = cquDataService.getLatest(user);

        assertThat(response.studentId()).isEqualTo("20230001");
        assertThat(response.semester()).isEqualTo("2025-spring");
        assertThat(response.schedule()).extracting(CourseResponse::courseName).containsExactly("Java");
        assertThat(response.grades()).extracting(GradeResponse::courseName).containsExactly("Java");
    }

    @Test
    void shouldFallbackToMostRecentStoredSchoolDataWhenUserContextIsEmpty() {
        CquApiProperties properties = new CquApiProperties();
        properties.setRequireLogin(true);
        cquDataService = new CquDataService(cquApiClient, courseRepository, gradeRepository, userRepository, properties);

        User user = new User();
        user.setId(1L);
        user.setUsername("alice");

        Course latestCourse = new Course();
        latestCourse.setUser(user);
        latestCourse.setCourseName("Java");
        latestCourse.setTeacher("Zhang");
        latestCourse.setClassroom("A101");
        latestCourse.setDayOfWeek(1);
        latestCourse.setStartTime(1);
        latestCourse.setEndTime(2);
        latestCourse.setSemester("2025-spring");
        latestCourse.setStudentId("20230001");
        latestCourse.setCreatedAt(LocalDateTime.now());

        when(courseRepository.findTopByUserIdOrderByCreatedAtDesc(1L)).thenReturn(Optional.of(latestCourse));
        when(gradeRepository.findTopByUserIdOrderByCreatedAtDesc(1L)).thenReturn(Optional.empty());
        when(courseRepository.findByUserIdAndStudentIdAndSemesterOrderByDayOfWeekAscStartTimeAsc(1L, "20230001", "2025-spring"))
                .thenReturn(List.of(latestCourse));
        when(gradeRepository.findByUserIdAndStudentIdOrderBySemesterAscGradeDesc(1L, "20230001"))
                .thenReturn(List.of());

        LatestSchoolDataResponse response = cquDataService.getLatest(user);

        assertThat(response.studentId()).isEqualTo("20230001");
        assertThat(response.semester()).isEqualTo("2025-spring");
        assertThat(user.getLastSchoolStudentId()).isEqualTo("20230001");
        assertThat(user.getLastSchoolSemester()).isEqualTo("2025-spring");
    }
}
