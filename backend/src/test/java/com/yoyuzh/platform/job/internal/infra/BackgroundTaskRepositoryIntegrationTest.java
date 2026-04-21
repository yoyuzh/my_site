package com.yoyuzh.platform.job.internal.infra;

import com.yoyuzh.platform.job.internal.domain.BackgroundTask;

import com.yoyuzh.platform.job.api.BackgroundTaskFailureCategory;
import com.yoyuzh.platform.job.api.BackgroundTaskStatus;
import com.yoyuzh.platform.job.api.BackgroundTaskType;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class BackgroundTaskRepositoryIntegrationTest {

    @Autowired
    private BackgroundTaskRepository backgroundTaskRepository;

    @Test
    void shouldRejectDuplicateCorrelationIdsAtDatabaseLevel() {
        backgroundTaskRepository.saveAndFlush(createTask("media-meta:auto:file:77", 7L));

        assertThatThrownBy(() -> backgroundTaskRepository.saveAndFlush(createTask("media-meta:auto:file:77", 8L)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private BackgroundTask createTask(String correlationId, Long userId) {
        BackgroundTask task = new BackgroundTask();
        task.setType(BackgroundTaskType.MEDIA_META);
        task.setStatus(BackgroundTaskStatus.QUEUED);
        task.setUserId(userId);
        task.setPublicStateJson("{\"phase\":\"queued\"}");
        task.setPrivateStateJson("{\"taskType\":\"MEDIA_META\"}");
        task.setCorrelationId(correlationId);
        task.setAttemptCount(0);
        task.setMaxAttempts(2);
        return task;
    }
}
