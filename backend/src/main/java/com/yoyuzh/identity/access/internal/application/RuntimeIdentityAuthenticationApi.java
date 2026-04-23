package com.yoyuzh.identity.access.internal.application;

import com.yoyuzh.identity.access.api.IdentityAuthenticatedUser;
import com.yoyuzh.identity.access.api.IdentityAuthenticationApi;
import com.yoyuzh.identity.access.api.IdentityRoleName;
import com.yoyuzh.identity.access.internal.domain.User;
import com.yoyuzh.identity.access.internal.infra.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
class RuntimeIdentityAuthenticationApi implements IdentityAuthenticationApi {

    private final UserRepository userRepository;

    @Override
    public Optional<IdentityAuthenticatedUser> findByUsername(String username) {
        return userRepository.findByUsername(username).map(this::toAuthenticatedUser);
    }

    private IdentityAuthenticatedUser toAuthenticatedUser(User user) {
        return new IdentityAuthenticatedUser(
                user.getId(),
                user.getUsername(),
                user.getPasswordHash(),
                IdentityRoleName.valueOf(user.getRole().name()),
                user.isBanned(),
                user.getActiveSessionId(),
                user.getDesktopActiveSessionId(),
                user.getMobileActiveSessionId(),
                user.getStorageQuotaBytes(),
                user.getMaxUploadSizeBytes()
        );
    }
}
