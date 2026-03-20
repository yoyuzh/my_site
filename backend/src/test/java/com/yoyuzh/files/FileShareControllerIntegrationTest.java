package com.yoyuzh.files;

import com.yoyuzh.PortalBackendApplication;
import com.yoyuzh.auth.User;
import com.yoyuzh.auth.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        classes = PortalBackendApplication.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:file_share_api_test;MODE=MySQL;DB_CLOSE_DELAY=-1",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "app.jwt.secret=0123456789abcdef0123456789abcdef",
                "app.storage.root-dir=./target/test-storage-file-share"
        }
)
@AutoConfigureMockMvc
class FileShareControllerIntegrationTest {

    private static final Path STORAGE_ROOT = Path.of("./target/test-storage-file-share").toAbsolutePath().normalize();
    private Long sharedFileId;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StoredFileRepository storedFileRepository;

    @Autowired
    private FileShareLinkRepository fileShareLinkRepository;

    @BeforeEach
    void setUp() throws Exception {
        fileShareLinkRepository.deleteAll();
        storedFileRepository.deleteAll();
        userRepository.deleteAll();
        if (Files.exists(STORAGE_ROOT)) {
            try (var paths = Files.walk(STORAGE_ROOT)) {
                paths.sorted((left, right) -> right.compareTo(left)).forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (Exception ex) {
                        throw new RuntimeException(ex);
                    }
                });
            }
        }
        Files.createDirectories(STORAGE_ROOT);

        User owner = new User();
        owner.setUsername("alice");
        owner.setEmail("alice@example.com");
        owner.setPhoneNumber("13800138000");
        owner.setPasswordHash("encoded-password");
        owner.setCreatedAt(LocalDateTime.now());
        owner = userRepository.save(owner);

        User recipient = new User();
        recipient.setUsername("bob");
        recipient.setEmail("bob@example.com");
        recipient.setPhoneNumber("13800138001");
        recipient.setPasswordHash("encoded-password");
        recipient.setCreatedAt(LocalDateTime.now());
        recipient = userRepository.save(recipient);

        StoredFile file = new StoredFile();
        file.setUser(owner);
        file.setFilename("notes.txt");
        file.setPath("/docs");
        file.setStorageName("notes.txt");
        file.setContentType("text/plain");
        file.setSize(5L);
        file.setDirectory(false);
        sharedFileId = storedFileRepository.save(file).getId();

        Path ownerDir = STORAGE_ROOT.resolve(owner.getId().toString()).resolve("docs");
        Files.createDirectories(ownerDir);
        Files.writeString(ownerDir.resolve("notes.txt"), "hello", StandardCharsets.UTF_8);
    }

    @Test
    void shouldCreateInspectAndImportSharedFile() throws Exception {
        String response = mockMvc.perform(post("/api/files/{fileId}/share-links", sharedFileId)
                        .with(user("alice")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andExpect(jsonPath("$.data.filename").value("notes.txt"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String token = com.jayway.jsonpath.JsonPath.read(response, "$.data.token");

        mockMvc.perform(get("/api/files/share-links/{token}", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").value(token))
                .andExpect(jsonPath("$.data.filename").value("notes.txt"))
                .andExpect(jsonPath("$.data.ownerUsername").value("alice"));

        mockMvc.perform(post("/api/files/share-links/{token}/import", token)
                        .with(anonymous())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "path": "/下载"
                                }
                                """))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/files/share-links/{token}/import", token)
                        .with(user("bob"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "path": "/下载"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.filename").value("notes.txt"))
                .andExpect(jsonPath("$.data.path").value("/下载"));

        mockMvc.perform(get("/api/files/list")
                        .with(user("bob"))
                        .param("path", "/下载")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].filename").value("notes.txt"));
    }

    @Test
    void shouldMoveFileIntoAnotherDirectoryThroughApi() throws Exception {
        User owner = userRepository.findByUsername("alice").orElseThrow();

        StoredFile downloadDirectory = new StoredFile();
        downloadDirectory.setUser(owner);
        downloadDirectory.setFilename("下载");
        downloadDirectory.setPath("/");
        downloadDirectory.setStorageName("下载");
        downloadDirectory.setContentType("directory");
        downloadDirectory.setSize(0L);
        downloadDirectory.setDirectory(true);
        storedFileRepository.save(downloadDirectory);
        Files.createDirectories(STORAGE_ROOT.resolve(owner.getId().toString()).resolve("下载"));

        mockMvc.perform(patch("/api/files/{fileId}/move", sharedFileId)
                        .with(user("alice"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "path": "/下载"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.filename").value("notes.txt"))
                .andExpect(jsonPath("$.data.path").value("/下载"));

        mockMvc.perform(get("/api/files/list")
                        .with(user("alice"))
                        .param("path", "/下载")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].filename").value("notes.txt"));
    }

    @Test
    void shouldCopyFileIntoAnotherDirectoryThroughApi() throws Exception {
        User owner = userRepository.findByUsername("alice").orElseThrow();

        StoredFile downloadDirectory = new StoredFile();
        downloadDirectory.setUser(owner);
        downloadDirectory.setFilename("下载");
        downloadDirectory.setPath("/");
        downloadDirectory.setStorageName("下载");
        downloadDirectory.setContentType("directory");
        downloadDirectory.setSize(0L);
        downloadDirectory.setDirectory(true);
        storedFileRepository.save(downloadDirectory);
        Files.createDirectories(STORAGE_ROOT.resolve(owner.getId().toString()).resolve("下载"));

        mockMvc.perform(post("/api/files/{fileId}/copy", sharedFileId)
                        .with(user("alice"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "path": "/下载"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.filename").value("notes.txt"))
                .andExpect(jsonPath("$.data.path").value("/下载"));

        mockMvc.perform(get("/api/files/list")
                        .with(user("alice"))
                        .param("path", "/下载")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].filename").value("notes.txt"));
    }
}
