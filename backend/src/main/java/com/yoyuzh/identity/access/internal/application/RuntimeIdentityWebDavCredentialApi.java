package com.yoyuzh.identity.access.internal.application;

import com.yoyuzh.identity.access.api.IdentityAuthenticatedUser;
import com.yoyuzh.identity.access.api.IdentityRoleName;
import com.yoyuzh.identity.access.api.IdentityWebDavCredentialApi;
import com.yoyuzh.identity.access.api.IdentityWebDavCredentialIssueResult;
import com.yoyuzh.identity.access.api.IdentityWebDavCredentialStatus;
import com.yoyuzh.identity.access.internal.domain.User;
import com.yoyuzh.identity.access.internal.domain.WebDavCredential;
import com.yoyuzh.identity.access.internal.infra.UserRepository;
import com.yoyuzh.identity.access.internal.infra.WebDavCredentialRepository;
import com.yoyuzh.shared.kernel.BusinessException;
import com.yoyuzh.shared.kernel.ErrorCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Optional;

@Service
public class RuntimeIdentityWebDavCredentialApi implements IdentityWebDavCredentialApi {

    private static final int RAW_CREDENTIAL_BYTES = 32;

    private final UserRepository userRepository;
    private final WebDavCredentialRepository credentialRepository;
    private final PasswordEncoder passwordEncoder;
    private final SecureRandom secureRandom;
    private final Clock clock;

    @Autowired
    public RuntimeIdentityWebDavCredentialApi(UserRepository userRepository,
                                              WebDavCredentialRepository credentialRepository,
                                              PasswordEncoder passwordEncoder) {
        this(userRepository, credentialRepository, passwordEncoder, new SecureRandom(), Clock.systemUTC());
    }

    RuntimeIdentityWebDavCredentialApi(UserRepository userRepository,
                                       WebDavCredentialRepository credentialRepository,
                                       PasswordEncoder passwordEncoder,
                                       SecureRandom secureRandom,
                                       Clock clock) {
        this.userRepository = userRepository;
        this.credentialRepository = credentialRepository;
        this.passwordEncoder = passwordEncoder;
        this.secureRandom = secureRandom;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public IdentityWebDavCredentialStatus getCredentialStatus(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_LOGGED_IN, "用户不存在"));
        return credentialRepository.findByUserId(user.getId())
                .map(credential -> new IdentityWebDavCredentialStatus(
                        user.getId(),
                        credential.isEnabled(),
                        credential.getCreatedAt(),
                        credential.getUpdatedAt()
                ))
                .orElseGet(() -> IdentityWebDavCredentialStatus.missing(user.getId()));
    }

    @Override
    @Transactional
    public IdentityWebDavCredentialIssueResult issueOrReplaceCredential(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_LOGGED_IN, "用户不存在"));
        String plaintextPassword = generateCredential();
        String passwordHash = passwordEncoder.encode(plaintextPassword);
        LocalDateTime now = LocalDateTime.now(clock);
        WebDavCredential credential = credentialRepository.findByUserId(user.getId())
                .orElseGet(() -> WebDavCredential.create(user.getId(), passwordHash, now));
        credential.replacePasswordHash(passwordHash, now);
        credentialRepository.save(credential);
        return new IdentityWebDavCredentialIssueResult(user.getId(), plaintextPassword);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<IdentityAuthenticatedUser> authenticate(String username, String plaintextPassword) {
        if (isBlank(username) || isBlank(plaintextPassword)) {
            return Optional.empty();
        }
        Optional<User> user = userRepository.findByUsername(username.trim());
        if (user.isEmpty() || user.get().isBanned()) {
            return Optional.empty();
        }
        Optional<WebDavCredential> credential = credentialRepository.findByUserId(user.get().getId());
        if (credential.isEmpty() || !credential.get().isEnabled()) {
            return Optional.empty();
        }
        if (!passwordEncoder.matches(plaintextPassword, credential.get().getPasswordHash())) {
            return Optional.empty();
        }
        return Optional.of(toAuthenticatedUser(user.get()));
    }

    private String generateCredential() {
        byte[] bytes = new byte[RAW_CREDENTIAL_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
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

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
