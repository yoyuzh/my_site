package com.yoyuzh.cqu;

import com.yoyuzh.auth.CustomUserDetailsService;
import com.yoyuzh.auth.User;
import com.yoyuzh.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/cqu")
@RequiredArgsConstructor
public class CquController {

    private final CquDataService cquDataService;
    private final CustomUserDetailsService userDetailsService;

    @Operation(summary = "获取课表")
    @GetMapping("/schedule")
    public ApiResponse<List<CourseResponse>> schedule(@AuthenticationPrincipal UserDetails userDetails,
                                                      @RequestParam String semester,
                                                      @RequestParam String studentId,
                                                      @RequestParam(defaultValue = "false") boolean refresh) {
        return ApiResponse.success(cquDataService.getSchedule(resolveUser(userDetails), semester, studentId, refresh));
    }

    @Operation(summary = "获取成绩")
    @GetMapping("/grades")
    public ApiResponse<List<GradeResponse>> grades(@AuthenticationPrincipal UserDetails userDetails,
                                                   @RequestParam String semester,
                                                   @RequestParam String studentId,
                                                   @RequestParam(defaultValue = "false") boolean refresh) {
        return ApiResponse.success(cquDataService.getGrades(resolveUser(userDetails), semester, studentId, refresh));
    }

    @Operation(summary = "获取最近一次教务数据")
    @GetMapping("/latest")
    public ApiResponse<LatestSchoolDataResponse> latest(@AuthenticationPrincipal UserDetails userDetails) {
        return ApiResponse.success(cquDataService.getLatest(resolveUser(userDetails)));
    }

    private User resolveUser(UserDetails userDetails) {
        return userDetails == null ? null : userDetailsService.loadDomainUser(userDetails.getUsername());
    }
}
