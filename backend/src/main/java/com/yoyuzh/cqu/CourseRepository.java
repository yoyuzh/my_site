package com.yoyuzh.cqu;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CourseRepository extends JpaRepository<Course, Long> {
    List<Course> findByUserIdAndStudentIdAndSemesterOrderByDayOfWeekAscStartTimeAsc(Long userId, String studentId, String semester);

    void deleteByUserIdAndStudentIdAndSemester(Long userId, String studentId, String semester);
}
