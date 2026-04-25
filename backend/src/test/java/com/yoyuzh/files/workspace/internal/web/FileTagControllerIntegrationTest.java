package com.yoyuzh.files.workspace.internal.web;

import com.yoyuzh.PortalBackendApplication;
import com.yoyuzh.files.workspace.internal.domain.StoredFile;
import com.yoyuzh.files.workspace.internal.infra.StoredFileRepository;
import com.yoyuzh.identity.access.internal.domain.User;
import com.yoyuzh.identity.access.internal.infra.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        classes = PortalBackendApplication.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:file_tag_api_test;MODE=MySQL;DB_CLOSE_DELAY=-1",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "app.jwt.secret=0123456789abcdef0123456789abcdef"
        }
)
@AutoConfigureMockMvc
class FileTagControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StoredFileRepository storedFileRepository;

    private Long ownerFolderId;
    private Long ownerFileId;

    @BeforeEach
    void setUp() {
        storedFileRepository.deleteAll();
        userRepository.deleteAll();

        User owner = new User();
        owner.setUsername("alice");
        owner.setEmail("alice@example.com");
        owner.setPhoneNumber("13800138000");
        owner.setPasswordHash("encoded-password");
        owner.setCreatedAt(LocalDateTime.now());
        owner = userRepository.save(owner);

        User otherUser = new User();
        otherUser.setUsername("bob");
        otherUser.setEmail("bob@example.com");
        otherUser.setPhoneNumber("13800138001");
        otherUser.setPasswordHash("encoded-password");
        otherUser.setCreatedAt(LocalDateTime.now());
        userRepository.save(otherUser);

        StoredFile folder = new StoredFile();
        folder.setUserId(owner.getId());
        folder.setFilename("docs");
        folder.setPath("/");
        folder.setContentType("directory");
        folder.setSize(0L);
        folder.setDirectory(true);
        ownerFolderId = storedFileRepository.save(folder).getId();

        StoredFile file = new StoredFile();
        file.setUserId(owner.getId());
        file.setFilename("notes.txt");
        file.setPath("/");
        file.setContentType("text/plain");
        file.setSize(5L);
        file.setDirectory(false);
        ownerFileId = storedFileRepository.save(file).getId();
    }

    @Test
    void shouldCreateAssignUpdateListAndDeleteWorkspaceTags() throws Exception {
        String createResponse = mockMvc.perform(post("/api/files/tags")
                        .with(user("alice"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "项目",
                                  "color": "#2563EB"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("项目"))
                .andExpect(jsonPath("$.data.color").value("#2563EB"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        Long tagId = ((Number) com.jayway.jsonpath.JsonPath.read(createResponse, "$.data.id")).longValue();

        mockMvc.perform(get("/api/files/tags")
                        .with(user("alice")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].name").value("项目"))
                .andExpect(jsonPath("$.data[0].color").value("#2563EB"));

        mockMvc.perform(put("/api/files/{fileId}/tags/{tagId}", ownerFolderId, tagId)
                        .with(user("alice")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(tagId))
                .andExpect(jsonPath("$.data[0].name").value("项目"));

        mockMvc.perform(get("/api/files/{fileId}/tags", ownerFolderId)
                        .with(user("alice")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].name").value("项目"))
                .andExpect(jsonPath("$.data[0].color").value("#2563EB"));

        mockMvc.perform(get("/api/files/{fileId}/detail", ownerFolderId)
                        .with(user("alice")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tags[0].name").value("项目"))
                .andExpect(jsonPath("$.data.tags[0].color").value("#2563EB"));

        mockMvc.perform(put("/api/files/{fileId}/tags/{tagId}", ownerFileId, tagId)
                        .with(user("alice")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.msg").value("只有文件夹支持标签"));

        mockMvc.perform(patch("/api/files/tags/{tagId}", tagId)
                        .with(user("alice"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "归档",
                                  "color": "#16A34A"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("归档"))
                .andExpect(jsonPath("$.data.color").value("#16A34A"));

        mockMvc.perform(delete("/api/files/{fileId}/tags/{tagId}", ownerFolderId, tagId)
                        .with(user("alice")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());

        mockMvc.perform(get("/api/files/{fileId}/tags", ownerFolderId)
                        .with(user("alice")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());

        mockMvc.perform(delete("/api/files/tags/{tagId}", tagId)
                        .with(user("alice")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());

        mockMvc.perform(get("/api/files/tags")
                        .with(user("alice")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void shouldRejectTagMutationForOtherUsersFileOrTag() throws Exception {
        String createResponse = mockMvc.perform(post("/api/files/tags")
                        .with(user("alice"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "私人",
                                  "color": "#9333EA"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        Long tagId = ((Number) com.jayway.jsonpath.JsonPath.read(createResponse, "$.data.id")).longValue();

        mockMvc.perform(put("/api/files/{fileId}/tags/{tagId}", ownerFolderId, tagId)
                        .with(user("bob")))
                .andExpect(status().isNotFound());

        mockMvc.perform(patch("/api/files/tags/{tagId}", tagId)
                        .with(user("bob"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "越权",
                                  "color": "#000000"
                                }
                                """))
                .andExpect(status().isNotFound());
    }
}
