package com.yoyuzh.cqu;

import com.yoyuzh.auth.User;
import com.yoyuzh.common.BusinessException;
import com.yoyuzh.common.ErrorCode;
import com.yoyuzh.config.CquApiProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CquDataService {

    private final CquApiClient cquApiClient;
    private final CourseRepository courseRepository;
    private final GradeRepository gradeRepository;
    private final CquApiProperties cquApiProperties;

    @Transactional
    public List<CourseResponse> getSchedule(User user, String semester, String studentId) {
        requireLoginIfNecessary(user);
        List<CourseResponse> responses = cquApiClient.fetchSchedule(semester, studentId).stream()
                .map(this::toCourseResponse)
                .toList();
        if (user != null) {
            saveCourses(user, semester, studentId, responses);
            return courseRepository.findByUserIdAndStudentIdAndSemesterOrderByDayOfWeekAscStartTimeAsc(
                            user.getId(), studentId, semester)
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
        return responses;
    }

    @Transactional
    public List<GradeResponse> getGrades(User user, String semester, String studentId) {
        requireLoginIfNecessary(user);
        List<GradeResponse> responses = cquApiClient.fetchGrades(semester, studentId).stream()
                .map(this::toGradeResponse)
                .toList();
        if (user != null) {
            saveGrades(user, semester, studentId, responses);
            return gradeRepository.findByUserIdAndStudentIdOrderBySemesterAscGradeDesc(user.getId(), studentId)
                    .stream()
                    .map(item -> new GradeResponse(item.getCourseName(), item.getGrade(), item.getSemester()))
                    .toList();
        }
        return responses;
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
}
