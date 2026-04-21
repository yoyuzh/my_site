package com.yoyuzh.identity.access.internal.application;

import com.yoyuzh.identity.access.internal.domain.User;
import com.yoyuzh.identity.access.internal.infra.UserRepository;
import com.yoyuzh.identity.access.internal.domain.UserRole;
import com.yoyuzh.identity.access.api.AdminAccessContinuityGuard;
import com.yoyuzh.identity.access.api.IdentityAdminUserGovernanceApi;
import com.yoyuzh.identity.access.api.IdentityAdminUserQuery;
import com.yoyuzh.identity.access.api.IdentityAdminUserView;
import com.yoyuzh.identity.access.api.IdentityCredentialRevocationPolicy;
import com.yoyuzh.identity.access.api.IdentityRoleName;
import com.yoyuzh.identity.access.api.PasswordPolicy;
import com.yoyuzh.shared.kernel.BusinessException;
import com.yoyuzh.shared.kernel.ErrorCode;
import com.yoyuzh.shared.kernel.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RuntimeIdentityAdminUserGovernanceApi implements IdentityAdminUserGovernanceApi {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final IdentityCredentialRevocationPolicy identityCredentialRevocationPolicy;
    private final AdminAccessContinuityGuard adminAccessContinuityGuard;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<IdentityAdminUserView> listUsersAsAdmin(IdentityAdminUserQuery query) {
        int page = query.page();
        int size = query.size();
        Page<User> result = userRepository.searchByUsernameOrEmail(
                normalizeQuery(query.query()),
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
        );
        return new PageResponse<>(
                result.getContent().stream().map(this::toView).toList(),
                result.getTotalElements(),
                page,
                size
        );
    }

    @Override
    @Transactional
    public IdentityAdminUserView updateUserRoleAsAdmin(Long userId, IdentityRoleName role) {
        User user = getRequiredUser(userId);
        UserRole nextRole = toUserRole(role);
        ensureAdminAccessRemainsAvailable(user, false, nextRole);
        user.setRole(nextRole);
        return toView(userRepository.save(user));
    }

    @Override
    @Transactional
    public IdentityAdminUserView updateUserBannedAsAdmin(Long userId, boolean banned) {
        User user = getRequiredUser(userId);
        ensureAdminAccessRemainsAvailable(user, banned, user.getRole());
        user.setBanned(banned);
        identityCredentialRevocationPolicy.revokeAll(user);
        return toView(userRepository.save(user));
    }

    @Override
    @Transactional
    public IdentityAdminUserView updateUserPasswordAsAdmin(Long userId, String newPassword) {
        if (!PasswordPolicy.isStrong(newPassword)) {
            throw new BusinessException(ErrorCode.UNKNOWN, PasswordPolicy.VALIDATION_MESSAGE);
        }
        User user = getRequiredUser(userId);
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        identityCredentialRevocationPolicy.revokeAll(user);
        return toView(userRepository.save(user));
    }

    @Override
    @Transactional
    public IdentityAdminUserView updateUserStorageQuotaAsAdmin(Long userId, long storageQuotaBytes) {
        User user = getRequiredUser(userId);
        user.setStorageQuotaBytes(storageQuotaBytes);
        return toView(userRepository.save(user));
    }

    @Override
    @Transactional
    public IdentityAdminUserView updateUserMaxUploadSizeAsAdmin(Long userId, long maxUploadSizeBytes) {
        User user = getRequiredUser(userId);
        user.setMaxUploadSizeBytes(maxUploadSizeBytes);
        return toView(userRepository.save(user));
    }

    private User getRequiredUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNKNOWN, "user not found"));
    }

    private void ensureAdminAccessRemainsAvailable(User user, boolean bannedAfterUpdate, UserRole roleAfterUpdate) {
        adminAccessContinuityGuard.ensureAdminAccessRemainsAvailable(
                user.getRole() == null ? null : user.getRole().name(),
                user.isBanned(),
                roleAfterUpdate == null ? null : roleAfterUpdate.name(),
                bannedAfterUpdate
        );
    }

    private IdentityAdminUserView toView(User user) {
        return new IdentityAdminUserView(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getPhoneNumber(),
                user.getCreatedAt(),
                toIdentityRoleName(user.getRole()),
                user.isBanned(),
                user.getStorageQuotaBytes(),
                user.getMaxUploadSizeBytes()
        );
    }

    private UserRole toUserRole(IdentityRoleName role) {
        if (role == null) {
            return null;
        }
        return UserRole.valueOf(role.name());
    }

    private IdentityRoleName toIdentityRoleName(UserRole role) {
        if (role == null) {
            return null;
        }
        return IdentityRoleName.valueOf(role.name());
    }

    private String normalizeQuery(String value) {
        if (value == null) {
            return "";
        }
        return value.trim();
    }
}
