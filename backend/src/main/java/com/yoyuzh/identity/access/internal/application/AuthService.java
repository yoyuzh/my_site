package com.yoyuzh.identity.access.internal.application;

import com.yoyuzh.identity.access.api.AuthResponse;
import com.yoyuzh.identity.access.api.DevLoginRoleResolver;
import com.yoyuzh.identity.access.api.IdentityClientType;
import com.yoyuzh.identity.access.api.IdentityRoleName;
import com.yoyuzh.identity.access.api.IdentityCredentialIssuer;
import com.yoyuzh.identity.access.api.IdentityStorageUsageQuery;
import com.yoyuzh.identity.access.api.IdentityUserSnapshot;
import com.yoyuzh.identity.access.api.IssuedAuthCredentials;
import com.yoyuzh.identity.access.api.LoginRequest;
import com.yoyuzh.identity.access.api.LoginAdmissionPolicy;
import com.yoyuzh.identity.access.api.PasswordChangeAttempt;
import com.yoyuzh.identity.access.api.PasswordChangePolicy;
import com.yoyuzh.identity.access.api.ProfileUpdateAdmissionPolicy;
import com.yoyuzh.identity.access.api.ProfileUpdateAttempt;
import com.yoyuzh.identity.access.api.RegistrationAdmissionPolicy;
import com.yoyuzh.identity.access.api.RegistrationAttempt;
import com.yoyuzh.identity.access.api.RegisterRequest;
import com.yoyuzh.identity.access.api.UpdateUserAvatarRequest;
import com.yoyuzh.identity.access.api.UpdateUserPasswordRequest;
import com.yoyuzh.identity.access.api.UpdateUserProfileRequest;
import com.yoyuzh.identity.access.api.UserCapacityResponse;
import com.yoyuzh.identity.access.api.UserProfileResponse;
import com.yoyuzh.identity.access.api.UserSettingsResponse;
import com.yoyuzh.identity.access.internal.domain.User;
import com.yoyuzh.identity.access.internal.domain.UserRole;
import com.yoyuzh.identity.access.internal.infra.UserRepository;
import com.yoyuzh.files.upload.InitiateUploadResponse;
import com.yoyuzh.files.workspace.api.WorkspaceBootstrapApi;
import com.yoyuzh.files.workspace.api.WorkspaceUserContext;
import com.yoyuzh.shared.kernel.BusinessException;
import com.yoyuzh.shared.kernel.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final WorkspaceBootstrapApi workspaceBootstrapApi;
    private final AvatarService avatarService;
    private final RegistrationAdmissionPolicy registrationAdmissionPolicy;
    private final DevLoginRoleResolver devLoginRoleResolver;
    private final ProfileUpdateAdmissionPolicy profileUpdateAdmissionPolicy;
    private final LoginAdmissionPolicy loginAdmissionPolicy;
    private final PasswordChangePolicy passwordChangePolicy;
    private final IdentityCredentialIssuer identityCredentialIssuer;
    private final IdentityStorageUsageQuery identityStorageUsageQuery;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        return register(request, IdentityClientType.DESKTOP);
    }

    @Transactional
    public AuthResponse register(RegisterRequest request, IdentityClientType clientType) {
        registrationAdmissionPolicy.assertAllowed(
                new RegistrationAttempt(
                        request.username(),
                        request.email(),
                        request.phoneNumber(),
                        request.inviteCode()));

        User user = new User();
        user.setUsername(request.username());
        user.setDisplayName(request.username());
        user.setEmail(request.email());
        user.setPhoneNumber(request.phoneNumber());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(UserRole.USER);
        user.setPreferredLanguage("zh-CN");
        User saved = userRepository.save(user);
        workspaceBootstrapApi.ensureDefaultDirectories(workspaceUser(saved));
        return toAuthResponse(identityCredentialIssuer.issueFresh(saved.getId(), clientType));
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        return login(request, IdentityClientType.DESKTOP);
    }

    @Transactional
    public AuthResponse login(LoginRequest request, IdentityClientType clientType) {
        loginAdmissionPolicy.assertAllowed(request.username(), request.password());

        User user = userRepository.findByUsername(request.username())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_LOGGED_IN, "用户不存在"));
        workspaceBootstrapApi.ensureDefaultDirectories(workspaceUser(user));
        return toAuthResponse(identityCredentialIssuer.issueFresh(user.getId(), clientType));
    }

    @Transactional
    public AuthResponse devLogin(String username) {
        return devLogin(username, IdentityClientType.DESKTOP);
    }

    @Transactional
    public AuthResponse devLogin(String username, IdentityClientType clientType) {
        String candidate = username == null ? "" : username.trim();
        if (candidate.isEmpty()) {
            candidate = "1";
        }

        final String finalCandidate = candidate;
        UserRole desiredRole = toUserRole(devLoginRoleResolver.resolveRoleForUsername(finalCandidate));
        User user = userRepository.findByUsername(finalCandidate).map(existing -> {
            if (existing.getRole() != desiredRole) {
                existing.setRole(desiredRole);
                return userRepository.save(existing);
            }
            return existing;
        }).orElseGet(() -> {
            User created = new User();
            created.setUsername(finalCandidate);
            created.setDisplayName(finalCandidate);
            created.setEmail(finalCandidate + "@dev.local");
            created.setPasswordHash(passwordEncoder.encode("1"));
            created.setRole(desiredRole);
            created.setPreferredLanguage("zh-CN");
            return userRepository.save(created);
        });
        workspaceBootstrapApi.ensureDefaultDirectories(workspaceUser(user));
        return toAuthResponse(identityCredentialIssuer.issueFresh(user.getId(), clientType));
    }

    @Transactional
    public AuthResponse refresh(String refreshToken) {
        return refresh(refreshToken, IdentityClientType.DESKTOP);
    }

    @Transactional
    public AuthResponse refresh(String refreshToken, IdentityClientType defaultClientType) {
        return toAuthResponse(identityCredentialIssuer.refresh(refreshToken, defaultClientType));
    }

    public UserProfileResponse getProfile(String username) {
        return toProfile(findUserByUsername(username));
    }

    public UserCapacityResponse getCapacity(String username) {
        User user = findUserByUsername(username);
        long totalBytes = user.getStorageQuotaBytes();
        long usedBytes = identityStorageUsageQuery.usedStorageBytes(user.getId());
        long availableBytes = Math.max(0L, totalBytes - usedBytes);
        return new UserCapacityResponse(totalBytes, usedBytes, availableBytes, user.getMaxUploadSizeBytes());
    }

    public UserSettingsResponse getSettings(String username) {
        User user = findUserByUsername(username);
        return new UserSettingsResponse(
                user.getDisplayName(),
                user.getPreferredLanguage(),
                "system",
                false
        );
    }

    @Transactional
    public UserProfileResponse updateProfile(String username, UpdateUserProfileRequest request) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_LOGGED_IN, "用户不存在"));

        String nextEmail = request.email().trim();
        String nextPhoneNumber = request.phoneNumber().trim();
        profileUpdateAdmissionPolicy.assertAllowed(
                new ProfileUpdateAttempt(user.getEmail(), user.getPhoneNumber(), nextEmail, nextPhoneNumber));

        user.setDisplayName(request.displayName().trim());
        user.setEmail(nextEmail);
        user.setPhoneNumber(nextPhoneNumber);
        user.setBio(normalizeOptionalText(request.bio()));
        user.setPreferredLanguage(normalizePreferredLanguage(request.preferredLanguage()));
        return toProfile(userRepository.save(user));
    }

    @Transactional
    public AuthResponse changePassword(String username, UpdateUserPasswordRequest request) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_LOGGED_IN, "用户不存在"));
        return toAuthResponse(passwordChangePolicy.changePassword(
                user.getId(),
                new PasswordChangeAttempt(request.currentPassword(), request.newPassword(), IdentityClientType.DESKTOP)));
    }

    public InitiateUploadResponse initiateAvatarUpload(String username, UpdateUserAvatarRequest request) {
        return avatarService.initiateAvatarUpload(username, request);
    }

    public void uploadAvatar(String username, String storageName, MultipartFile file) {
        avatarService.uploadAvatar(username, storageName, file);
    }

    @Transactional
    public UserProfileResponse completeAvatarUpload(String username, UpdateUserAvatarRequest request) {
        return toProfile(avatarService.completeAvatarUpload(username, request));
    }

    public AvatarDownloadResult getAvatarContent(String username) {
        return avatarService.getAvatarContent(username);
    }

    private UserProfileResponse toProfile(User user) {
        return new UserProfileResponse(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getEmail(),
                user.getPhoneNumber(),
                user.getBio(),
                user.getPreferredLanguage(),
                avatarService.buildAvatarUrl(user),
                toIdentityRoleName(user.getRole()),
                user.getCreatedAt(),
                user.getStorageQuotaBytes(),
                user.getMaxUploadSizeBytes()
        );
    }

    private User findUserByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_LOGGED_IN, "用户不存在"));
    }

    private AuthResponse toAuthResponse(IssuedAuthCredentials issuedAuthCredentials) {
        return AuthResponse.issued(
                issuedAuthCredentials.accessToken(),
                issuedAuthCredentials.refreshToken(),
                toProfile(issuedAuthCredentials.user()));
    }

    private UserProfileResponse toProfile(IdentityUserSnapshot user) {
        return new UserProfileResponse(
                user.id(),
                user.username(),
                user.displayName(),
                user.email(),
                user.phoneNumber(),
                user.bio(),
                user.preferredLanguage(),
                avatarService.buildAvatarUrl(user),
                user.role(),
                user.createdAt(),
                user.storageQuotaBytes(),
                user.maxUploadSizeBytes()
        );
    }

    private String normalizeOptionalText(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private UserRole toUserRole(IdentityRoleName roleName) {
        if (roleName == IdentityRoleName.ADMIN) {
            return UserRole.ADMIN;
        }
        if (roleName == IdentityRoleName.MODERATOR) {
            return UserRole.MODERATOR;
        }
        return UserRole.USER;
    }

    private IdentityRoleName toIdentityRoleName(UserRole role) {
        if (role == null) {
            return IdentityRoleName.USER;
        }
        return IdentityRoleName.valueOf(role.name());
    }

    private String normalizePreferredLanguage(String preferredLanguage) {
        if (preferredLanguage == null || preferredLanguage.trim().isEmpty()) {
            return "zh-CN";
        }
        return preferredLanguage.trim();
    }

    private WorkspaceUserContext workspaceUser(User user) {
        return new WorkspaceUserContext(
                user.getId(),
                user.getStorageQuotaBytes(),
                user.getMaxUploadSizeBytes()
        );
    }
}
