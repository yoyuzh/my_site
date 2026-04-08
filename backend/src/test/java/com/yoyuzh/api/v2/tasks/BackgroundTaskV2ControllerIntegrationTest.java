package com.yoyuzh.api.v2.tasks;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.yoyuzh.PortalBackendApplication;
import com.yoyuzh.auth.User;
import com.yoyuzh.auth.UserRepository;
import com.yoyuzh.files.BackgroundTask;
import com.yoyuzh.files.BackgroundTaskRepository;
import com.yoyuzh.files.BackgroundTaskStatus;
import com.yoyuzh.files.BackgroundTaskType;
import com.yoyuzh.files.StoredFile;
import com.yoyuzh.files.StoredFileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        classes = PortalBackendApplication.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:background_task_api_test;MODE=MySQL;DB_CLOSE_DELAY=-1",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "app.jwt.secret=0123456789abcdef0123456789abcdef",
                "app.storage.root-dir=./target/test-storage-background-task"
        }
)
@AutoConfigureMockMvc
class BackgroundTaskV2ControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BackgroundTaskRepository backgroundTaskRepository;

    @Autowired
    private StoredFileRepository storedFileRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private Long archiveDirectoryId;
    private Long archiveFileId;
    private Long extractFileId;
    private Long mediaFileId;
    private Long foreignFileId;
    private Long deletedFileId;

    @BeforeEach
    void setUp() {
        backgroundTaskRepository.deleteAll();
        storedFileRepository.deleteAll();
        userRepository.deleteAll();

        User alice = new User();
        alice.setUsername("alice");
        alice.setEmail("alice@example.com");
        alice.setPhoneNumber("13800138000");
        alice.setPasswordHash("encoded-password");
        alice.setCreatedAt(LocalDateTime.now());
        userRepository.save(alice);

        User bob = new User();
        bob.setUsername("bob");
        bob.setEmail("bob@example.com");
        bob.setPhoneNumber("13800138001");
        bob.setPasswordHash("encoded-password");
        bob.setCreatedAt(LocalDateTime.now());
        bob = userRepository.save(bob);

        archiveDirectoryId = storedFileRepository.save(createFile(alice, "/docs", "archive", true, null, 0L, null)).getId();
        archiveFileId = storedFileRepository.save(createFile(alice, "/docs", "archive-source.txt", false, "text/plain", 12L, null)).getId();
        extractFileId = storedFileRepository.save(createFile(alice, "/docs", "extract.zip", false, "application/zip", 32L, null)).getId();
        mediaFileId = storedFileRepository.save(createFile(alice, "/docs", "media.png", false, "image/png", 24L, null)).getId();
        foreignFileId = storedFileRepository.save(createFile(bob, "/docs", "foreign.zip", false, "application/zip", 32L, null)).getId();
        deletedFileId = storedFileRepository.save(createFile(alice, "/docs", "deleted.zip", false, "application/zip", 32L, LocalDateTime.now())).getId();
    }

    @Test
    void shouldRequireAuthenticationForTaskEndpoints() throws Exception {
        mockMvc.perform(get("/api/v2/tasks").with(anonymous()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v2/tasks/archive")
                        .with(anonymous())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fileId": 1,
                                  "path": "/docs"
                                }
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldQueueListGetAndCancelOwnedTasks() throws Exception {
        String archiveResponse = mockMvc.perform(post("/api/v2/tasks/archive")
                        .with(user("alice"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fileId": %d,
                                  "path": "/docs/archive",
                                  "correlationId": "archive-1"
                                }
                                """.formatted(archiveDirectoryId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.type").value("ARCHIVE"))
                .andExpect(jsonPath("$.data.status").value("QUEUED"))
                .andExpect(jsonPath("$.data.publicStateJson", containsString("\"fileId\":" + archiveDirectoryId)))
                .andExpect(jsonPath("$.data.publicStateJson", containsString("\"path\":\"/docs/archive\"")))
                .andExpect(jsonPath("$.data.publicStateJson", containsString("\"directory\":true")))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String extractResponse = mockMvc.perform(post("/api/v2/tasks/extract")
                        .with(user("alice"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fileId": %d,
                                  "path": "/docs/extract.zip"
                                }
                                """.formatted(extractFileId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.type").value("EXTRACT"))
                .andExpect(jsonPath("$.data.status").value("QUEUED"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String mediaResponse = mockMvc.perform(post("/api/v2/tasks/media-metadata")
                        .with(user("alice"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fileId": %d,
                                  "path": "/docs/media.png"
                                }
                                """.formatted(mediaFileId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.type").value("MEDIA_META"))
                .andExpect(jsonPath("$.data.status").value("QUEUED"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        Long archiveId = ((Number) JsonPath.read(archiveResponse, "$.data.id")).longValue();
        Long extractId = ((Number) JsonPath.read(extractResponse, "$.data.id")).longValue();
        Long mediaId = ((Number) JsonPath.read(mediaResponse, "$.data.id")).longValue();

        mockMvc.perform(get("/api/v2/tasks")
                        .with(user("alice"))
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.items[0].id").value(mediaId));

        mockMvc.perform(get("/api/v2/tasks/{id}", archiveId).with(user("alice")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(archiveId))
                .andExpect(jsonPath("$.data.privateStateJson").doesNotExist());

        mockMvc.perform(delete("/api/v2/tasks/{id}", extractId).with(user("alice")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));

        BackgroundTask cancelled = backgroundTaskRepository.findById(extractId).orElseThrow();
        assertThat(cancelled.getStatus()).isEqualTo(BackgroundTaskStatus.CANCELLED);
        assertThat(cancelled.getFinishedAt()).isNotNull();
    }

    @Test
    void shouldRejectOtherUsersTaskAccess() throws Exception {
        String response = mockMvc.perform(post("/api/v2/tasks/archive")
                        .with(user("alice"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fileId": %d,
                                  "path": "/docs/archive-source.txt"
                                }
                                """.formatted(archiveFileId)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        Long taskId = ((Number) JsonPath.read(response, "$.data.id")).longValue();

        mockMvc.perform(get("/api/v2/tasks/{id}", taskId).with(user("bob")))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/v2/tasks/{id}", taskId).with(user("bob")))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldRejectInvalidTaskTargetsBeforeQueueing() throws Exception {
        mockMvc.perform(post("/api/v2/tasks/archive")
                        .with(user("alice"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fileId": %d,
                                  "path": "/docs/foreign.zip"
                                }
                                """.formatted(foreignFileId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(2404));

        mockMvc.perform(post("/api/v2/tasks/archive")
                        .with(user("alice"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fileId": %d,
                                  "path": "/docs/deleted.zip"
                                }
                                """.formatted(deletedFileId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(2404));

        mockMvc.perform(post("/api/v2/tasks/archive")
                        .with(user("alice"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fileId": %d,
                                  "path": "/docs/client-path.zip"
                                }
                                """.formatted(extractFileId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(2400));
    }

    private StoredFile createFile(User user,
                                  String path,
                                  String filename,
                                  boolean directory,
                                  String contentType,
                                  Long size,
                                  LocalDateTime deletedAt) {
        StoredFile file = new StoredFile();
        file.setUser(user);
        file.setPath(path);
        file.setFilename(filename);
        file.setDirectory(directory);
        file.setContentType(contentType);
        file.setSize(size);
        file.setDeletedAt(deletedAt);
        return file;
    }
}
