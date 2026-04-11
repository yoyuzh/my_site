package com.yoyuzh.admin;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yoyuzh.auth.AuthTokenInvalidationService;
import com.yoyuzh.auth.PasswordPolicy;
import com.yoyuzh.auth.RefreshTokenService;
import com.yoyuzh.auth.RegistrationInviteService;
import com.yoyuzh.auth.User;
import com.yoyuzh.auth.UserRepository;
import com.yoyuzh.auth.UserRole;
import com.yoyuzh.common.BusinessException;
import com.yoyuzh.common.ErrorCode;
import com.yoyuzh.common.PageResponse;
import com.yoyuzh.config.RedisCacheNames;
import com.yoyuzh.files.core.FileEntity;
import com.yoyuzh.files.core.FileEntityRepository;
import com.yoyuzh.files.core.FileEntityType;
import com.yoyuzh.files.core.FileService;
import com.yoyuzh.files.core.StoredFile;
import com.yoyuzh.files.core.StoredFileEntityRepository;
import com.yoyuzh.files.core.StoredFileRepository;
import com.yoyuzh.files.core.FileBlobRepository;
import com.yoyuzh.files.policy.StoragePolicy;
import com.yoyuzh.files.policy.StoragePolicyRepository;
import com.yoyuzh.files.policy.StoragePolicyService;
import com.yoyuzh.files.share.FileShareLink;
import com.yoyuzh.files.share.FileShareLinkRepository;
import com.yoyuzh.files.tasks.BackgroundTask;
import com.yoyuzh.files.tasks.BackgroundTaskFailureCategory;
import com.yoyuzh.files.tasks.BackgroundTaskRepository;
import com.yoyuzh.files.tasks.BackgroundTaskService;
import com.yoyuzh.files.tasks.BackgroundTaskStatus;
import com.yoyuzh.files.tasks.BackgroundTaskType;
import com.yoyuzh.transfer.OfflineTransferSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminService {

    private final UserRepository userRepository;
    private final StoredFileRepository storedFileRepository;
    private final FileBlobRepository fileBlobRepository;
    private final FileService fileService;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;
    private final AuthTokenInvalidationService authTokenInvalidationService;
    private final RegistrationInviteService registrationInviteService;
    private final OfflineTransferSessionRepository offlineTransferSessionRepository;
    private final AdminMetricsService adminMetricsService;
    private final StoragePolicyRepository storagePolicyRepository;
    private final StoragePolicyService storagePolicyService;
    private final FileEntityRepository fileEntityRepository;
    private final StoredFileEntityRepository storedFileEntityRepository;
    private final BackgroundTaskRepository backgroundTaskRepository;
    private final BackgroundTaskService backgroundTaskService;
    private final FileShareLinkRepository fileShareLinkRepository;
    private final ObjectMapper objectMapper;
    private final SecureRandom secureRandom = new SecureRandom();

    public AdminSummaryResponse getSummary() {
        AdminMetricsSnapshot metrics = adminMetricsService.getSnapshot();
        return new AdminSummaryResponse(
                userRepository.count(),
                storedFileRepository.count(),
                fileBlobRepository.sumAllBlobSize(),
                metrics.downloadTrafficBytes(),
                metrics.requestCount(),
                metrics.transferUsageBytes(),
                offlineTransferSessionRepository.sumUploadedFileSizeByExpiresAtAfter(Instant.now()),
                metrics.offlineTransferStorageLimitBytes(),
                metrics.dailyActiveUsers(),
                metrics.requestTimeline(),
                registrationInviteService.getCurrentInviteCode()
        );
    }

    public PageResponse<AdminUserResponse> listUsers(int page, int size, String query) {
        Page<User> result = userRepository.searchByUsernameOrEmail(
                normalizeQuery(query),
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
        );
        List<AdminUserResponse> items = result.getContent().stream()
                .map(this::toUserResponse)
                .toList();
        return new PageResponse<>(items, result.getTotalElements(), page, size);
    }

    public PageResponse<AdminFileResponse> listFiles(int page, int size, String query, String ownerQuery) {
        Page<StoredFile> result = storedFileRepository.searchAdminFiles(
                normalizeQuery(query),
                normalizeQuery(ownerQuery),
                PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "user.username")
                        .and(Sort.by(Sort.Direction.DESC, "createdAt")))
        );
        List<AdminFileResponse> items = result.getContent().stream()
                .map(this::toFileResponse)
                .toList();
        return new PageResponse<>(items, result.getTotalElements(), page, size);
    }

    public PageResponse<AdminFileBlobResponse> listFileBlobs(int page,
                                                             int size,
                                                             String userQuery,
                                                             Long storagePolicyId,
                                                             String objectKey,
                                                             FileEntityType entityType) {
        Page<FileEntity> result = fileEntityRepository.searchAdminEntities(
                normalizeQuery(userQuery),
                storagePolicyId,
                normalizeQuery(objectKey),
                entityType,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
        );
        List<AdminFileBlobResponse> items = result.getContent().stream()
                .map(this::toFileBlobResponse)
                .toList();
        return new PageResponse<>(items, result.getTotalElements(), page, size);
    }

    public PageResponse<AdminShareResponse> listShares(int page,
                                                       int size,
                                                       String userQuery,
                                                       String fileName,
                                                       String token,
                                                       Boolean passwordProtected,
                                                       Boolean expired) {
        Page<FileShareLink> result = fileShareLinkRepository.searchAdminShares(
                normalizeQuery(userQuery),
                normalizeQuery(fileName),
                normalizeQuery(token),
                passwordProtected,
                expired,
                LocalDateTime.now(),
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
        );
        List<AdminShareResponse> items = result.getContent().stream()
                .map(this::toAdminShareResponse)
                .toList();
        return new PageResponse<>(items, result.getTotalElements(), page, size);
    }

    @Transactional
    public void deleteShare(Long shareId) {
        FileShareLink shareLink = fileShareLinkRepository.findById(shareId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FILE_NOT_FOUND, "share not found"));
        fileShareLinkRepository.delete(shareLink);
    }

    public PageResponse<AdminTaskResponse> listTasks(int page,
                                                     int size,
                                                     String userQuery,
                                                     BackgroundTaskType type,
                                                     BackgroundTaskStatus status,
                                                     BackgroundTaskFailureCategory failureCategory,
                                                     AdminTaskLeaseState leaseState) {
        String failureCategoryPattern = failureCategory == null
                ? null
                : "\"failureCategory\":\"" + failureCategory.name() + "\"";
        Page<BackgroundTask> result = backgroundTaskRepository.searchAdminTasks(
                normalizeQuery(userQuery),
                type,
                status,
                failureCategoryPattern,
                leaseState == null ? null : leaseState.name(),
                LocalDateTime.now(),
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
        );
        Map<Long, User> ownerById = userRepository.findAllById(result.getContent().stream()
                        .map(BackgroundTask::getUserId)
                        .collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(User::getId, user -> user));
        List<AdminTaskResponse> items = result.getContent().stream()
                .map(task -> toAdminTaskResponse(task, ownerById.get(task.getUserId())))
                .toList();
        return new PageResponse<>(items, result.getTotalElements(), page, size);
    }

    public AdminTaskResponse getTask(Long taskId) {
        BackgroundTask task = backgroundTaskRepository.findById(taskId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FILE_NOT_FOUND, "task not found"));
        User owner = userRepository.findById(task.getUserId()).orElse(null);
        return toAdminTaskResponse(task, owner);
    }

    @Cacheable(cacheNames = RedisCacheNames.STORAGE_POLICIES, key = "'all'")
    public List<AdminStoragePolicyResponse> listStoragePolicies() {
        return storagePolicyRepository.findAll(Sort.by(Sort.Direction.DESC, "defaultPolicy")
                        .and(Sort.by(Sort.Direction.DESC, "enabled"))
                        .and(Sort.by(Sort.Direction.ASC, "id")))
                .stream()
                .map(this::toStoragePolicyResponse)
                .toList();
    }

    @Transactional
    @CacheEvict(cacheNames = RedisCacheNames.STORAGE_POLICIES, allEntries = true)
    public AdminStoragePolicyResponse createStoragePolicy(AdminStoragePolicyUpsertRequest request) {
        StoragePolicy policy = new StoragePolicy();
        policy.setDefaultPolicy(false);
        applyStoragePolicyUpsert(policy, request);
        return toStoragePolicyResponse(storagePolicyRepository.save(policy));
    }

    @Transactional
    @CacheEvict(cacheNames = RedisCacheNames.STORAGE_POLICIES, allEntries = true)
    public AdminStoragePolicyResponse updateStoragePolicy(Long policyId, AdminStoragePolicyUpsertRequest request) {
        StoragePolicy policy = getRequiredStoragePolicy(policyId);
        applyStoragePolicyUpsert(policy, request);
        return toStoragePolicyResponse(storagePolicyRepository.save(policy));
    }

    @Transactional
    @CacheEvict(cacheNames = RedisCacheNames.STORAGE_POLICIES, allEntries = true)
    public AdminStoragePolicyResponse updateStoragePolicyStatus(Long policyId, boolean enabled) {
        StoragePolicy policy = getRequiredStoragePolicy(policyId);
        if (policy.isDefaultPolicy() && !enabled) {
            throw new BusinessException(ErrorCode.UNKNOWN, "榛樿瀛樺偍绛栫暐涓嶈兘鍋滅敤");
        }
        policy.setEnabled(enabled);
        return toStoragePolicyResponse(storagePolicyRepository.save(policy));
    }

    @Transactional
    public BackgroundTask createStoragePolicyMigrationTask(User user, AdminStoragePolicyMigrationCreateRequest request) {
        StoragePolicy sourcePolicy = getRequiredStoragePolicy(request.sourcePolicyId());
        StoragePolicy targetPolicy = getRequiredStoragePolicy(request.targetPolicyId());
        if (sourcePolicy.getId().equals(targetPolicy.getId())) {
            throw new BusinessException(ErrorCode.UNKNOWN, "婧愬瓨鍌ㄧ瓥鐣ュ拰鐩爣瀛樺偍绛栫暐涓嶈兘鐩稿悓");
        }
        if (!targetPolicy.isEnabled()) {
            throw new BusinessException(ErrorCode.UNKNOWN, "target storage policy must be enabled");
        }

        long candidateEntityCount = fileEntityRepository.countByStoragePolicyIdAndEntityType(
                sourcePolicy.getId(),
                FileEntityType.VERSION
        );
        long candidateStoredFileCount = storedFileEntityRepository.countDistinctStoredFilesByStoragePolicyIdAndEntityType(
                sourcePolicy.getId(),
                FileEntityType.VERSION
        );

        Map<String, Object> state = new LinkedHashMap<>();
        state.put("sourcePolicyId", sourcePolicy.getId());
        state.put("sourcePolicyName", sourcePolicy.getName());
        state.put("targetPolicyId", targetPolicy.getId());
        state.put("targetPolicyName", targetPolicy.getName());
        state.put("candidateEntityCount", candidateEntityCount);
        state.put("candidateStoredFileCount", candidateStoredFileCount);
        state.put("migrationPerformed", false);
        state.put("migrationMode", "skeleton");
        state.put("entityType", FileEntityType.VERSION.name());
        state.put("message", "storage policy migration skeleton queued; worker will validate and recount candidates without moving object data");

        Map<String, Object> privateState = new LinkedHashMap<>(state);
        privateState.put("taskType", BackgroundTaskType.STORAGE_POLICY_MIGRATION.name());

        return backgroundTaskService.createQueuedTask(
                user,
                BackgroundTaskType.STORAGE_POLICY_MIGRATION,
                state,
                privateState,
                request.correlationId()
        );
    }

    @Transactional
    public void deleteFile(Long fileId) {
        StoredFile storedFile = storedFileRepository.findById(fileId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FILE_NOT_FOUND, "file not found"));
        fileService.delete(storedFile.getUser(), fileId);
    }

    @Transactional
    public AdminUserResponse updateUserRole(Long userId, UserRole role) {
        User user = getRequiredUser(userId);
        user.setRole(role);
        return toUserResponse(userRepository.save(user));
    }

    @Transactional
    public AdminUserResponse updateUserBanned(Long userId, boolean banned) {
        User user = getRequiredUser(userId);
        user.setBanned(banned);
        authTokenInvalidationService.revokeAccessTokensForUser(user.getId());
        user.setActiveSessionId(UUID.randomUUID().toString());
        user.setDesktopActiveSessionId(UUID.randomUUID().toString());
        user.setMobileActiveSessionId(UUID.randomUUID().toString());
        refreshTokenService.revokeAllForUser(user.getId());
        return toUserResponse(userRepository.save(user));
    }

    @Transactional
    public AdminUserResponse updateUserPassword(Long userId, String newPassword) {
        if (!PasswordPolicy.isStrong(newPassword)) {
            throw new BusinessException(ErrorCode.UNKNOWN, PasswordPolicy.VALIDATION_MESSAGE);
        }
        User user = getRequiredUser(userId);
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        authTokenInvalidationService.revokeAccessTokensForUser(user.getId());
        user.setActiveSessionId(UUID.randomUUID().toString());
        user.setDesktopActiveSessionId(UUID.randomUUID().toString());
        user.setMobileActiveSessionId(UUID.randomUUID().toString());
        refreshTokenService.revokeAllForUser(user.getId());
        return toUserResponse(userRepository.save(user));
    }

    @Transactional
    public AdminUserResponse updateUserStorageQuota(Long userId, long storageQuotaBytes) {
        User user = getRequiredUser(userId);
        user.setStorageQuotaBytes(storageQuotaBytes);
        return toUserResponse(userRepository.save(user));
    }

    @Transactional
    public AdminUserResponse updateUserMaxUploadSize(Long userId, long maxUploadSizeBytes) {
        User user = getRequiredUser(userId);
        user.setMaxUploadSizeBytes(maxUploadSizeBytes);
        return toUserResponse(userRepository.save(user));
    }

    @Transactional
    public AdminPasswordResetResponse resetUserPassword(Long userId) {
        String temporaryPassword = generateTemporaryPassword();
        updateUserPassword(userId, temporaryPassword);
        return new AdminPasswordResetResponse(temporaryPassword);
    }

    @Transactional
    public AdminOfflineTransferStorageLimitResponse updateOfflineTransferStorageLimit(long offlineTransferStorageLimitBytes) {
        return adminMetricsService.updateOfflineTransferStorageLimit(offlineTransferStorageLimitBytes);
    }

    private AdminUserResponse toUserResponse(User user) {
        long usedStorageBytes = storedFileRepository.sumFileSizeByUserId(user.getId());
        return new AdminUserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getPhoneNumber(),
                user.getCreatedAt(),
                user.getRole(),
                user.isBanned(),
                usedStorageBytes,
                user.getStorageQuotaBytes(),
                user.getMaxUploadSizeBytes()
        );
    }

    private AdminFileResponse toFileResponse(StoredFile storedFile) {
        User owner = storedFile.getUser();
        return new AdminFileResponse(
                storedFile.getId(),
                storedFile.getFilename(),
                storedFile.getPath(),
                storedFile.getSize(),
                storedFile.getContentType(),
                storedFile.isDirectory(),
                storedFile.getCreatedAt(),
                owner.getId(),
                owner.getUsername(),
                owner.getEmail()
        );
    }

    private AdminStoragePolicyResponse toStoragePolicyResponse(StoragePolicy policy) {
        return new AdminStoragePolicyResponse(
                policy.getId(),
                policy.getName(),
                policy.getType(),
                policy.getBucketName(),
                policy.getEndpoint(),
                policy.getRegion(),
                policy.isPrivateBucket(),
                policy.getPrefix(),
                policy.getCredentialMode(),
                policy.getMaxSizeBytes(),
                storagePolicyService.readCapabilities(policy),
                policy.isEnabled(),
                policy.isDefaultPolicy(),
                policy.getCreatedAt(),
                policy.getUpdatedAt()
        );
    }

    private AdminFileBlobResponse toFileBlobResponse(FileEntity entity) {
        var blob = fileBlobRepository.findByObjectKey(entity.getObjectKey()).orElse(null);
        long linkedStoredFileCount = storedFileEntityRepository.countByFileEntityId(entity.getId());
        long linkedOwnerCount = storedFileEntityRepository.countDistinctOwnersByFileEntityId(entity.getId());
        return new AdminFileBlobResponse(
                entity.getId(),
                blob == null ? null : blob.getId(),
                entity.getObjectKey(),
                entity.getEntityType(),
                entity.getStoragePolicyId(),
                entity.getSize(),
                StringUtils.hasText(entity.getContentType()) ? entity.getContentType() : blob == null ? null : blob.getContentType(),
                entity.getReferenceCount(),
                linkedStoredFileCount,
                linkedOwnerCount,
                storedFileEntityRepository.findSampleOwnerUsernameByFileEntityId(entity.getId()),
                storedFileEntityRepository.findSampleOwnerEmailByFileEntityId(entity.getId()),
                entity.getCreatedBy() == null ? null : entity.getCreatedBy().getId(),
                entity.getCreatedBy() == null ? null : entity.getCreatedBy().getUsername(),
                entity.getCreatedAt(),
                blob == null ? null : blob.getCreatedAt(),
                blob == null,
                linkedStoredFileCount == 0,
                entity.getReferenceCount() == null || entity.getReferenceCount() != linkedStoredFileCount
        );
    }

    private AdminShareResponse toAdminShareResponse(FileShareLink shareLink) {
        StoredFile file = shareLink.getFile();
        User owner = shareLink.getOwner();
        boolean expired = shareLink.getExpiresAt() != null && shareLink.getExpiresAt().isBefore(LocalDateTime.now());
        return new AdminShareResponse(
                shareLink.getId(),
                shareLink.getToken(),
                shareLink.getShareNameOrDefault(),
                shareLink.hasPassword(),
                expired,
                shareLink.getCreatedAt(),
                shareLink.getExpiresAt(),
                shareLink.getMaxDownloads(),
                shareLink.getDownloadCountOrZero(),
                shareLink.getViewCountOrZero(),
                shareLink.isAllowImportEnabled(),
                shareLink.isAllowDownloadEnabled(),
                owner.getId(),
                owner.getUsername(),
                owner.getEmail(),
                file.getId(),
                file.getFilename(),
                file.getPath(),
                file.getContentType(),
                file.getSize(),
                file.isDirectory()
        );
    }

    private AdminTaskResponse toAdminTaskResponse(BackgroundTask task, User owner) {
        Map<String, Object> state = parseState(task.getPublicStateJson());
        return new AdminTaskResponse(
                task.getId(),
                task.getType(),
                task.getStatus(),
                task.getUserId(),
                owner == null ? null : owner.getUsername(),
                owner == null ? null : owner.getEmail(),
                task.getPublicStateJson(),
                task.getCorrelationId(),
                task.getErrorMessage(),
                task.getAttemptCount(),
                task.getMaxAttempts(),
                task.getNextRunAt(),
                task.getLeaseOwner(),
                task.getLeaseExpiresAt(),
                task.getHeartbeatAt(),
                task.getCreatedAt(),
                task.getUpdatedAt(),
                task.getFinishedAt(),
                readStringState(state, "failureCategory"),
                readBooleanState(state, "retryScheduled"),
                readStringState(state, "workerOwner"),
                resolveLeaseState(task)
        );
    }

    private void applyStoragePolicyUpsert(StoragePolicy policy, AdminStoragePolicyUpsertRequest request) {
        if (policy.isDefaultPolicy() && !request.enabled()) {
            throw new BusinessException(ErrorCode.UNKNOWN, "榛樿瀛樺偍绛栫暐涓嶈兘鍋滅敤");
        }
        validateStoragePolicyRequest(request);
        policy.setName(request.name().trim());
        policy.setType(request.type());
        policy.setBucketName(normalizeNullable(request.bucketName()));
        policy.setEndpoint(normalizeNullable(request.endpoint()));
        policy.setRegion(normalizeNullable(request.region()));
        policy.setPrivateBucket(request.privateBucket());
        policy.setPrefix(normalizePrefix(request.prefix()));
        policy.setCredentialMode(request.credentialMode());
        policy.setMaxSizeBytes(request.maxSizeBytes());
        policy.setCapabilitiesJson(storagePolicyService.writeCapabilities(request.capabilities()));
        policy.setEnabled(request.enabled());
    }

    private User getRequiredUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNKNOWN, "user not found"));
    }

    private StoragePolicy getRequiredStoragePolicy(Long policyId) {
        return storagePolicyRepository.findById(policyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNKNOWN, "storage policy not found"));
    }

    private String normalizeQuery(String query) {
        if (query == null) {
            return "";
        }
        return query.trim();
    }

    private String normalizeNullable(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private String normalizePrefix(String prefix) {
        if (!StringUtils.hasText(prefix)) {
            return "";
        }
        return prefix.trim();
    }

    private Map<String, Object> parseState(String json) {
        if (!StringUtils.hasText(json)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() {
            });
        } catch (JsonProcessingException ex) {
            return Map.of();
        }
    }

    private String readStringState(Map<String, Object> state, String key) {
        Object value = state.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private Boolean readBooleanState(Map<String, Object> state, String key) {
        Object value = state.get(key);
        if (value instanceof Boolean boolValue) {
            return boolValue;
        }
        if (value instanceof String stringValue) {
            return Boolean.parseBoolean(stringValue);
        }
        return null;
    }

    private AdminTaskLeaseState resolveLeaseState(BackgroundTask task) {
        if (!StringUtils.hasText(task.getLeaseOwner()) || task.getLeaseExpiresAt() == null) {
            return AdminTaskLeaseState.NONE;
        }
        return task.getLeaseExpiresAt().isBefore(LocalDateTime.now())
                ? AdminTaskLeaseState.EXPIRED
                : AdminTaskLeaseState.ACTIVE;
    }

    private void validateStoragePolicyRequest(AdminStoragePolicyUpsertRequest request) {
        if (request.type() == com.yoyuzh.files.policy.StoragePolicyType.LOCAL
                && request.credentialMode() != com.yoyuzh.files.policy.StoragePolicyCredentialMode.NONE) {
            throw new BusinessException(ErrorCode.UNKNOWN, "鏈湴瀛樺偍绛栫暐蹇呴』浣跨敤 NONE 鍑瘉妯″紡");
        }
        if (request.type() == com.yoyuzh.files.policy.StoragePolicyType.S3_COMPATIBLE
                && !StringUtils.hasText(request.bucketName())) {
            throw new BusinessException(ErrorCode.UNKNOWN, "S3 瀛樺偍绛栫暐蹇呴』鎻愪緵 bucketName");
        }
    }

    private String generateTemporaryPassword() {
        String lowers = "abcdefghjkmnpqrstuvwxyz";
        String uppers = "ABCDEFGHJKMNPQRSTUVWXYZ";
        String digits = "23456789";
        String specials = "!@#$%^&*";
        String all = lowers + uppers + digits + specials;
        char[] password = new char[12];
        password[0] = lowers.charAt(secureRandom.nextInt(lowers.length()));
        password[1] = uppers.charAt(secureRandom.nextInt(uppers.length()));
        password[2] = digits.charAt(secureRandom.nextInt(digits.length()));
        password[3] = specials.charAt(secureRandom.nextInt(specials.length()));
        for (int i = 4; i < password.length; i += 1) {
            password[i] = all.charAt(secureRandom.nextInt(all.length()));
        }
        for (int i = password.length - 1; i > 0; i -= 1) {
            int j = secureRandom.nextInt(i + 1);
            char tmp = password[i];
            password[i] = password[j];
            password[j] = tmp;
        }
        return new String(password);
    }
}
