package com.yoyuzh.platform.job.internal.web;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.yoyuzh.PortalBackendApplication;
import com.yoyuzh.identity.access.internal.domain.User;
import com.yoyuzh.identity.access.internal.domain.UserRole;
import com.yoyuzh.identity.access.internal.infra.UserRepository;
import com.yoyuzh.platform.job.internal.domain.BackgroundTask;
import com.yoyuzh.platform.job.internal.infra.BackgroundTaskRepository;
import com.yoyuzh.platform.job.api.BackgroundTaskStatus;
import com.yoyuzh.platform.job.internal.application.BackgroundTaskStartupRecovery;
import com.yoyuzh.platform.job.api.BackgroundTaskType;
import com.yoyuzh.platform.job.internal.application.BackgroundTaskWorker;
import com.yoyuzh.files.content.internal.domain.FileBlob;
import com.yoyuzh.files.content.internal.infra.FileBlobRepository;
import com.yoyuzh.files.workspace.internal.domain.StoredFile;
import com.yoyuzh.files.workspace.internal.infra.StoredFileRepository;
import com.yoyuzh.files.content.api.FileContentStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
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
    private BackgroundTaskWorker backgroundTaskWorker;

    @Autowired
    private BackgroundTaskStartupRecovery backgroundTaskStartupRecovery;

    @Autowired
    private FileBlobRepository fileBlobRepository;

    @Autowired
    private FileContentStorage fileContentStorage;

    @Autowired
    private StoredFileRepository storedFileRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private Long aliceId;
    private Long archiveDirectoryId;
    private Long archiveFileId;
    private Long extractFileId;
    private Long invalidExtractFileId;
    private Long unsupportedExtractFileId;
    private Long mediaFileId;
    private Long foreignFileId;
    private Long deletedFileId;

    @BeforeEach
    void setUp() throws Exception {
        backgroundTaskRepository.deleteAll();
        storedFileRepository.deleteAll();
        fileBlobRepository.deleteAll();
        userRepository.deleteAll();

        User alice = new User();
        alice.setUsername("alice");
        alice.setEmail("alice@example.com");
        alice.setPhoneNumber("13800138000");
        alice.setPasswordHash("encoded-password");
        alice.setCreatedAt(LocalDateTime.now());
        alice = userRepository.save(alice);
        aliceId = alice.getId();

        User bob = new User();
        bob.setUsername("bob");
        bob.setEmail("bob@example.com");
        bob.setPhoneNumber("13800138001");
        bob.setPasswordHash("encoded-password");
        bob.setCreatedAt(LocalDateTime.now());
        bob = userRepository.save(bob);

        User admin = new User();
        admin.setUsername("admin");
        admin.setEmail("admin@example.com");
        admin.setPhoneNumber("13800138002");
        admin.setPasswordHash("encoded-password");
        admin.setRole(UserRole.ADMIN);
        admin.setCreatedAt(LocalDateTime.now());
        userRepository.save(admin);

        archiveDirectoryId = storedFileRepository.save(createFile(alice, "/docs", "archive", true, null, 0L, null)).getId();
        storedFileRepository.save(createBlobBackedFile(
                alice,
                "/docs/archive",
                "nested.txt",
                "text/plain",
                "archive-nested",
                "nested-content".getBytes(StandardCharsets.UTF_8)
        ));
        archiveFileId = storedFileRepository.save(createBlobBackedFile(
                alice,
                "/docs",
                "archive-source.txt",
                "text/plain",
                "archive-source",
                "archive-source".getBytes(StandardCharsets.UTF_8)
        )).getId();
        extractFileId = storedFileRepository.save(createBlobBackedFile(
                alice,
                "/docs",
                "extract.zip",
                "application/zip",
                "extract-source",
                createZipArchive(Map.of(
                        "extract/", "",
                        "extract/nested/", "",
                        "extract/notes.txt", "hello",
                        "extract/nested/todo.txt", "world"
                ))
        )).getId();
        invalidExtractFileId = storedFileRepository.save(createBlobBackedFile(
                alice,
                "/docs",
                "broken.zip",
                "application/zip",
                "broken-extract",
                "not-a-zip".getBytes(StandardCharsets.UTF_8)
        )).getId();
        unsupportedExtractFileId = storedFileRepository.save(createFile(alice, "/docs", "backup.exe", false, "application/octet-stream", 64L, null)).getId();
        mediaFileId = storedFileRepository.save(createFile(alice, "/docs", "media.png", false, "image/png", 24L, null)).getId();
        foreignFileId = storedFileRepository.save(createBlobBackedFile(
                bob,
                "/docs",
                "foreign.zip",
                "application/zip",
                "foreign-zip",
                createZipArchive(Map.of("foreign.txt", "blocked"))
        )).getId();
        deletedFileId = storedFileRepository.save(createBlobBackedFile(
                alice,
                "/docs",
                "deleted.zip",
                "application/zip",
                "deleted-zip",
                createZipArchive(Map.of("deleted.txt", "gone")),
                LocalDateTime.now()
        )).getId();
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
                .andExpect(jsonPath("$.data.publicStateJson", containsString("\"phase\":\"queued\"")))
                .andExpect(jsonPath("$.data.publicStateJson", containsString("\"attemptCount\":0")))
                .andExpect(jsonPath("$.data.publicStateJson", containsString("\"maxAttempts\":4")))
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
                .andExpect(jsonPath("$.data.publicStateJson", containsString("\"outputPath\":\"/docs\"")))
                .andExpect(jsonPath("$.data.publicStateJson", containsString("\"outputDirectoryName\":\"extract\"")))
                .andExpect(jsonPath("$.data.publicStateJson", containsString("\"phase\":\"queued\"")))
                .andExpect(jsonPath("$.data.publicStateJson", containsString("\"attemptCount\":0")))
                .andExpect(jsonPath("$.data.publicStateJson", containsString("\"maxAttempts\":3")))
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
                .andExpect(jsonPath("$.data.publicStateJson", containsString("\"phase\":\"queued\"")))
                .andExpect(jsonPath("$.data.publicStateJson", containsString("\"attemptCount\":0")))
                .andExpect(jsonPath("$.data.publicStateJson", containsString("\"maxAttempts\":2")))
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
                .andExpect(jsonPath("$.data.status").value("CANCELLED"))
                .andExpect(jsonPath("$.data.publicStateJson", containsString("\"phase\":\"cancelled\"")));

        BackgroundTask cancelled = backgroundTaskRepository.findById(extractId).orElseThrow();
        assertThat(cancelled.getStatus()).isEqualTo(BackgroundTaskStatus.CANCELLED);
        assertThat(cancelled.getFinishedAt()).isNotNull();
        assertThat(cancelled.getPublicStateJson()).contains("\"phase\":\"cancelled\"");
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

        mockMvc.perform(post("/api/v2/tasks/{id}/retry", taskId).with(user("bob")))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldExposeOwnedTaskProgress() throws Exception {
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

        mockMvc.perform(get("/api/v2/tasks/{id}/progress", taskId).with(user("alice")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.taskId").value(taskId))
                .andExpect(jsonPath("$.data.status").value("QUEUED"))
                .andExpect(jsonPath("$.data.progressPercent").value(0))
                .andExpect(jsonPath("$.data.processedItems").value(0))
                .andExpect(jsonPath("$.data.totalItems").value(0))
                .andExpect(jsonPath("$.data.message").value("QUEUED"));
    }

    @Test
    void shouldAllowAdminToQueueSearchIndexRebuildTask() throws Exception {
        String response = mockMvc.perform(post("/api/v2/tasks/search-index/rebuild")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.type").value("SEARCH_INDEX_REBUILD"))
                .andExpect(jsonPath("$.data.status").value("QUEUED"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        Long taskId = ((Number) JsonPath.read(response, "$.data.id")).longValue();
        BackgroundTask task = backgroundTaskRepository.findById(taskId).orElseThrow();
        assertThat(task.getType()).isEqualTo(BackgroundTaskType.SEARCH_INDEX_REBUILD);
        assertThat(task.getPublicStateJson()).contains("\"progressPercent\":0");
        assertThat(task.getPublicStateJson()).contains("\"message\":\"search index rebuild queued\"");
    }

    @Test
    void shouldRejectExtractTaskForUnsupportedArchive() throws Exception {
        mockMvc.perform(post("/api/v2/tasks/extract")
                        .with(user("alice"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fileId": %d,
                                  "path": "/docs/backup.exe"
                                }
                                """.formatted(unsupportedExtractFileId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(2406));
    }

    @Test
    void shouldCompleteArchiveTaskThroughWorkerAndExposeTerminalState() throws Exception {
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

        int processedCount = backgroundTaskWorker.processQueuedTasks(5);

        assertThat(processedCount).isEqualTo(1);
        String taskResponse = mockMvc.perform(get("/api/v2/tasks/{id}", taskId).with(user("alice")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        Map<String, Object> publicState = readPublicState(taskResponse);
        assertThat(publicState).containsEntry("worker", "archive");
        assertThat(publicState).containsEntry("archivedFilename", "archive-source.txt.zip");
        assertThat(publicState).containsEntry("archivedPath", "/docs");
        assertThat(publicState).containsEntry("phase", "completed");
        assertThat(publicState).containsEntry("attemptCount", 1);
        assertThat(publicState).containsEntry("maxAttempts", 4);
        assertThat(publicState).containsEntry("processedFileCount", 1);
        assertThat(publicState).containsEntry("totalFileCount", 1);
        assertThat(publicState).containsEntry("processedDirectoryCount", 0);
        assertThat(publicState).containsEntry("totalDirectoryCount", 0);
        assertThat(publicState).containsEntry("progressPercent", 100);
        assertThat(publicState.get("heartbeatAt")).isNotNull();
        assertThat(publicState).doesNotContainKey("workerOwner");
        assertThat(publicState).doesNotContainKey("leaseExpiresAt");
        assertThat(publicState.get("archivedFileId")).isNotNull();
        assertThat(publicState.get("archiveSize")).isNotNull();

        BackgroundTask completed = backgroundTaskRepository.findById(taskId).orElseThrow();
        assertThat(completed.getStatus()).isEqualTo(BackgroundTaskStatus.COMPLETED);
        assertThat(completed.getFinishedAt()).isNotNull();
        assertThat(completed.getErrorMessage()).isNull();
        assertThat(storedFileRepository.findByUserIdAndPathAndFilename(aliceId, "/docs", "archive-source.txt.zip")).isPresent();
    }

    @Test
    void shouldCompleteExtractTaskThroughWorkerAndExposeTerminalState() throws Exception {
        String response = mockMvc.perform(post("/api/v2/tasks/extract")
                        .with(user("alice"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fileId": %d,
                                  "path": "/docs/extract.zip"
                                }
                                """.formatted(extractFileId)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        Long taskId = ((Number) JsonPath.read(response, "$.data.id")).longValue();

        int processedCount = backgroundTaskWorker.processQueuedTasks(5);

        assertThat(processedCount).isEqualTo(1);
        String taskResponse = mockMvc.perform(get("/api/v2/tasks/{id}", taskId).with(user("alice")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        Map<String, Object> publicState = readPublicState(taskResponse);
        assertThat(publicState).containsEntry("worker", "extract");
        assertThat(publicState).containsEntry("extractedPath", "/docs/extract");
        assertThat(publicState).containsEntry("extractedFileCount", 2);
        assertThat(publicState).containsEntry("extractedDirectoryCount", 2);
        assertThat(publicState).containsEntry("phase", "completed");
        assertThat(publicState).containsEntry("attemptCount", 1);
        assertThat(publicState).containsEntry("maxAttempts", 3);
        assertThat(publicState).containsEntry("processedFileCount", 2);
        assertThat(publicState).containsEntry("totalFileCount", 2);
        assertThat(publicState).containsEntry("processedDirectoryCount", 2);
        assertThat(publicState).containsEntry("totalDirectoryCount", 2);
        assertThat(publicState).containsEntry("progressPercent", 100);
        assertThat(publicState.get("heartbeatAt")).isNotNull();
        assertThat(publicState).doesNotContainKey("workerOwner");
        assertThat(publicState).doesNotContainKey("leaseExpiresAt");

        BackgroundTask completed = backgroundTaskRepository.findById(taskId).orElseThrow();
        assertThat(completed.getStatus()).isEqualTo(BackgroundTaskStatus.COMPLETED);
        assertThat(completed.getFinishedAt()).isNotNull();
        assertThat(completed.getErrorMessage()).isNull();
        assertThat(storedFileRepository.findByUserIdAndPathAndFilename(aliceId, "/docs/extract", "notes.txt")).isPresent();
        assertThat(storedFileRepository.findByUserIdAndPathAndFilename(aliceId, "/docs/extract/nested", "todo.txt")).isPresent();
    }

    @Test
    void shouldMarkExtractTaskFailedWhenWorkerHitsInvalidArchiveContent() throws Exception {
        String response = mockMvc.perform(post("/api/v2/tasks/extract")
                        .with(user("alice"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fileId": %d,
                                  "path": "/docs/broken.zip"
                                }
                                """.formatted(invalidExtractFileId)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        Long taskId = ((Number) JsonPath.read(response, "$.data.id")).longValue();

        int processedCount = backgroundTaskWorker.processQueuedTasks(5);

        assertThat(processedCount).isEqualTo(1);
        mockMvc.perform(get("/api/v2/tasks/{id}", taskId).with(user("alice")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("FAILED"))
                .andExpect(jsonPath("$.data.publicStateJson", containsString("\"phase\":\"failed\"")))
                .andExpect(jsonPath("$.data.publicStateJson", containsString("\"attemptCount\":1")))
                .andExpect(jsonPath("$.data.publicStateJson", containsString("\"maxAttempts\":3")))
                .andExpect(jsonPath("$.data.publicStateJson", containsString("\"failureCategory\":\"DATA_STATE\"")))
                .andExpect(jsonPath("$.data.errorMessage").value("extract task only supports supported archive files"));

        BackgroundTask failed = backgroundTaskRepository.findById(taskId).orElseThrow();
        assertThat(failed.getStatus()).isEqualTo(BackgroundTaskStatus.FAILED);
        assertThat(failed.getFinishedAt()).isNotNull();
        assertThat(failed.getErrorMessage()).isEqualTo("extract task only supports supported archive files");
    }

    @Test
    void shouldRetryFailedTaskAndResetStateToQueued() throws Exception {
        String response = mockMvc.perform(post("/api/v2/tasks/extract")
                        .with(user("alice"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fileId": %d,
                                  "path": "/docs/broken.zip"
                                }
                                """.formatted(invalidExtractFileId)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        Long taskId = ((Number) JsonPath.read(response, "$.data.id")).longValue();
        backgroundTaskWorker.processQueuedTasks(5);

        String retryResponse = mockMvc.perform(post("/api/v2/tasks/{id}/retry", taskId).with(user("alice")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("QUEUED"))
                .andExpect(jsonPath("$.data.errorMessage").doesNotExist())
                .andExpect(jsonPath("$.data.finishedAt").doesNotExist())
                .andExpect(jsonPath("$.data.publicStateJson", containsString("\"phase\":\"queued\"")))
                .andReturn()
                .getResponse()
                .getContentAsString();

        Map<String, Object> publicState = readPublicState(retryResponse);
        assertThat(publicState).containsEntry("phase", "queued");
        assertThat(publicState).containsEntry("outputPath", "/docs");
        assertThat(publicState).containsEntry("outputDirectoryName", "broken");
        assertThat(publicState).containsEntry("attemptCount", 0);
        assertThat(publicState).containsEntry("maxAttempts", 3);
        assertThat(publicState).doesNotContainKey("worker");
        assertThat(publicState).doesNotContainKey("processedFileCount");
        assertThat(publicState).doesNotContainKey("totalFileCount");

        BackgroundTask retried = backgroundTaskRepository.findById(taskId).orElseThrow();
        assertThat(retried.getStatus()).isEqualTo(BackgroundTaskStatus.QUEUED);
        assertThat(retried.getFinishedAt()).isNull();
        assertThat(retried.getErrorMessage()).isNull();
    }

    @Test
    void shouldRejectRetryForNonFailedTask() throws Exception {
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

        mockMvc.perform(post("/api/v2/tasks/{id}/retry", taskId).with(user("alice")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(2406));
    }

    @Test
    void shouldRecoverOnlyExpiredRunningTaskBackToQueuedOnStartup() throws Exception {
        BackgroundTask expired = new BackgroundTask();
        expired.setType(BackgroundTaskType.EXTRACT);
        expired.setStatus(BackgroundTaskStatus.RUNNING);
        expired.setUserId(aliceId);
        expired.setCorrelationId("recover-1");
        expired.setAttemptCount(1);
        expired.setMaxAttempts(3);
        expired.setLeaseOwner("worker-stale");
        expired.setLeaseExpiresAt(LocalDateTime.now().minusMinutes(2));
        expired.setHeartbeatAt(LocalDateTime.now().minusMinutes(3));
        expired.setPublicStateJson("""
                {"fileId":%d,"path":"/docs/extract.zip","phase":"extracting","worker":"extract","workerOwner":"worker-stale","attemptCount":1,"maxAttempts":3}
                """.formatted(extractFileId));
        expired.setPrivateStateJson("""
                {"fileId":%d,"path":"/docs/extract.zip","taskType":"EXTRACT","outputPath":"/docs","outputDirectoryName":"extract"}
                """.formatted(extractFileId));
        expired.setErrorMessage("stale worker");
        expired.setFinishedAt(LocalDateTime.now());
        expired = backgroundTaskRepository.save(expired);

        BackgroundTask fresh = new BackgroundTask();
        fresh.setType(BackgroundTaskType.EXTRACT);
        fresh.setStatus(BackgroundTaskStatus.RUNNING);
        fresh.setUserId(aliceId);
        fresh.setCorrelationId("recover-2");
        fresh.setAttemptCount(1);
        fresh.setMaxAttempts(3);
        fresh.setLeaseOwner("worker-live");
        fresh.setLeaseExpiresAt(LocalDateTime.now().plusMinutes(5));
        fresh.setHeartbeatAt(LocalDateTime.now());
        fresh.setPublicStateJson("""
                {"fileId":%d,"path":"/docs/extract.zip","phase":"extracting","worker":"extract","workerOwner":"worker-live","attemptCount":1,"maxAttempts":3}
                """.formatted(extractFileId));
        fresh.setPrivateStateJson("""
                {"fileId":%d,"path":"/docs/extract.zip","taskType":"EXTRACT","outputPath":"/docs","outputDirectoryName":"extract"}
                """.formatted(extractFileId));
        fresh = backgroundTaskRepository.save(fresh);

        backgroundTaskStartupRecovery.recoverOnStartup();

        mockMvc.perform(get("/api/v2/tasks/{id}", expired.getId()).with(user("alice")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("QUEUED"))
                .andExpect(jsonPath("$.data.errorMessage").doesNotExist())
                .andExpect(jsonPath("$.data.finishedAt").doesNotExist())
                .andExpect(jsonPath("$.data.publicStateJson", containsString("\"phase\":\"queued\"")))
                .andExpect(jsonPath("$.data.publicStateJson", containsString("\"attemptCount\":1")))
                .andExpect(jsonPath("$.data.publicStateJson", containsString("\"maxAttempts\":3")))
                .andExpect(jsonPath("$.data.publicStateJson", containsString("\"outputPath\":\"/docs\"")))
                .andExpect(jsonPath("$.data.publicStateJson", containsString("\"outputDirectoryName\":\"extract\"")))
                .andExpect(jsonPath("$.data.publicStateJson", not(containsString("\"worker\""))))
                .andExpect(jsonPath("$.data.publicStateJson", not(containsString("\"workerOwner\""))))
                .andExpect(jsonPath("$.data.publicStateJson", not(containsString("\"leaseExpiresAt\""))));

        mockMvc.perform(get("/api/v2/tasks/{id}", fresh.getId()).with(user("alice")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RUNNING"))
                .andExpect(jsonPath("$.data.publicStateJson", containsString("\"workerOwner\":\"worker-live\"")));
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
                .andExpect(jsonPath("$.code").value(2406));
    }

    private StoredFile createFile(User user,
                                  String path,
                                  String filename,
                                  boolean directory,
                                  String contentType,
                                  Long size,
                                  LocalDateTime deletedAt) {
        StoredFile file = new StoredFile();
        file.setUserId(user.getId());
        file.setPath(path);
        file.setFilename(filename);
        file.setDirectory(directory);
        file.setContentType(contentType);
        file.setSize(size);
        file.setDeletedAt(deletedAt);
        return file;
    }

    private StoredFile createBlobBackedFile(User user,
                                            String path,
                                            String filename,
                                            String contentType,
                                            String objectKeySuffix,
                                            byte[] content) {
        return createBlobBackedFile(user, path, filename, contentType, objectKeySuffix, content, null);
    }

    private StoredFile createBlobBackedFile(User user,
                                            String path,
                                            String filename,
                                            String contentType,
                                            String objectKeySuffix,
                                            byte[] content,
                                            LocalDateTime deletedAt) {
        String objectKey = "blobs/test-background-task/" + objectKeySuffix;
        fileContentStorage.storeBlob(objectKey, contentType, content);

        FileBlob blob = new FileBlob();
        blob.setObjectKey(objectKey);
        blob.setContentType(contentType);
        blob.setSize((long) content.length);
        blob = fileBlobRepository.save(blob);

        StoredFile file = createFile(user, path, filename, false, contentType, (long) content.length, deletedAt);
        file.setBlobId(blob == null ? null : blob.getId());
        return file;
    }

    private byte[] createZipArchive(Map<String, String> entries) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream, StandardCharsets.UTF_8)) {
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                zipOutputStream.putNextEntry(new ZipEntry(entry.getKey()));
                if (!entry.getKey().endsWith("/")) {
                    zipOutputStream.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                }
                zipOutputStream.closeEntry();
            }
        }
        return outputStream.toByteArray();
    }

    private Map<String, Object> readPublicState(String taskResponse) throws Exception {
        String publicStateJson = JsonPath.read(taskResponse, "$.data.publicStateJson");
        return objectMapper.readValue(publicStateJson, new TypeReference<Map<String, Object>>() {
        });
    }
}
