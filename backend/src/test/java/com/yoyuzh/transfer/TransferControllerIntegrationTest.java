package com.yoyuzh.transfer;

import com.yoyuzh.PortalBackendApplication;
import com.yoyuzh.auth.User;
import com.yoyuzh.auth.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        classes = PortalBackendApplication.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:transfer_api_test;MODE=MySQL;DB_CLOSE_DELAY=-1",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "app.jwt.secret=0123456789abcdef0123456789abcdef",
                "app.storage.root-dir=./target/test-storage-transfer"
        }
)
@AutoConfigureMockMvc
class TransferControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
      userRepository.deleteAll();

      User portalUser = new User();
      portalUser.setUsername("alice");
      portalUser.setEmail("alice@example.com");
      portalUser.setPhoneNumber("13800138000");
      portalUser.setPasswordHash("encoded-password");
      portalUser.setCreatedAt(LocalDateTime.now());
      userRepository.save(portalUser);
    }

    @Test
    @WithMockUser(username = "alice")
    void shouldCreateLookupJoinAndPollTransferSignals() throws Exception {
        String response = mockMvc.perform(post("/api/transfer/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "mode": "ONLINE",
                                  "files": [
                                    {"name": "report.pdf", "relativePath": "课程资料/report.pdf", "size": 2048, "contentType": "application/pdf"}
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.sessionId").isNotEmpty())
                .andExpect(jsonPath("$.data.pickupCode").isString())
                .andExpect(jsonPath("$.data.mode").value("ONLINE"))
                .andExpect(jsonPath("$.data.files[0].name").value("report.pdf"))
                .andExpect(jsonPath("$.data.files[0].relativePath").value("课程资料/report.pdf"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String sessionId = com.jayway.jsonpath.JsonPath.read(response, "$.data.sessionId");
        String pickupCode = com.jayway.jsonpath.JsonPath.read(response, "$.data.pickupCode");

        mockMvc.perform(get("/api/transfer/sessions/lookup").param("pickupCode", pickupCode))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sessionId").value(sessionId))
                .andExpect(jsonPath("$.data.pickupCode").value(pickupCode))
                .andExpect(jsonPath("$.data.mode").value("ONLINE"));

        mockMvc.perform(post("/api/transfer/sessions/{sessionId}/join", sessionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sessionId").value(sessionId))
                .andExpect(jsonPath("$.data.mode").value("ONLINE"))
                .andExpect(jsonPath("$.data.files[0].name").value("report.pdf"));

        mockMvc.perform(post("/api/transfer/sessions/{sessionId}/signals", sessionId)
                        .param("role", "sender")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "type": "offer",
                                  "payload": "{\\\"sdp\\\":\\\"demo-offer\\\"}"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(get("/api/transfer/sessions/{sessionId}/signals", sessionId)
                        .param("role", "receiver")
                        .param("after", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].type").value("offer"))
                .andExpect(jsonPath("$.data.items[0].payload").value("{\"sdp\":\"demo-offer\"}"))
                .andExpect(jsonPath("$.data.nextCursor").value(1));
    }

    @Test
    void shouldRejectAnonymousSessionCreationButAllowPublicJoinEndpoints() throws Exception {
        mockMvc.perform(post("/api/transfer/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"mode":"ONLINE","files":[{"name":"demo.txt","relativePath":"demo.txt","size":12,"contentType":"text/plain"}]}
                                """))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/transfer/sessions/{sessionId}/join", "missing-session"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = "alice")
    void shouldPersistOfflineTransfersForSevenDaysAndAllowRepeatedDownloads() throws Exception {
        String response = mockMvc.perform(post("/api/transfer/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "mode": "OFFLINE",
                                  "files": [
                                    {"name": "offline.txt", "relativePath": "资料/offline.txt", "size": 13, "contentType": "text/plain"}
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mode").value("OFFLINE"))
                .andExpect(jsonPath("$.data.files[0].id").isString())
                .andExpect(jsonPath("$.data.files[0].relativePath").value("资料/offline.txt"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String sessionId = com.jayway.jsonpath.JsonPath.read(response, "$.data.sessionId");
        String pickupCode = com.jayway.jsonpath.JsonPath.read(response, "$.data.pickupCode");
        String fileId = com.jayway.jsonpath.JsonPath.read(response, "$.data.files[0].id");
        String expiresAtRaw = com.jayway.jsonpath.JsonPath.read(response, "$.data.expiresAt");

        Instant expiresAt = Instant.parse(expiresAtRaw);
        assertThat(expiresAt).isAfter(Instant.now().plusSeconds(6 * 24 * 60 * 60L));

        MockMultipartFile offlineFile = new MockMultipartFile(
                "file",
                "offline.txt",
                MediaType.TEXT_PLAIN_VALUE,
                "hello offline".getBytes(StandardCharsets.UTF_8)
        );

        mockMvc.perform(multipart("/api/transfer/sessions/{sessionId}/files/{fileId}/content", sessionId, fileId)
                        .file(offlineFile))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(get("/api/transfer/sessions/lookup").param("pickupCode", pickupCode))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sessionId").value(sessionId))
                .andExpect(jsonPath("$.data.mode").value("OFFLINE"));

        mockMvc.perform(post("/api/transfer/sessions/{sessionId}/join", sessionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mode").value("OFFLINE"))
                .andExpect(jsonPath("$.data.files[0].name").value("offline.txt"));

        mockMvc.perform(get("/api/transfer/sessions/{sessionId}/files/{fileId}/download", sessionId, fileId))
                .andExpect(status().isOk())
                .andExpect(content().bytes("hello offline".getBytes(StandardCharsets.UTF_8)));

        mockMvc.perform(get("/api/transfer/sessions/{sessionId}/files/{fileId}/download", sessionId, fileId))
                .andExpect(status().isOk())
                .andExpect(content().bytes("hello offline".getBytes(StandardCharsets.UTF_8)));
    }
}
