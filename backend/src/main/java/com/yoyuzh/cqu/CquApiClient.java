package com.yoyuzh.cqu;

import com.yoyuzh.config.CquApiProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class CquApiClient {

    private final RestClient restClient;
    private final CquApiProperties properties;

    public List<Map<String, Object>> fetchSchedule(String semester, String studentId) {
        if (properties.isMockEnabled()) {
            return CquMockDataFactory.createSchedule(semester, studentId);
        }
        return restClient.get()
                .uri(properties.getBaseUrl() + "/schedule?semester={semester}&studentId={studentId}", semester, studentId)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
    }

    public List<Map<String, Object>> fetchGrades(String semester, String studentId) {
        if (properties.isMockEnabled()) {
            return CquMockDataFactory.createGrades(semester, studentId);
        }
        return restClient.get()
                .uri(properties.getBaseUrl() + "/grades?semester={semester}&studentId={studentId}", semester, studentId)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
    }
}
