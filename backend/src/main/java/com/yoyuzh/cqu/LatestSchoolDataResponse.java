package com.yoyuzh.cqu;

import java.util.List;

public record LatestSchoolDataResponse(
        String studentId,
        String semester,
        List<CourseResponse> schedule,
        List<GradeResponse> grades
) {
}
