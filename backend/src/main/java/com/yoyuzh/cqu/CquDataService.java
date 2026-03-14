package com.yoyuzh.cqu;

import com.yoyuzh.auth.UserRepository;
import com.yoyuzh.auth.User;
import com.yoyuzh.common.BusinessException;
import com.yoyuzh.common.ErrorCode;
import com.yoyuzh.config.CquApiProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CquDataService {

    private final CquApiClient cquApiClient;
    private final CourseRepository courseRepository;
    private final GradeRepository gradeRepository;
    private final UserRepository userRepository;
    private final CquApiProperties cquApiProperties;

    @Transactional
    public List<CourseResponse> getSchedule(User user, String semester, String studentId) {
        return getSchedule(user, semester, studentId, false);
    }

    @Transactional
    public List<CourseResponse> getSchedule(User user, String semester, String studentId, boolean refresh) {
        requireLoginIfNecessary(user);
        if (user != null && !refresh) {
            List<CourseResponse> stored = readSavedSchedule(user.getId(), studentId, semester);
            if (!stored.isEmpty()) {
                rememberLastSchoolQuery(user, studentId, semester);
                return stored;
            }
        }

        List<CourseResponse> responses = cquApiClient.fetchSchedule(semester, studentId).stream()
                .map(this::toCourseResponse)
                .toList();
        if (user != null) {
            saveCourses(user, semester, studentId, responses);
            rememberLastSchoolQuery(user, studentId, semester);
            return readSavedSchedule(user.getId(), studentId, semester);
        }
        return responses;
    }

    @Transactional
    public List<GradeResponse> getGrades(User user, String semester, String studentId) {
        return getGrades(user, semester, studentId, false);
    }

    @Transactional
    public List<GradeResponse> getGrades(User user, String semester, String studentId, boolean refresh) {
        requireLoginIfNecessary(user);
        if (user != null && !refresh
                && gradeRepository.existsByUserIdAndStudentIdAndSemester(user.getId(), studentId, semester)) {
            rememberLastSchoolQuery(user, studentId, semester);
            return readSavedGrades(user.getId(), studentId);
        }

        List<GradeResponse> responses = cquApiClient.fetchGrades(semester, studentId).stream()
                .map(this::toGradeResponse)
                .toList();
        if (user != null) {
            saveGrades(user, semester, studentId, responses);
            rememberLastSchoolQuery(user, studentId, semester);
            return readSavedGrades(user.getId(), studentId);
        }
        return responses;
    }

    @Transactional
    public LatestSchoolDataResponse getLatest(User user) {
        requireLoginIfNecessary(user);
        if (user == null) {
            return null;
        }

        QueryContext context = resolveLatestContext(user);
        if (context == null) {
            return null;
        }

        List<CourseResponse> schedule = readSavedSchedule(user.getId(), context.studentId(), context.semester());
        List<GradeResponse> grades = readSavedGrades(user.getId(), context.studentId());
        if (schedule.isEmpty() && grades.isEmpty()) {
            return null;
        }

        return new LatestSchoolDataResponse(context.studentId(), context.semester(), schedule, grades);
    }

    private void requireLoginIfNecessary(User user) {
        if (cquApiProperties.isRequireLogin() && user == null) {
            throw new BusinessException(ErrorCode.NOT_LOGGED_IN, "该接口需要登录后访问");
        }
    }

    @Transactional
    protected void saveCourses(User user, String semester, String studentId, List<CourseResponse> responses) {
        courseRepository.deleteByUserIdAndStudentIdAndSemester(user.getId(), studentId, semester);
        courseRepository.saveAll(responses.stream().map(item -> {
            Course course = new Course();
            course.setUser(user);
            course.setCourseName(item.courseName());
            course.setSemester(semester);
            course.setStudentId(studentId);
            course.setTeacher(item.teacher());
            course.setClassroom(item.classroom());
            course.setDayOfWeek(item.dayOfWeek());
            course.setStartTime(item.startTime());
            course.setEndTime(item.endTime());
            return course;
        }).toList());
    }

    @Transactional
    protected void saveGrades(User user, String semester, String studentId, List<GradeResponse> responses) {
        gradeRepository.deleteByUserIdAndStudentIdAndSemester(user.getId(), studentId, semester);
        gradeRepository.saveAll(responses.stream().map(item -> {
            Grade grade = new Grade();
            grade.setUser(user);
            grade.setCourseName(item.courseName());
            grade.setGrade(item.grade());
            grade.setSemester(item.semester() == null ? semester : item.semester());
            grade.setStudentId(studentId);
            return grade;
        }).toList());
    }

    private List<CourseResponse> readSavedSchedule(Long userId, String studentId, String semester) {
        return courseRepository.findByUserIdAndStudentIdAndSemesterOrderByDayOfWeekAscStartTimeAsc(
                        userId, studentId, semester)
                .stream()
                .map(item -> new CourseResponse(
                        item.getCourseName(),
                        item.getTeacher(),
                        item.getClassroom(),
                        item.getDayOfWeek(),
                        item.getStartTime(),
                        item.getEndTime()))
                .toList();
    }

    private List<GradeResponse> readSavedGrades(Long userId, String studentId) {
        return gradeRepository.findByUserIdAndStudentIdOrderBySemesterAscGradeDesc(userId, studentId)
                .stream()
                .map(item -> new GradeResponse(item.getCourseName(), item.getGrade(), item.getSemester()))
                .toList();
    }

    private void rememberLastSchoolQuery(User user, String studentId, String semester) {
        boolean changed = false;
        if (!semester.equals(user.getLastSchoolSemester())) {
            user.setLastSchoolSemester(semester);
            changed = true;
        }
        if (!studentId.equals(user.getLastSchoolStudentId())) {
            user.setLastSchoolStudentId(studentId);
            changed = true;
        }
        if (changed) {
            userRepository.save(user);
        }
    }

    private QueryContext resolveLatestContext(User user) {
        if (hasText(user.getLastSchoolStudentId()) && hasText(user.getLastSchoolSemester())) {
            return new QueryContext(user.getLastSchoolStudentId(), user.getLastSchoolSemester());
        }

        Optional<Course> latestCourse = courseRepository.findTopByUserIdOrderByCreatedAtDesc(user.getId());
        Optional<Grade> latestGrade = gradeRepository.findTopByUserIdOrderByCreatedAtDesc(user.getId());
        if (latestCourse.isEmpty() && latestGrade.isEmpty()) {
            return null;
        }

        QueryContext context;
        if (latestGrade.isEmpty()) {
            context = new QueryContext(latestCourse.get().getStudentId(), latestCourse.get().getSemester());
        } else if (latestCourse.isEmpty()) {
            context = new QueryContext(latestGrade.get().getStudentId(), latestGrade.get().getSemester());
        } else if (latestCourse.get().getCreatedAt().isAfter(latestGrade.get().getCreatedAt())) {
            context = new QueryContext(latestCourse.get().getStudentId(), latestCourse.get().getSemester());
        } else {
            context = new QueryContext(latestGrade.get().getStudentId(), latestGrade.get().getSemester());
        }

        if (hasText(context.studentId()) && hasText(context.semester())) {
            user.setLastSchoolStudentId(context.studentId());
            user.setLastSchoolSemester(context.semester());
            userRepository.save(user);
            return context;
        }
        return null;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private CourseResponse toCourseResponse(Map<String, Object> source) {
        return new CourseResponse(
                stringValue(source, "courseName"),
                stringValue(source, "teacher"),
                stringValue(source, "classroom"),
                intValue(source, "dayOfWeek"),
                intValue(source, "startTime"),
                intValue(source, "endTime"));
    }

    private GradeResponse toGradeResponse(Map<String, Object> source) {
        return new GradeResponse(
                stringValue(source, "courseName"),
                doubleValue(source, "grade"),
                stringValue(source, "semester"));
    }

    private String stringValue(Map<String, Object> source, String key) {
        Object value = source.get(key);
        return value == null ? null : value.toString();
    }

    private Integer intValue(Map<String, Object> source, String key) {
        Object value = source.get(key);
        return value == null ? null : Integer.parseInt(value.toString());
    }

    private Double doubleValue(Map<String, Object> source, String key) {
        Object value = source.get(key);
        return value == null ? null : Double.parseDouble(value.toString());
    }

    private record QueryContext(String studentId, String semester) {
    }
}
