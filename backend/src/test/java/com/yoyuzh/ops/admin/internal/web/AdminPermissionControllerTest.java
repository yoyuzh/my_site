package com.yoyuzh.ops.admin.internal.web;

import com.yoyuzh.PortalBackendApplication;
import com.yoyuzh.identity.access.internal.domain.User;
import com.yoyuzh.identity.access.internal.domain.UserRole;
import com.yoyuzh.identity.access.internal.infra.UserRepository;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        classes = PortalBackendApplication.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:admin_permission_controller_test;MODE=MySQL;DB_CLOSE_DELAY=-1",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "app.jwt.secret=0123456789abcdef0123456789abcdef",
                "app.storage.root-dir=./target/test-storage-admin-permissions"
        }
)
@AutoConfigureMockMvc
class AdminPermissionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        saveUser("alice", "USER");
        saveUser("moderator", "MODERATOR");
    }

    @Test
    void shouldRequireAdminAccessForPermissionEndpoint() throws Exception {
        mockMvc.perform(get("/api/admin/permissions").with(user("alice").roles("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldReturnPermissionsForAdminCapableUser() throws Exception {
        mockMvc.perform(get("/api/admin/permissions").with(user("moderator").roles("MODERATOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.msg").value("success"))
                .andExpect(jsonPath("$.data.permissions").isArray())
                .andExpect(jsonPath("$.data.permissions[0]").value("admin.overview.read"));
    }

    private void saveUser(String username, String roleName) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(username + "@example.com");
        user.setPhoneNumber("1380013800" + (userRepository.count() + 1));
        user.setPasswordHash(passwordEncoder.encode("password"));
        user.setRole(UserRole.valueOf(roleName));
        user.setCreatedAt(LocalDateTime.now());
        userRepository.save(user);
    }
}
