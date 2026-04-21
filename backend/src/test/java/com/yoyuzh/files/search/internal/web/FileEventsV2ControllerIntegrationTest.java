package com.yoyuzh.files.search.internal.web;

import com.yoyuzh.PortalBackendApplication;
import com.yoyuzh.identity.access.internal.domain.User;
import com.yoyuzh.identity.access.internal.infra.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        classes = PortalBackendApplication.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:file_events_api_test;MODE=MySQL;DB_CLOSE_DELAY=-1",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "app.jwt.secret=0123456789abcdef0123456789abcdef",
                "app.storage.root-dir=./target/test-storage-file-events"
        }
)
@AutoConfigureMockMvc
class FileEventsV2ControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        User user = new User();
        user.setUsername("alice");
        user.setEmail("alice@example.com");
        user.setPhoneNumber("13800138000");
        user.setPasswordHash("encoded-password");
        user.setCreatedAt(LocalDateTime.now());
        userRepository.save(user);
    }

    @Test
    void shouldRequireAuthenticationForFileEventStream() throws Exception {
        mockMvc.perform(get("/api/v2/files/events").with(anonymous()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldOpenStreamAndSendReadyEvent() throws Exception {
        var result = mockMvc.perform(get("/api/v2/files/events")
                        .with(user("alice"))
                        .param("path", "/docs")
                        .header("X-Yoyuzh-Client-Id", "tab-1"))
                .andExpect(request().asyncStarted())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(result.getResponse().getContentType()).startsWith("text/event-stream");
        assertThat(body).contains("READY");
        assertThat(body).contains("/docs");
        assertThat(body).contains("tab-1");
    }
}
