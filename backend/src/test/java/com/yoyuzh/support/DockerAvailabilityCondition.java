package com.yoyuzh.support;

import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.testcontainers.DockerClientFactory;

final class DockerAvailabilityCondition implements ExecutionCondition {

    private static final ConditionEvaluationResult RESULT = evaluateDockerAvailability();

    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
        return RESULT;
    }

    private static ConditionEvaluationResult evaluateDockerAvailability() {
        try {
            if (DockerClientFactory.instance().isDockerAvailable()) {
                return ConditionEvaluationResult.enabled("Docker is available for Testcontainers");
            }
            return ConditionEvaluationResult.disabled("Docker is unavailable for Testcontainers");
        } catch (Throwable throwable) {
            return ConditionEvaluationResult.disabled(
                    "Docker is unavailable for Testcontainers: " + throwable.getMessage()
            );
        }
    }
}
