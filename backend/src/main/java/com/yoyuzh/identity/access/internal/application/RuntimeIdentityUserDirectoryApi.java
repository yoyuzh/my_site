package com.yoyuzh.identity.access.internal.application;

import com.yoyuzh.identity.access.internal.domain.User;
import com.yoyuzh.identity.access.internal.infra.UserRepository;
import com.yoyuzh.identity.access.api.IdentityUserDirectoryApi;
import com.yoyuzh.identity.access.api.IdentityUserProfileSummary;
import com.yoyuzh.identity.access.api.IdentityUserSnapshot;
import com.yoyuzh.identity.access.api.IdentityRoleName;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RuntimeIdentityUserDirectoryApi implements IdentityUserDirectoryApi {

    private final UserRepository userRepository;

    @Override
    public Map<Long, IdentityUserProfileSummary> findProfilesByIds(Set<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, this::toSummary));
    }

    @Override
    public Optional<IdentityUserProfileSummary> findProfileById(Long userId) {
        return userRepository.findById(userId)
                .map(this::toSummary);
    }

    @Override
    public Optional<IdentityUserSnapshot> findSnapshotById(Long userId) {
        return userRepository.findById(userId)
                .map(this::toSnapshot);
    }

    @Override
    public Optional<IdentityUserProfileSummary> findProfileByUsername(String username) {
        return userRepository.findByUsername(username)
                .map(this::toSummary);
    }

    private IdentityUserProfileSummary toSummary(User user) {
        return new IdentityUserProfileSummary(
                user.getId(),
                user.getUsername(),
                user.getEmail()
        );
    }

    private IdentityUserSnapshot toSnapshot(User user) {
        return new IdentityUserSnapshot(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getEmail(),
                user.getPhoneNumber(),
                user.getBio(),
                user.getPreferredLanguage(),
                user.getAvatarStorageName(),
                user.getAvatarContentType(),
                user.getAvatarUpdatedAt(),
                user.getRole() == null ? IdentityRoleName.USER : IdentityRoleName.valueOf(user.getRole().name()),
                user.getCreatedAt(),
                user.getStorageQuotaBytes(),
                user.getMaxUploadSizeBytes()
        );
    }
}
