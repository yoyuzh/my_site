package com.yoyuzh.api.v2.shares;

import com.jayway.jsonpath.JsonPath;
import com.yoyuzh.PortalBackendApplication;
import com.yoyuzh.auth.User;
import com.yoyuzh.auth.UserRepository;
import com.yoyuzh.files.FileBlob;
import com.yoyuzh.files.FileBlobRepository;
import com.yoyuzh.files.FileShareLink;
import com.yoyuzh.files.FileShareLinkRepository;
import com.yoyuzh.files.StoredFile;
import com.yoyuzh.files.StoredFileRepository;
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
import java.util.Comparator;

import static org.hamcrest.Matchers.nullValue;
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
                "spring.datasource.url=jdbc:h2:mem:share_v2_api_test;MODE=MySQL;DB_CLOSE_DELAY=-1",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "app.jwt.secret=0123456789abcdef0123456789abcdef",
                "app.storage.root-dir=./target/test-storage-share-v2"
        }
)
@AutoConfigureMockMvc
class ShareV2ControllerIntegrationTest {

    private static final Path STORAGE_ROOT = Path.of("./target/test-storage-share-v2").toAbsolutePath().normalize();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StoredFileRepository storedFileRepository;

    @Autowired
    private FileBlobRepository fileBlobRepository;

    @Autowired
    private FileShareLinkRepository fileShareLinkRepository;

    private Long sharedFileId;

    @BeforeEach
    void setUp() throws Exception {
        fileShareLinkRepository.deleteAll();
        storedFileRepository.deleteAll();
        fileBlobRepository.deleteAll();
        userRepository.deleteAll();
        if (Files.exists(STORAGE_ROOT)) {
            try (var paths = Files.walk(STORAGE_ROOT)) {
                paths.sorted(Comparator.reverseOrder()).forEach(path -> {
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
        userRepository.save(recipient);

        FileBlob blob = new FileBlob();
        blob.setObjectKey("blobs/share-v2-notes");
        blob.setContentType("text/plain");
        blob.setSize(5L);
        blob.setCreatedAt(LocalDateTime.now());
        blob = fileBlobRepository.save(blob);

        StoredFile file = new StoredFile();
        file.setUser(owner);
        file.setFilename("notes.txt");
        file.setPath("/docs");
        file.setContentType("text/plain");
        file.setSize(5L);
        file.setDirectory(false);
        file.setBlob(blob);
        sharedFileId = storedFileRepository.save(file).getId();

        Path blobPath = STORAGE_ROOT.resolve("blobs").resolve("share-v2-notes");
        Files.createDirectories(blobPath.getParent());
        Files.writeString(blobPath, "hello", StandardCharsets.UTF_8);
    }

    @Test
    void shouldCreateReadVerifyImportAndDeleteOwnV2Share() throws Exception {
        String createResponse = mockMvc.perform(post("/api/v2/shares")
                        .with(user("alice"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fileId": %d,
                                  "password": "Share123",
                                  "shareName": "course-share",
                                  "allowImport": true,
                                  "allowDownload": true
                                }
                                """.formatted(sharedFileId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andExpect(jsonPath("$.data.shareName").value("course-share"))
                .andExpect(jsonPath("$.data.passwordRequired").value(true))
                .andExpect(jsonPath("$.data.file.filename").value("notes.txt"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String token = JsonPath.read(createResponse, "$.data.token");
        Long shareId = ((Number) JsonPath.read(createResponse, "$.data.id")).longValue();

        mockMvc.perform(get("/api/v2/shares/{token}", token).with(anonymous()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.passwordRequired").value(true))
                .andExpect(jsonPath("$.data.passwordVerified").value(false))
                .andExpect(jsonPath("$.data.file").value(nullValue()));

        mockMvc.perform(post("/api/v2/shares/{token}/verify-password", token)
                        .with(anonymous())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "password": "WrongPass1!"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(2400));

        mockMvc.perform(post("/api/v2/shares/{token}/verify-password", token)
                        .with(anonymous())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "password": "Share123"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.passwordVerified").value(true))
                .andExpect(jsonPath("$.data.file.filename").value("notes.txt"));

        mockMvc.perform(post("/api/v2/shares/{token}/import", token)
                        .with(user("bob"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "path": "/download",
                                  "password": "Share123"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.filename").value("notes.txt"))
                .andExpect(jsonPath("$.data.path").value("/download"));

        mockMvc.perform(get("/api/v2/shares/mine")
                        .with(user("alice"))
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].id").value(shareId))
                .andExpect(jsonPath("$.data.items[0].file.filename").value("notes.txt"));

        mockMvc.perform(delete("/api/v2/shares/{id}", shareId)
                        .with(user("alice")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(get("/api/v2/shares/mine")
                        .with(user("alice"))
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));
    }

    @Test
    void shouldRejectDisabledOrExpiredV2ShareImports() throws Exception {
        String disabledResponse = mockMvc.perform(post("/api/v2/shares")
                        .with(user("alice"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fileId": %d,
                                  "allowImport": false,
                                  "allowDownload": true
                                }
                                """.formatted(sharedFileId)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String disabledToken = JsonPath.read(disabledResponse, "$.data.token");

        mockMvc.perform(post("/api/v2/shares/{token}/import", disabledToken)
                        .with(user("bob"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "path": "/download"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(2403));

        String expiringResponse = mockMvc.perform(post("/api/v2/shares")
                        .with(user("alice"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fileId": %d,
                                  "allowImport": true,
                                  "allowDownload": true
                                }
                                """.formatted(sharedFileId)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String expiringToken = JsonPath.read(expiringResponse, "$.data.token");

        FileShareLink expiringShare = fileShareLinkRepository.findByToken(expiringToken).orElseThrow();
        expiringShare.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        fileShareLinkRepository.save(expiringShare);

        mockMvc.perform(post("/api/v2/shares/{token}/import", expiringToken)
                        .with(user("bob"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "path": "/download"
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(2404));
    }

    @Test
    void shouldDenyDeletingOtherUsersShare() throws Exception {
        String createResponse = mockMvc.perform(post("/api/v2/shares")
                        .with(user("alice"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fileId": %d
                                }
                                """.formatted(sharedFileId)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        Long shareId = ((Number) JsonPath.read(createResponse, "$.data.id")).longValue();

        mockMvc.perform(delete("/api/v2/shares/{id}", shareId)
                        .with(user("bob")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(2404));
    }
}
