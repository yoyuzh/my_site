package com.yoyuzh.platform.job.internal.web;

import com.jayway.jsonpath.JsonPath;
import com.yoyuzh.PortalBackendApplication;
import com.yoyuzh.files.workspace.internal.domain.StoredFile;
import com.yoyuzh.files.workspace.internal.infra.StoredFileRepository;
import com.yoyuzh.identity.access.internal.domain.User;
import com.yoyuzh.identity.access.internal.infra.UserRepository;
import com.yoyuzh.platform.job.internal.infra.BackgroundTaskRepository;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        classes = PortalBackendApplication.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:background_task_v2_smoke;MODE=MySQL;DB_CLOSE_DELAY=-1",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "app.jwt.secret=0123456789abcdef0123456789abcdef",
                "app.background-tasks.worker.lightweight-wakeup-enabled=false"
        }
)
@AutoConfigureMockMvc
class BackgroundTaskV2SmokeTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StoredFileRepository storedFileRepository;

    @Autowired
    private BackgroundTaskRepository backgroundTaskRepository;

    private Long fileId;

    @BeforeEach
    void setUp() {
        backgroundTaskRepository.deleteAll();
        storedFileRepository.deleteAll();
        userRepository.deleteAll();

        User alice = new User();
        alice.setUsername("alice");
        alice.setEmail("alice-smoke@example.com");
        alice.setPhoneNumber("13800138999");
        alice.setPasswordHash("encoded-password");
        alice.setCreatedAt(LocalDateTime.now());
        alice = userRepository.save(alice);

        StoredFile file = StoredFile.blobBackedFile(
                alice.getId(),
                "/docs",
                "task-smoke.txt",
                "text/plain",
                12L,
                null,
                "task-smoke.txt",
                null
        );
        fileId = storedFileRepository.save(file).getId();
    }

    @Test
    void shouldSmokeQueueListGetAndReadProgressForOwnedTask() throws Exception {
        mockMvc.perform(get("/api/v2/tasks").with(anonymous()))
                .andExpect(status().isUnauthorized());

        String queueResponse = mockMvc.perform(post("/api/v2/tasks/archive")
                        .with(user("alice"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fileId": %d,
                                  "path": "/docs/task-smoke.txt",
                                  "correlationId": "task-smoke-archive"
                                }
                                """.formatted(fileId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.type").value("ARCHIVE"))
                .andExpect(jsonPath("$.data.status").value("QUEUED"))
                .andExpect(jsonPath("$.data.privateStateJson").doesNotExist())
                .andExpect(jsonPath("$.data.publicStateJson", containsString("\"phase\":\"queued\"")))
                .andReturn()
                .getResponse()
                .getContentAsString();
        Long taskId = ((Number) JsonPath.read(queueResponse, "$.data.id")).longValue();

        mockMvc.perform(get("/api/v2/tasks").with(user("alice")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(taskId));

        mockMvc.perform(get("/api/v2/tasks/{id}", taskId).with(user("alice")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(taskId))
                .andExpect(jsonPath("$.data.type").value("ARCHIVE"))
                .andExpect(jsonPath("$.data.privateStateJson").doesNotExist());

        mockMvc.perform(get("/api/v2/tasks/{id}/progress", taskId).with(user("alice")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.taskId").value(taskId))
                .andExpect(jsonPath("$.data.status").value("QUEUED"))
                .andExpect(jsonPath("$.data.progressPercent").value(0));
    }
}
