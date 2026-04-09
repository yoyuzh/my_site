package com.yoyuzh.admin;

import com.yoyuzh.PortalBackendApplication;
import com.yoyuzh.admin.AdminMetricsStateRepository;
import com.yoyuzh.auth.User;
import com.yoyuzh.auth.UserRepository;
import com.yoyuzh.files.core.FileBlob;
import com.yoyuzh.files.core.FileBlobRepository;
import com.yoyuzh.files.core.StoredFile;
import com.yoyuzh.files.core.StoredFileRepository;
import com.yoyuzh.files.policy.StoragePolicy;
import com.yoyuzh.files.policy.StoragePolicyRepository;
import com.yoyuzh.files.policy.StoragePolicyType;
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
                "app.admin.usernames=admin,alice",
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
    private OfflineTransferSessionRepository offlineTransferSessionRepository;
    @Autowired
    private AdminMetricsStateRepository adminMetricsStateRepository;
    @Autowired
    private AdminMetricsService adminMetricsService;
    @Autowired
    private StoragePolicyRepository storagePolicyRepository;

    private User portalUser;
    private User secondaryUser;
    private StoredFile storedFile;
    private StoredFile secondaryFile;

    @BeforeEach
    void setUp() {
        offlineTransferSessionRepository.deleteAll();
        storedFileRepository.deleteAll();
        fileBlobRepository.deleteAll();
        userRepository.deleteAll();
        adminMetricsStateRepository.deleteAll();

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
        storedFile = new StoredFile();
        storedFile.setUser(portalUser);
        storedFile.setFilename("report.pdf");
        storedFile.setPath("/");
        storedFile.setContentType("application/pdf");
        storedFile.setSize(1024L);
        storedFile.setDirectory(false);
        storedFile.setBlob(reportBlob);
        storedFile.setCreatedAt(LocalDateTime.now());
        storedFile = storedFileRepository.save(storedFile);

        FileBlob notesBlob = createBlob("blobs/admin-notes", "text/plain", 256L);
        secondaryFile = new StoredFile();
        secondaryFile.setUser(secondaryUser);
        secondaryFile.setFilename("notes.txt");
        secondaryFile.setPath("/docs");
        secondaryFile.setContentType("text/plain");
        secondaryFile.setSize(256L);
        secondaryFile.setDirectory(false);
        secondaryFile.setBlob(notesBlob);
        secondaryFile.setCreatedAt(LocalDateTime.now().minusHours(2));
        secondaryFile = storedFileRepository.save(secondaryFile);
    }

    private FileBlob createBlob(String objectKey, String contentType, long size) {
        FileBlob blob = new FileBlob();
        blob.setObjectKey(objectKey);
        blob.setContentType(contentType);
        blob.setSize(size);
        blob.setCreatedAt(LocalDateTime.now());
        return fileBlobRepository.save(blob);
    }

    @Test
    @WithMockUser(username = "admin")
    void shouldAllowConfiguredAdminToListUsersAndSummary() throws Exception {
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
    @WithMockUser(username = "admin")
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
                        .with(user("alice")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.url").value("/api/files/download/" + storedFile.getId()));

        mockMvc.perform(post("/api/transfer/sessions")
                        .with(user("alice"))
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

        mockMvc.perform(get("/api/admin/summary").with(user("admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.downloadTrafficBytes").value(1024L))
                .andExpect(jsonPath("$.data.transferUsageBytes").value(13L))
                .andExpect(jsonPath("$.data.requestCount", greaterThanOrEqualTo(2)));
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
    @WithMockUser(username = "admin")
    void shouldAllowConfiguredAdminToListStoragePolicies() throws Exception {
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
    @WithMockUser(username = "admin")
    void shouldAllowConfiguredAdminToCreateUpdateAndDisableNonDefaultStoragePolicy() throws Exception {
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
    @WithMockUser(username = "admin")
    void shouldRejectDisablingDefaultStoragePolicy() throws Exception {
        StoragePolicy defaultPolicy = storagePolicyRepository.findFirstByDefaultPolicyTrueOrderByIdAsc().orElseThrow();

        mockMvc.perform(patch("/api/admin/storage-policies/{policyId}/status", defaultPolicy.getId())
                        .contentType("application/json")
                        .content("""
                                {
                                  "enabled": false
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.msg").value("默认存储策略不能停用"));
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
                        .with(user("alice"))
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
    @WithMockUser(username = "portal-user")
    void shouldRejectNonAdminUser() throws Exception {
        mockMvc.perform(get("/api/admin/users?page=0&size=10"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.msg").value("没有权限访问该资源"));

        mockMvc.perform(get("/api/admin/storage-policies"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.msg").value("没有权限访问该资源"));
    }
}
