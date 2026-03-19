package com.yoyuzh.cqu;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GradeRepository extends JpaRepository<Grade, Long> {
    List<Grade> findByUserIdAndStudentIdOrderBySemesterAscGradeDesc(Long userId, String studentId);

    boolean existsByUserIdAndStudentIdAndSemester(Long userId, String studentId, String semester);

    Optional<Grade> findTopByUserIdOrderByCreatedAtDesc(Long userId);

    void deleteByUserIdAndStudentIdAndSemester(Long userId, String studentId, String semester);

    long countByUserIdAndStudentIdAndSemester(Long userId, String studentId, String semester);
}
