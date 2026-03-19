package com.yoyuzh.cqu;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CourseRepository extends JpaRepository<Course, Long> {
    List<Course> findByUserIdAndStudentIdAndSemesterOrderByDayOfWeekAscStartTimeAsc(Long userId, String studentId, String semester);

    Optional<Course> findTopByUserIdOrderByCreatedAtDesc(Long userId);

    void deleteByUserIdAndStudentIdAndSemester(Long userId, String studentId, String semester);

    long countByUserIdAndStudentIdAndSemester(Long userId, String studentId, String semester);
}
