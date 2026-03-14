package com.yoyuzh.cqu;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GradeRepository extends JpaRepository<Grade, Long> {
    List<Grade> findByUserIdAndStudentIdOrderBySemesterAscGradeDesc(Long userId, String studentId);

    void deleteByUserIdAndStudentIdAndSemester(Long userId, String studentId, String semester);
}
