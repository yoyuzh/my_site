package com.yoyuzh.admin;

import com.yoyuzh.PortalBackendApplication;
import com.yoyuzh.auth.User;
import com.yoyuzh.auth.UserRepository;
import com.yoyuzh.files.StoredFile;
import com.yoyuzh.files.StoredFileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

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
                "spring.datasource.url=jdbc:h2:mem:admin_api_test;MODE=MySQL;DB_CLOSE_DELAY=-1",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "app.jwt.secret=0123456789abcdef0123456789abcdef",
                "app.admin.usernames=admin",
                "app.storage.root-dir=./target/test-storage-admin"
        }
)
@AutoConfigureMockMvc
class AdminControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StoredFileRepository storedFileRepository;

    private User portalUser;
    private User secondaryUser;
    private StoredFile storedFile;
    private StoredFile secondaryFile;

    @BeforeEach
    void setUp() {
        storedFileRepository.deleteAll();
        userRepository.deleteAll();

        portalUser = new User();
        portalUser.setUsername("alice");
        portalUser.setEmail("alice@example.com");
        portalUser.setPhoneNumber("13800138000");
        portalUser.setPasswordHash("encoded-password");
        portalUser.setCreatedAt(LocalDateTime.now());
        portalUser = userRepository.save(portalUser);

        secondaryUser = new User();
        secondaryUser.setUsername("bob");
        secondaryUser.setEmail("bob@example.com");
        secondaryUser.setPhoneNumber("13900139000");
        secondaryUser.setPasswordHash("encoded-password");
        secondaryUser.setCreatedAt(LocalDateTime.now().minusDays(1));
        secondaryUser = userRepository.save(secondaryUser);

        storedFile = new StoredFile();
        storedFile.setUser(portalUser);
        storedFile.setFilename("report.pdf");
        storedFile.setPath("/");
        storedFile.setStorageName("report.pdf");
        storedFile.setContentType("application/pdf");
        storedFile.setSize(1024L);
        storedFile.setDirectory(false);
        storedFile.setCreatedAt(LocalDateTime.now());
        storedFile = storedFileRepository.save(storedFile);

        secondaryFile = new StoredFile();
        secondaryFile.setUser(secondaryUser);
        secondaryFile.setFilename("notes.txt");
        secondaryFile.setPath("/docs");
        secondaryFile.setStorageName("notes.txt");
        secondaryFile.setContentType("text/plain");
        secondaryFile.setSize(256L);
        secondaryFile.setDirectory(false);
        secondaryFile.setCreatedAt(LocalDateTime.now().minusHours(2));
        secondaryFile = storedFileRepository.save(secondaryFile);
    }

    @Test
    @WithMockUser(username = "admin")
    void shouldAllowConfiguredAdminToListUsersAndSummary() throws Exception {
        mockMvc.perform(get("/api/admin/users?page=0&size=10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.items[0].username").value("alice"))
                .andExpect(jsonPath("$.data.items[0].phoneNumber").value("13800138000"))
                .andExpect(jsonPath("$.data.items[0].role").value("USER"))
                .andExpect(jsonPath("$.data.items[0].banned").value(false));

        mockMvc.perform(get("/api/admin/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalUsers").value(2))
                .andExpect(jsonPath("$.data.totalFiles").value(2));
    }

    @Test
    @WithMockUser(username = "admin")
    void shouldSupportUserSearchPasswordAndStatusManagement() throws Exception {
        mockMvc.perform(get("/api/admin/users?page=0&size=10&query=ali"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].username").value("alice"));

        mockMvc.perform(get("/api/admin/users?page=0&size=10&query=13900139000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].username").value("bob"))
                .andExpect(jsonPath("$.data.items[0].phoneNumber").value("13900139000"));

        mockMvc.perform(patch("/api/admin/users/{userId}/role", portalUser.getId())
                        .contentType("application/json")
                        .content("""
                                {"role":"ADMIN"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("ADMIN"));

        mockMvc.perform(patch("/api/admin/users/{userId}/status", portalUser.getId())
                        .contentType("application/json")
                        .content("""
                                {"banned":true}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.banned").value(true));

        mockMvc.perform(put("/api/admin/users/{userId}/password", portalUser.getId())
                        .contentType("application/json")
                        .content("""
                                {"newPassword":"AdminSetPass1!"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(portalUser.getId()));

        mockMvc.perform(post("/api/admin/users/{userId}/password/reset", secondaryUser.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.temporaryPassword").isNotEmpty());
    }

    @Test
    @WithMockUser(username = "admin")
    void shouldAllowConfiguredAdminToListAndDeleteFiles() throws Exception {
        mockMvc.perform(get("/api/admin/files?page=0&size=10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].filename").value("report.pdf"))
                .andExpect(jsonPath("$.data.items[0].ownerUsername").value("alice"));

        mockMvc.perform(get("/api/admin/files?page=0&size=10&query=report&ownerQuery=ali"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].filename").value("report.pdf"));

        mockMvc.perform(delete("/api/admin/files/{fileId}", storedFile.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    @WithMockUser(username = "portal-user")
    void shouldRejectNonAdminUser() throws Exception {
        mockMvc.perform(get("/api/admin/users?page=0&size=10"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.msg").value("没有权限访问该资源"));
    }
}
