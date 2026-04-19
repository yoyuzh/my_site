package com.yoyuzh.ops.admin.internal.application;

import com.yoyuzh.PortalBackendApplication;
import com.yoyuzh.auth.RefreshTokenRepository;
import com.yoyuzh.auth.RegistrationInviteStateRepository;
import com.yoyuzh.auth.User;
import com.yoyuzh.auth.UserRepository;
import com.yoyuzh.files.core.FileBlob;
import com.yoyuzh.files.core.FileBlobRepository;
import com.yoyuzh.files.core.FileEntity;
import com.yoyuzh.files.core.FileEntityRepository;
import com.yoyuzh.files.core.FileEntityType;
import com.yoyuzh.files.core.StoredFile;
import com.yoyuzh.files.core.StoredFileEntity;
import com.yoyuzh.files.core.StoredFileEntityRepository;
import com.yoyuzh.files.core.StoredFileRepository;
import com.yoyuzh.files.policy.StoragePolicy;
import com.yoyuzh.files.policy.StoragePolicyRepository;
import com.yoyuzh.files.policy.StoragePolicyType;
import com.yoyuzh.files.share.FileShareLink;
import com.yoyuzh.files.share.FileShareLinkRepository;
import com.yoyuzh.files.tasks.BackgroundTask;
import com.yoyuzh.platform.job.api.BackgroundTaskFailureCategory;
import com.yoyuzh.files.tasks.BackgroundTaskRepository;
import com.yoyuzh.platform.job.api.BackgroundTaskStatus;
import com.yoyuzh.platform.job.api.BackgroundTaskType;
import com.yoyuzh.ops.admin.api.AdminSettingsUpdateRequest;
import com.yoyuzh.ops.admin.internal.infra.AdminAuditLogRepository;
import com.yoyuzh.ops.admin.internal.infra.AdminMetricsStateRepository;
import com.yoyuzh.ops.admin.internal.infra.AdminRuntimeSettingsStateRepository;
import com.yoyuzh.transfer.OfflineTransferSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
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
    private PasswordEncoder passwordEncoder;

    @Autowired
    private StoredFileRepository storedFileRepository;
    @Autowired
    private FileBlobRepository fileBlobRepository;
    @Autowired
    private FileEntityRepository fileEntityRepository;
    @Autowired
    private StoredFileEntityRepository storedFileEntityRepository;
    @Autowired
    private FileShareLinkRepository fileShareLinkRepository;
    @Autowired
    private BackgroundTaskRepository backgroundTaskRepository;
    @Autowired
    private RefreshTokenRepository refreshTokenRepository;
    @Autowired
    private OfflineTransferSessionRepository offlineTransferSessionRepository;
    @Autowired
    private AdminMetricsStateRepository adminMetricsStateRepository;
    @Autowired
    private RegistrationInviteStateRepository registrationInviteStateRepository;
    @Autowired
    private AdminAuditLogRepository adminAuditLogRepository;
    @Autowired
    private AdminMetricsService adminMetricsService;
    @Autowired
    private AdminRuntimeSettingsService adminRuntimeSettingsService;
    @Autowired
    private StoragePolicyRepository storagePolicyRepository;

    private User portalUser;
    private User secondaryUser;
    private StoredFile storedFile;
    private StoredFile secondaryFile;

    @BeforeEach
    void setUp() {
        backgroundTaskRepository.deleteAll();
        fileShareLinkRepository.deleteAll();
        storedFileEntityRepository.deleteAll();
        offlineTransferSessionRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        storedFileRepository.deleteAll();
        fileEntityRepository.deleteAll();
        fileBlobRepository.deleteAll();
        userRepository.deleteAll();
        adminMetricsStateRepository.deleteAll();
        registrationInviteStateRepository.deleteAll();
        adminAuditLogRepository.deleteAll();
        adminRuntimeSettingsService.reset();

        Long defaultPolicyId = storagePolicyRepository.findFirstByDefaultPolicyTrueOrderByIdAsc()
                .map(StoragePolicy::getId)
                .orElse(null);

        portalUser = new User();
        portalUser.setUsername("alice");
        portalUser.setEmail("alice@example.com");
        portalUser.setPhoneNumber("13800138000");
        portalUser.setPasswordHash(passwordEncoder.encode("OriginalA"));
        portalUser.setCreatedAt(LocalDateTime.now());
        portalUser = userRepository.save(portalUser);

        secondaryUser = new User();
        secondaryUser.setUsername("bob");
        secondaryUser.setEmail("bob@example.com");
        secondaryUser.setPhoneNumber("13900139000");
        secondaryUser.setPasswordHash(passwordEncoder.encode("OriginalB"));
        secondaryUser.setCreatedAt(LocalDateTime.now().minusDays(1));
        secondaryUser = userRepository.save(secondaryUser);

        FileBlob reportBlob = createBlob("blobs/admin-report", "application/pdf", 1024L);
        FileEntity reportEntity = createEntity(
                "blobs/admin-report",
                FileEntityType.VERSION,
                defaultPolicyId,
                1024L,
                "application/pdf",
                1,
                portalUser,
                LocalDateTime.now().minusMinutes(10)
        );
        storedFile = new StoredFile();
        storedFile.setUser(portalUser);
        storedFile.setFilename("report.pdf");
        storedFile.setPath("/");
        storedFile.setContentType("application/pdf");
        storedFile.setSize(1024L);
        storedFile.setDirectory(false);
        storedFile.setBlob(reportBlob);
        storedFile.setPrimaryEntity(reportEntity);
        storedFile.setCreatedAt(LocalDateTime.now());
        storedFile = storedFileRepository.save(storedFile);
        createRelation(storedFile, reportEntity, "PRIMARY");

        FileBlob notesBlob = createBlob("blobs/admin-notes", "text/plain", 256L);
        FileEntity notesEntity = createEntity(
                "blobs/admin-notes",
                FileEntityType.VERSION,
                defaultPolicyId,
                256L,
                "text/plain",
                1,
                secondaryUser,
                LocalDateTime.now().minusHours(3)
        );
        secondaryFile = new StoredFile();
        secondaryFile.setUser(secondaryUser);
        secondaryFile.setFilename("notes.txt");
        secondaryFile.setPath("/docs");
        secondaryFile.setContentType("text/plain");
        secondaryFile.setSize(256L);
        secondaryFile.setDirectory(false);
        secondaryFile.setBlob(notesBlob);
        secondaryFile.setPrimaryEntity(notesEntity);
        secondaryFile.setCreatedAt(LocalDateTime.now().minusHours(2));
        secondaryFile = storedFileRepository.save(secondaryFile);
        createRelation(secondaryFile, notesEntity, "PRIMARY");
    }

    private FileBlob createBlob(String objectKey, String contentType, long size) {
        FileBlob blob = new FileBlob();
        blob.setObjectKey(objectKey);
        blob.setContentType(contentType);
        blob.setSize(size);
        blob.setCreatedAt(LocalDateTime.now());
        return fileBlobRepository.save(blob);
    }

    private FileEntity createEntity(String objectKey,
                                    FileEntityType entityType,
                                    Long storagePolicyId,
                                    long size,
                                    String contentType,
                                    int referenceCount,
                                    User createdBy,
                                    LocalDateTime createdAt) {
        FileEntity entity = new FileEntity();
        entity.setObjectKey(objectKey);
        entity.setEntityType(entityType);
        entity.setStoragePolicyId(storagePolicyId);
        entity.setSize(size);
        entity.setContentType(contentType);
        entity.setReferenceCount(referenceCount);
        entity.setCreatedBy(createdBy);
        entity.setCreatedAt(createdAt);
        return fileEntityRepository.save(entity);
    }

    private StoredFileEntity createRelation(StoredFile storedFile, FileEntity entity, String role) {
        StoredFileEntity relation = new StoredFileEntity();
        relation.setStoredFile(storedFile);
        relation.setFileEntity(entity);
        relation.setEntityRole(role);
        relation.setCreatedAt(LocalDateTime.now());
        return storedFileEntityRepository.save(relation);
    }

    @Test
    @WithMockUser(username = "service-admin", roles = "ADMIN")
    void shouldAllowAdminRoleToListUsersAndSummary() throws Exception {
        int currentHour = LocalTime.now().getHour();
        LocalDate today = LocalDate.now();
        adminMetricsService.recordUserOnline(portalUser.getId(), portalUser.getUsername());
        adminMetricsService.recordUserOnline(secondaryUser.getId(), secondaryUser.getUsername());

        mockMvc.perform(get("/api/admin/users?page=0&size=10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.items[0].username").value("alice"))
                .andExpect(jsonPath("$.data.items[0].phoneNumber").value("13800138000"))
                .andExpect(jsonPath("$.data.items[0].role").value("USER"))
                .andExpect(jsonPath("$.data.items[0].banned").value(false))
                .andExpect(jsonPath("$.data.items[0].usedStorageBytes").value(1024L))
                .andExpect(jsonPath("$.data.items[0].storageQuotaBytes").isNumber())
                .andExpect(jsonPath("$.data.items[0].maxUploadSizeBytes").isNumber());

        mockMvc.perform(get("/api/admin/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalUsers").value(2))
                .andExpect(jsonPath("$.data.totalFiles").value(2))
                .andExpect(jsonPath("$.data.totalStorageBytes").value(1280L))
                .andExpect(jsonPath("$.data.downloadTrafficBytes").value(0L))
                .andExpect(jsonPath("$.data.requestCount", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data.requestTimeline.length()").value(currentHour + 1))
                .andExpect(jsonPath("$.data.requestTimeline[" + currentHour + "].hour").value(currentHour))
                .andExpect(jsonPath("$.data.requestTimeline[" + currentHour + "].label").value(String.format("%02d:00", currentHour)))
                .andExpect(jsonPath("$.data.requestTimeline[" + currentHour + "].requestCount", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data.transferUsageBytes").value(0L))
                .andExpect(jsonPath("$.data.offlineTransferStorageBytes").value(0L))
                .andExpect(jsonPath("$.data.offlineTransferStorageLimitBytes").isNumber())
                .andExpect(jsonPath("$.data.dailyActiveUsers.length()").value(7))
                .andExpect(jsonPath("$.data.dailyActiveUsers[6].metricDate").value(today.toString()))
                .andExpect(jsonPath("$.data.dailyActiveUsers[6].userCount").value(2))
                .andExpect(jsonPath("$.data.dailyActiveUsers[6].usernames[0]").value("alice"))
                .andExpect(jsonPath("$.data.dailyActiveUsers[6].usernames[1]").value("bob"))
                .andExpect(jsonPath("$.data.inviteCode").isNotEmpty());
    }

    @Test
    @WithMockUser(username = "service-admin", roles = "ADMIN")
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

        secondaryUser.setRole(com.yoyuzh.auth.UserRole.MODERATOR);
        secondaryUser = userRepository.save(secondaryUser);

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
                                {"newPassword":"AdminPass"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(portalUser.getId()));

        mockMvc.perform(patch("/api/admin/users/{userId}/storage-quota", portalUser.getId())
                        .contentType("application/json")
                        .content("""
                                {"storageQuotaBytes":1073741824}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(portalUser.getId()))
                .andExpect(jsonPath("$.data.storageQuotaBytes").value(1073741824L));

        mockMvc.perform(patch("/api/admin/users/{userId}/max-upload-size", portalUser.getId())
                        .contentType("application/json")
                        .content("""
                                {"maxUploadSizeBytes":10485760}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(portalUser.getId()))
                .andExpect(jsonPath("$.data.maxUploadSizeBytes").value(10485760L));

        mockMvc.perform(patch("/api/admin/settings/offline-transfer-storage-limit")
                        .contentType("application/json")
                        .content("""
                                {"offlineTransferStorageLimitBytes":2147483648}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.offlineTransferStorageLimitBytes").value(2147483648L));

        mockMvc.perform(post("/api/admin/users/{userId}/password/reset", secondaryUser.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.temporaryPassword").isNotEmpty());
    }

    @Test
    @WithMockUser(username = "service-admin", roles = "ADMIN")
    void shouldRejectInvalidOfflineTransferStorageLimitValues() throws Exception {
        mockMvc.perform(patch("/api/admin/settings/offline-transfer-storage-limit")
                        .contentType("application/json")
                        .content("""
                                {"offlineTransferStorageLimitBytes":0}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1000));

        mockMvc.perform(patch("/api/admin/settings/offline-transfer-storage-limit")
                        .contentType("application/json")
                        .content("""
                                {"offlineTransferStorageLimitBytes":-1}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1000));

        mockMvc.perform(patch("/api/admin/settings/offline-transfer-storage-limit")
                        .contentType("application/json")
                        .content("""
                                {}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1000));
    }

    @Test
    @WithMockUser(username = "service-admin", roles = "ADMIN")
    void shouldExposeSettingsAndFilesystemOverview() throws Exception {
        mockMvc.perform(get("/api/admin/settings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.site.supported").value(false))
                .andExpect(jsonPath("$.data.registration.inviteCodeRequired").value(true))
                .andExpect(jsonPath("$.data.registration.currentInviteCode").isNotEmpty())
                .andExpect(jsonPath("$.data.registration.managementRoles[0]").value("MODERATOR"))
                .andExpect(jsonPath("$.data.registration.managementRoles[1]").value("ADMIN"))
                .andExpect(jsonPath("$.data.registration.writeSupported").value(true))
                .andExpect(jsonPath("$.data.userSession.accessExpirationSeconds").value(900))
                .andExpect(jsonPath("$.data.userSession.refreshExpirationSeconds").value(1209600))
                .andExpect(jsonPath("$.data.site.writeSupported").value(false))
                .andExpect(jsonPath("$.data.userSession.writeSupported").value(false))
                .andExpect(jsonPath("$.data.transfer.offlineTransferStorageLimitBytes").isNumber())
                .andExpect(jsonPath("$.data.transfer.writeSupported").value(true))
                .andExpect(jsonPath("$.data.queue.backend").value("in-memory"))
                .andExpect(jsonPath("$.data.mediaProcessing.writeSupported").value(false))
                .andExpect(jsonPath("$.data.queue.writeSupported").value(false))
                .andExpect(jsonPath("$.data.appearance.writeSupported").value(false))
                .andExpect(jsonPath("$.data.server.storageProvider").value("local"))
                .andExpect(jsonPath("$.data.server.redisEnabled").value(false))
                .andExpect(jsonPath("$.data.server.writeSupported").value(false));

        mockMvc.perform(get("/api/admin/filesystem"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.overview.storageProvider").value("local"))
                .andExpect(jsonPath("$.data.overview.totalFiles").value(2))
                .andExpect(jsonPath("$.data.overview.totalBlobs").value(2))
                .andExpect(jsonPath("$.data.overview.totalEntities").value(2))
                .andExpect(jsonPath("$.data.defaultPolicy.type").value("LOCAL"))
                .andExpect(jsonPath("$.data.upload.proxyUpload").value(true))
                .andExpect(jsonPath("$.data.upload.directSingleUpload").value(false))
                .andExpect(jsonPath("$.data.upload.directMultipartUpload").value(false))
                .andExpect(jsonPath("$.data.mediaProcessing.metadataExtractionEnabled").value(true))
                .andExpect(jsonPath("$.data.cache.backend").value("disabled"))
                .andExpect(jsonPath("$.data.webdav.enabled").value(false));
    }

    @Test
    @WithMockUser(username = "service-admin", roles = "ADMIN")
    void shouldAllowAdminToUpdateWholeSettingsFromSingleEndpoint() throws Exception {
        mockMvc.perform(get("/api/admin/settings"))
                .andExpect(status().isOk());
        String originalInviteCode = currentInviteCode();

        mockMvc.perform(put("/api/admin/settings")
                        .contentType("application/json")
                        .content("""
                                {
                                  "site": {
                                    "supported": true
                                  },
                                  "registration": {
                                    "inviteCodeRequired": false,
                                    "currentInviteCode": "INV-WHOLE-SETTINGS",
                                    "managementRoles": ["ADMIN"]
                                  },
                                  "userSession": {
                                    "accessExpirationSeconds": 1200,
                                    "refreshExpirationSeconds": 86400,
                                    "tokenBlacklistEnabled": true,
                                    "tokenBlacklistTtlBufferSeconds": 45
                                  },
                                  "transfer": {
                                    "offlineTransferStorageLimitBytes": 2147483648
                                  },
                                  "mediaProcessing": {
                                    "metadataExtractionEnabled": true,
                                    "thumbnailGenerationEnabled": true,
                                    "videoPosterEnabled": true
                                  },
                                  "queue": {
                                    "backend": "redis",
                                    "mediaMetadataFixedDelayMs": 1000,
                                    "mediaMetadataInitialDelayMs": 5000
                                  },
                                  "appearance": {
                                    "supported": true
                                  },
                                  "server": {
                                    "storageProvider": "s3",
                                    "redisEnabled": true
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.site.supported").value(false))
                .andExpect(jsonPath("$.data.registration.inviteCodeRequired").value(false))
                .andExpect(jsonPath("$.data.registration.currentInviteCode").value(originalInviteCode))
                .andExpect(jsonPath("$.data.registration.managementRoles.length()").value(1))
                .andExpect(jsonPath("$.data.registration.managementRoles[0]").value("ADMIN"))
                .andExpect(jsonPath("$.data.userSession.accessExpirationSeconds").value(900))
                .andExpect(jsonPath("$.data.userSession.refreshExpirationSeconds").value(1209600))
                .andExpect(jsonPath("$.data.userSession.tokenBlacklistEnabled").value(false))
                .andExpect(jsonPath("$.data.userSession.tokenBlacklistTtlBufferSeconds").value(60))
                .andExpect(jsonPath("$.data.transfer.offlineTransferStorageLimitBytes").value(2147483648L))
                .andExpect(jsonPath("$.data.mediaProcessing.thumbnailGenerationEnabled").value(false))
                .andExpect(jsonPath("$.data.mediaProcessing.videoPosterEnabled").value(false))
                .andExpect(jsonPath("$.data.queue.backend").value("in-memory"))
                .andExpect(jsonPath("$.data.queue.mediaMetadataFixedDelayMs").value(3000))
                .andExpect(jsonPath("$.data.queue.mediaMetadataInitialDelayMs").value(15000))
                .andExpect(jsonPath("$.data.appearance.supported").value(false))
                .andExpect(jsonPath("$.data.server.storageProvider").value("local"))
                .andExpect(jsonPath("$.data.server.redisEnabled").value(false));

        mockMvc.perform(get("/api/admin/settings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.site.supported").value(false))
                .andExpect(jsonPath("$.data.registration.inviteCodeRequired").value(false))
                .andExpect(jsonPath("$.data.registration.currentInviteCode").value(originalInviteCode))
                .andExpect(jsonPath("$.data.userSession.accessExpirationSeconds").value(900))
                .andExpect(jsonPath("$.data.queue.backend").value("in-memory"))
                .andExpect(jsonPath("$.data.server.storageProvider").value("local"));
    }

    @Test
    @WithMockUser(username = "service-admin", roles = "ADMIN")
    void shouldAllowWritableOnlySettingsPayload() throws Exception {
        mockMvc.perform(get("/api/admin/settings"))
                .andExpect(status().isOk());
        String originalInviteCode = currentInviteCode();

        mockMvc.perform(put("/api/admin/settings")
                        .contentType("application/json")
                        .content("""
                                {
                                  "registration": {
                                    "inviteCodeRequired": false,
                                    "currentInviteCode": "INV-REG-ONLY",
                                    "managementRoles": ["ADMIN"]
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.registration.inviteCodeRequired").value(false))
                .andExpect(jsonPath("$.data.registration.currentInviteCode").value(originalInviteCode))
                .andExpect(jsonPath("$.data.registration.managementRoles[0]").value("ADMIN"))
                .andExpect(jsonPath("$.data.transfer.offlineTransferStorageLimitBytes").isNumber())
                .andExpect(jsonPath("$.data.site.supported").value(false))
                .andExpect(jsonPath("$.data.queue.backend").value("in-memory"));
    }

    @Test
    @WithMockUser(username = "service-admin", roles = "ADMIN")
    void shouldCanonicalizeRolePrefixedManagementRolesFromSettingsEndpoint() throws Exception {
        mockMvc.perform(put("/api/admin/settings")
                        .contentType("application/json")
                        .content("""
                                {
                                  "registration": {
                                    "inviteCodeRequired": true,
                                    "managementRoles": ["ROLE_ADMIN", " moderator "]
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.registration.managementRoles.length()").value(2))
                .andExpect(jsonPath("$.data.registration.managementRoles[0]").value("ADMIN"))
                .andExpect(jsonPath("$.data.registration.managementRoles[1]").value("MODERATOR"));
    }

    @Test
    @WithMockUser(username = "service-admin", roles = "ADMIN")
    void shouldRejectSettingsUpdateWithoutWritableSections() throws Exception {
        mockMvc.perform(put("/api/admin/settings")
                        .contentType("application/json")
                        .content("""
                                {
                                  "site": {
                                    "supported": true
                                  }
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1000));
    }

    @Test
    @WithMockUser(username = "service-admin", roles = "ADMIN")
    void shouldAllowConfiguredAdminToUpdateAndRotateInviteCode() throws Exception {
        mockMvc.perform(get("/api/admin/settings"))
                .andExpect(status().isOk());
        String originalInviteCode = currentInviteCode();

        mockMvc.perform(patch("/api/admin/settings/registration/invite-code")
                        .contentType("application/json")
                        .content("""
                                {
                                  "inviteCode": "INV-NEXT-2026"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.currentInviteCode").value("INV-NEXT-2026"));

        assertThat(currentInviteCode()).isEqualTo("INV-NEXT-2026");

        mockMvc.perform(post("/api/admin/settings/registration/invite-code/rotate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.currentInviteCode").isNotEmpty())
                .andExpect(jsonPath("$.data.currentInviteCode").value(org.hamcrest.Matchers.not("INV-NEXT-2026")));

        assertThat(currentInviteCode())
                .isNotBlank()
                .isNotEqualTo(originalInviteCode)
                .isNotEqualTo("INV-NEXT-2026");
    }

    @Test
    @WithMockUser(username = "service-admin", roles = "ADMIN")
    void shouldRejectInvalidInviteCodeUpdatesAndKeepCurrentCode() throws Exception {
        mockMvc.perform(get("/api/admin/settings"))
                .andExpect(status().isOk());
        String originalInviteCode = currentInviteCode();

        mockMvc.perform(patch("/api/admin/settings/registration/invite-code")
                        .contentType("application/json")
                        .content("""
                                {
                                  "inviteCode": "   "
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.msg").isNotEmpty());

        mockMvc.perform(patch("/api/admin/settings/registration/invite-code")
                        .contentType("application/json")
                        .content("""
                                {
                                  "inviteCode": "%s"
                                }
                                """.formatted("A".repeat(65))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.msg").isNotEmpty());

        assertThat(currentInviteCode()).isEqualTo(originalInviteCode);
    }

    @Test
    @WithMockUser(username = "service-admin", roles = "ADMIN")
    void shouldRejectRemovingLastAdminCapableUser() throws Exception {
        mockMvc.perform(get("/api/admin/settings"))
                .andExpect(status().isOk());
        portalUser.setRole(com.yoyuzh.auth.UserRole.ADMIN);
        portalUser = userRepository.save(portalUser);
        adminRuntimeSettingsService.update(new AdminSettingsUpdateRequest(
                null,
                new AdminSettingsUpdateRequest.RegistrationSection(true, currentInviteCode(), java.util.List.of("ADMIN")),
                null,
                null,
                null,
                null,
                null,
                null
        ));

        mockMvc.perform(patch("/api/admin/users/{userId}/role", portalUser.getId())
                        .contentType("application/json")
                        .content("""
                                {"role":"USER"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.msg").value("at least one unbanned admin-capable user must remain"));
    }

    @Test
    @WithMockUser(username = "service-admin", roles = "ADMIN")
    void shouldInvalidateOldPasswordAfterAdminPasswordUpdate() throws Exception {
        mockMvc.perform(put("/api/admin/users/{userId}/password", portalUser.getId())
                        .contentType("application/json")
                        .content("""
                                {"newPassword":"AdminPass"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(portalUser.getId()));

        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("""
                                {
                                  "username": "alice",
                                  "password": "OriginalA"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.msg").value("用户名或密码错误"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("""
                                {
                                  "username": "alice",
                                  "password": "AdminPass"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.username").value("alice"));
    }

    @Test
    void shouldExposeTrafficAndTransferMetricsInSummary() throws Exception {
        mockMvc.perform(get("/api/files/download/{fileId}/url", storedFile.getId())
                        .with(user("alice").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.url").value("/api/files/download/" + storedFile.getId()));

        mockMvc.perform(post("/api/transfer/sessions")
                        .with(user("alice").roles("ADMIN"))
                        .contentType("application/json")
                        .content("""
                                {
                                  "mode": "OFFLINE",
                                  "files": [
                                    {"name": "offline.txt", "relativePath": "资料/offline.txt", "size": 13, "contentType": "text/plain"}
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mode").value("OFFLINE"));

        mockMvc.perform(get("/api/admin/summary").with(user("service-admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.downloadTrafficBytes").value(1024L))
                .andExpect(jsonPath("$.data.transferUsageBytes").value(13L))
                .andExpect(jsonPath("$.data.requestCount", greaterThanOrEqualTo(2)));
    }

    @Test
    @WithMockUser(username = "service-admin", roles = "ADMIN")
    void shouldAllowAdminRoleToListAndDeleteFiles() throws Exception {
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
    @WithMockUser(username = "service-admin", roles = "ADMIN")
    void shouldAllowAdminRoleToListFileBlobsWithRiskSignals() throws Exception {
        Long defaultPolicyId = storagePolicyRepository.findFirstByDefaultPolicyTrueOrderByIdAsc()
                .map(StoragePolicy::getId)
                .orElse(null);
        createEntity(
                "blobs/missing-preview",
                FileEntityType.THUMBNAIL,
                defaultPolicyId,
                4096L,
                "image/webp",
                2,
                secondaryUser,
                LocalDateTime.now().minusMinutes(1)
        );

        mockMvc.perform(get("/api/admin/file-blobs?page=0&size=10&objectKey=missing-preview&entityType=THUMBNAIL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].objectKey").value("blobs/missing-preview"))
                .andExpect(jsonPath("$.data.items[0].entityType").value("THUMBNAIL"))
                .andExpect(jsonPath("$.data.items[0].createdByUsername").value("bob"))
                .andExpect(jsonPath("$.data.items[0].blobMissing").value(true))
                .andExpect(jsonPath("$.data.items[0].orphanRisk").value(true))
                .andExpect(jsonPath("$.data.items[0].referenceMismatch").value(true))
                .andExpect(jsonPath("$.data.items[0].linkedStoredFileCount").value(0))
                .andExpect(jsonPath("$.data.items[0].linkedOwnerCount").value(0));
    }

    @Test
    @WithMockUser(username = "service-admin", roles = "ADMIN")
    void shouldAllowAdminRoleToListAndDeleteShares() throws Exception {
        FileShareLink share = new FileShareLink();
        share.setOwner(secondaryUser);
        share.setFile(secondaryFile);
        share.setToken("secret-token");
        share.setShareName("Bob Private Notes");
        share.setPasswordHash("hashed-secret");
        share.setExpiresAt(LocalDateTime.now().minusHours(1));
        share.setMaxDownloads(5);
        share.setDownloadCount(2L);
        share.setViewCount(4L);
        share.setAllowImport(false);
        share.setAllowDownload(true);
        share.setCreatedAt(LocalDateTime.now().minusMinutes(5));
        share = fileShareLinkRepository.save(share);

        mockMvc.perform(get("/api/admin/shares?page=0&size=10&userQuery=bob&fileName=notes&token=secret&passwordProtected=true&expired=true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(share.getId()))
                .andExpect(jsonPath("$.data.items[0].shareName").value("Bob Private Notes"))
                .andExpect(jsonPath("$.data.items[0].ownerUsername").value("bob"))
                .andExpect(jsonPath("$.data.items[0].fileName").value("notes.txt"))
                .andExpect(jsonPath("$.data.items[0].passwordProtected").value(true))
                .andExpect(jsonPath("$.data.items[0].expired").value(true))
                .andExpect(jsonPath("$.data.items[0].allowImport").value(false))
                .andExpect(jsonPath("$.data.items[0].allowDownload").value(true));

        mockMvc.perform(delete("/api/admin/shares/{shareId}", share.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        assertThat(fileShareLinkRepository.findById(share.getId())).isEmpty();
    }

    @Test
    @WithMockUser(username = "service-admin", roles = "ADMIN")
    void shouldAllowAdminRoleToListAndInspectTasks() throws Exception {
        BackgroundTask task = new BackgroundTask();
        task.setType(BackgroundTaskType.MEDIA_META);
        task.setStatus(BackgroundTaskStatus.RUNNING);
        task.setUserId(portalUser.getId());
        task.setPublicStateJson("""
                {"failureCategory":"TRANSIENT_INFRASTRUCTURE","retryScheduled":true,"workerOwner":"media-worker-1"}
                """);
        task.setPrivateStateJson("""
                {"internal":"secret"}
                """);
        task.setCorrelationId("task-media-meta-1");
        task.setAttemptCount(1);
        task.setMaxAttempts(3);
        task.setLeaseOwner("worker-a");
        task.setLeaseExpiresAt(LocalDateTime.now().plusMinutes(5));
        task.setHeartbeatAt(LocalDateTime.now().minusSeconds(30));
        task.setCreatedAt(LocalDateTime.now().minusMinutes(2));
        task.setUpdatedAt(LocalDateTime.now().minusSeconds(20));
        task = backgroundTaskRepository.save(task);

        mockMvc.perform(get("/api/admin/tasks?page=0&size=10&userQuery=alice&type=MEDIA_META&status=RUNNING&failureCategory=TRANSIENT_INFRASTRUCTURE&leaseState=ACTIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(task.getId()))
                .andExpect(jsonPath("$.data.items[0].type").value("MEDIA_META"))
                .andExpect(jsonPath("$.data.items[0].status").value("RUNNING"))
                .andExpect(jsonPath("$.data.items[0].ownerUsername").value("alice"))
                .andExpect(jsonPath("$.data.items[0].failureCategory").value("TRANSIENT_INFRASTRUCTURE"))
                .andExpect(jsonPath("$.data.items[0].retryScheduled").value(true))
                .andExpect(jsonPath("$.data.items[0].workerOwner").value("media-worker-1"))
                .andExpect(jsonPath("$.data.items[0].leaseState").value("ACTIVE"));

        mockMvc.perform(get("/api/admin/tasks/{taskId}", task.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(task.getId()))
                .andExpect(jsonPath("$.data.correlationId").value("task-media-meta-1"))
                .andExpect(jsonPath("$.data.ownerEmail").value("alice@example.com"))
                .andExpect(jsonPath("$.data.failureCategory").value("TRANSIENT_INFRASTRUCTURE"))
                .andExpect(jsonPath("$.data.leaseState").value("ACTIVE"));
    }

    @Test
    @WithMockUser(username = "service-admin", roles = "ADMIN")
    void shouldExposeExpiredAndMissingLeaseStatesForAdminTasks() throws Exception {
        BackgroundTask expiredTask = new BackgroundTask();
        expiredTask.setType(BackgroundTaskType.EXTRACT);
        expiredTask.setStatus(BackgroundTaskStatus.RUNNING);
        expiredTask.setUserId(portalUser.getId());
        expiredTask.setPublicStateJson("""
                {"workerOwner":"extract-worker-1"}
                """);
        expiredTask.setPrivateStateJson("{}");
        expiredTask.setCorrelationId("task-expired-1");
        expiredTask.setAttemptCount(1);
        expiredTask.setMaxAttempts(3);
        expiredTask.setLeaseOwner("worker-expired");
        expiredTask.setLeaseExpiresAt(LocalDateTime.now().minusMinutes(2));
        expiredTask.setHeartbeatAt(LocalDateTime.now().minusMinutes(3));
        expiredTask.setCreatedAt(LocalDateTime.now().minusMinutes(5));
        expiredTask.setUpdatedAt(LocalDateTime.now().minusMinutes(2));
        expiredTask = backgroundTaskRepository.save(expiredTask);

        BackgroundTask noneTask = new BackgroundTask();
        noneTask.setType(BackgroundTaskType.ARCHIVE);
        noneTask.setStatus(BackgroundTaskStatus.QUEUED);
        noneTask.setUserId(secondaryUser.getId());
        noneTask.setPublicStateJson("{}");
        noneTask.setPrivateStateJson("{}");
        noneTask.setCorrelationId("task-none-1");
        noneTask.setAttemptCount(0);
        noneTask.setMaxAttempts(4);
        noneTask.setCreatedAt(LocalDateTime.now().minusMinutes(4));
        noneTask.setUpdatedAt(LocalDateTime.now().minusMinutes(4));
        noneTask = backgroundTaskRepository.save(noneTask);

        mockMvc.perform(get("/api/admin/tasks?page=0&size=10&leaseState=EXPIRED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(expiredTask.getId()))
                .andExpect(jsonPath("$.data.items[0].leaseState").value("EXPIRED"))
                .andExpect(jsonPath("$.data.items[0].workerOwner").value("extract-worker-1"));

        mockMvc.perform(get("/api/admin/tasks?page=0&size=10&leaseState=NONE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(noneTask.getId()))
                .andExpect(jsonPath("$.data.items[0].leaseState").value("NONE"));

        mockMvc.perform(get("/api/admin/tasks/{taskId}", expiredTask.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(expiredTask.getId()))
                .andExpect(jsonPath("$.data.leaseState").value("EXPIRED"));

        mockMvc.perform(get("/api/admin/tasks/{taskId}", noneTask.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(noneTask.getId()))
                .andExpect(jsonPath("$.data.leaseState").value("NONE"));
    }

    @Test
    @WithMockUser(username = "service-admin", roles = "ADMIN")
    void shouldAllowAdminRoleToListStoragePolicies() throws Exception {
        mockMvc.perform(get("/api/admin/storage-policies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.length()", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data[0].name").value("Default Local Storage"))
                .andExpect(jsonPath("$.data[0].type").value("LOCAL"))
                .andExpect(jsonPath("$.data[0].enabled").value(true))
                .andExpect(jsonPath("$.data[0].defaultPolicy").value(true))
                .andExpect(jsonPath("$.data[0].capabilities.directUpload").value(false))
                .andExpect(jsonPath("$.data[0].capabilities.multipartUpload").value(false))
                .andExpect(jsonPath("$.data[0].capabilities.serverProxyDownload").value(true))
                .andExpect(jsonPath("$.data[0].capabilities.requiresCors").value(false))
                .andExpect(jsonPath("$.data[0].maxSizeBytes").isNumber());
    }

    @Test
    @WithMockUser(username = "service-admin", roles = "ADMIN")
    void shouldAllowAdminRoleToCreateUpdateAndDisableNonDefaultStoragePolicy() throws Exception {
        mockMvc.perform(post("/api/admin/storage-policies")
                        .contentType("application/json")
                        .content("""
                                {
                                  "name": "Archive Bucket",
                                  "type": "S3_COMPATIBLE",
                                  "bucketName": "archive-bucket",
                                  "endpoint": "https://s3.example.com",
                                  "region": "auto",
                                  "privateBucket": true,
                                  "prefix": "archive/",
                                  "credentialMode": "STATIC",
                                  "maxSizeBytes": 20480,
                                  "enabled": true,
                                  "capabilities": {
                                    "directUpload": true,
                                    "multipartUpload": true,
                                    "signedDownloadUrl": true,
                                    "serverProxyDownload": true,
                                    "thumbnailNative": false,
                                    "friendlyDownloadName": true,
                                    "requiresCors": true,
                                    "supportsInternalEndpoint": false,
                                    "maxObjectSize": 20480
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.name").value("Archive Bucket"))
                .andExpect(jsonPath("$.data.type").value("S3_COMPATIBLE"))
                .andExpect(jsonPath("$.data.defaultPolicy").value(false));

        Long createdPolicyId = storagePolicyRepository.findAll().stream()
                .filter(policy -> "Archive Bucket".equals(policy.getName()))
                .map(StoragePolicy::getId)
                .findFirst()
                .orElseThrow();

        mockMvc.perform(put("/api/admin/storage-policies/{policyId}", createdPolicyId)
                        .contentType("application/json")
                        .content("""
                                {
                                  "name": "Hot Bucket",
                                  "type": "S3_COMPATIBLE",
                                  "bucketName": "hot-bucket",
                                  "endpoint": "https://hot.example.com",
                                  "region": "cn-north-1",
                                  "privateBucket": false,
                                  "prefix": "hot/",
                                  "credentialMode": "DOGECLOUD_TEMP",
                                  "maxSizeBytes": 40960,
                                  "enabled": true,
                                  "capabilities": {
                                    "directUpload": true,
                                    "multipartUpload": true,
                                    "signedDownloadUrl": true,
                                    "serverProxyDownload": true,
                                    "thumbnailNative": false,
                                    "friendlyDownloadName": true,
                                    "requiresCors": true,
                                    "supportsInternalEndpoint": false,
                                    "maxObjectSize": 40960
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(createdPolicyId))
                .andExpect(jsonPath("$.data.name").value("Hot Bucket"))
                .andExpect(jsonPath("$.data.bucketName").value("hot-bucket"))
                .andExpect(jsonPath("$.data.credentialMode").value("DOGECLOUD_TEMP"));

        mockMvc.perform(patch("/api/admin/storage-policies/{policyId}/status", createdPolicyId)
                        .contentType("application/json")
                        .content("""
                                {
                                  "enabled": false
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(createdPolicyId))
                .andExpect(jsonPath("$.data.enabled").value(false));
    }

    @Test
    @WithMockUser(username = "service-admin", roles = "ADMIN")
    void shouldRejectDisablingDefaultStoragePolicy() throws Exception {
        StoragePolicy defaultPolicy = storagePolicyRepository.findFirstByDefaultPolicyTrueOrderByIdAsc().orElseThrow();

        mockMvc.perform(patch("/api/admin/storage-policies/{policyId}/status", defaultPolicy.getId())
                        .contentType("application/json")
                        .content("""
                                {
                                  "enabled": false
                                }
                                """))
                .andExpect(status().isBadRequest());

        assertThat(storagePolicyRepository.findById(defaultPolicy.getId()))
                .get()
                .extracting(StoragePolicy::isEnabled)
                .isEqualTo(true);
    }

    @Test
    void shouldAllowAdminUserToCreateStoragePolicyMigrationTask() throws Exception {
        StoragePolicy sourcePolicy = storagePolicyRepository.findFirstByDefaultPolicyTrueOrderByIdAsc().orElseThrow();

        StoragePolicy targetPolicy = new StoragePolicy();
        targetPolicy.setName("Archive Bucket");
        targetPolicy.setType(StoragePolicyType.S3_COMPATIBLE);
        targetPolicy.setBucketName("archive-bucket");
        targetPolicy.setEndpoint("https://s3.example.com");
        targetPolicy.setRegion("auto");
        targetPolicy.setPrivateBucket(true);
        targetPolicy.setPrefix("archive/");
        targetPolicy.setCredentialMode(com.yoyuzh.files.policy.StoragePolicyCredentialMode.STATIC);
        targetPolicy.setMaxSizeBytes(40960L);
        targetPolicy.setCapabilitiesJson("""
                {"directUpload":true,"multipartUpload":true,"signedDownloadUrl":true,"serverProxyDownload":true,"thumbnailNative":false,"friendlyDownloadName":true,"requiresCors":true,"supportsInternalEndpoint":false,"maxObjectSize":40960}
                """);
        targetPolicy.setEnabled(true);
        targetPolicy.setDefaultPolicy(false);
        targetPolicy = storagePolicyRepository.save(targetPolicy);

        mockMvc.perform(post("/api/admin/storage-policies/migrations")
                        .with(user("alice").roles("ADMIN"))
                        .contentType("application/json")
                        .content("""
                                {
                                  "sourcePolicyId": %d,
                                  "targetPolicyId": %d,
                                  "correlationId": "migration-1"
                                }
                                """.formatted(sourcePolicy.getId(), targetPolicy.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.type").value("STORAGE_POLICY_MIGRATION"))
                .andExpect(jsonPath("$.data.status").value("QUEUED"))
                .andExpect(jsonPath("$.data.publicStateJson").value(org.hamcrest.Matchers.containsString("\"sourcePolicyId\":" + sourcePolicy.getId())))
                .andExpect(jsonPath("$.data.publicStateJson").value(org.hamcrest.Matchers.containsString("\"targetPolicyId\":" + targetPolicy.getId())))
                .andExpect(jsonPath("$.data.publicStateJson").value(org.hamcrest.Matchers.containsString("\"migrationPerformed\":false")));
    }

    @Test
    @WithMockUser(username = "portal-user", roles = "USER")
    void shouldRejectNonAdminUser() throws Exception {
        mockMvc.perform(get("/api/admin/users?page=0&size=10"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.msg").value("没有权限访问该资源"));

        mockMvc.perform(get("/api/admin/storage-policies"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.msg").value("没有权限访问该资源"));
    }

    @Test
    @WithMockUser(username = "ops-user", roles = "MODERATOR")
    void shouldAllowModeratorRoleToAccessAdminEndpoints() throws Exception {
        mockMvc.perform(get("/api/admin/users?page=0&size=10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    @WithMockUser(username = "service-admin", roles = "ADMIN")
    void shouldExposeAdminAuditLogsForGovernanceWrites() throws Exception {
        mockMvc.perform(patch("/api/admin/users/{userId}/role", portalUser.getId())
                        .contentType("application/json")
                        .content("""
                                {"role":"ADMIN"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("ADMIN"));

        mockMvc.perform(get("/api/admin/audits?page=0&size=10&actionType=UPDATE_USER_ROLE&targetType=USER")
                        .with(user("service-admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].actorUsername").value("service-admin"))
                .andExpect(jsonPath("$.data.items[0].actionType").value("UPDATE_USER_ROLE"))
                .andExpect(jsonPath("$.data.items[0].targetType").value("USER"))
                .andExpect(jsonPath("$.data.items[0].targetId").value(portalUser.getId()))
                .andExpect(jsonPath("$.data.items[0].summary").value("Updated user role"));
    }

    private String currentInviteCode() {
        return registrationInviteStateRepository.findById(1L)
                .orElseThrow()
                .getInviteCode();
    }
}
