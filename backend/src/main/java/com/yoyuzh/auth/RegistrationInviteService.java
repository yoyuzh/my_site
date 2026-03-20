package com.yoyuzh.auth;

import com.yoyuzh.common.BusinessException;
import com.yoyuzh.common.ErrorCode;
import com.yoyuzh.config.RegistrationProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;

@Service
@RequiredArgsConstructor
public class RegistrationInviteService {

    private static final Long STATE_ID = 1L;
    private static final String INVITE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";
    private static final int INVITE_LENGTH = 16;

    private final RegistrationInviteStateRepository registrationInviteStateRepository;
    private final RegistrationProperties registrationProperties;
    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional
    public String getCurrentInviteCode() {
        return ensureCurrentState().getInviteCode();
    }

    @Transactional
    public void consumeInviteCode(String inviteCode) {
        RegistrationInviteState state = ensureCurrentStateForUpdate();
        String candidateCode = normalize(inviteCode);
        if (!state.getInviteCode().equals(candidateCode)) {
            throw new BusinessException(ErrorCode.PERMISSION_DENIED, "邀请码错误");
        }

        state.setInviteCode(generateNextInviteCode(state.getInviteCode()));
        registrationInviteStateRepository.save(state);
    }

    private RegistrationInviteState ensureCurrentState() {
        return registrationInviteStateRepository.findById(STATE_ID)
                .orElseGet(this::createInitialState);
    }

    private RegistrationInviteState ensureCurrentStateForUpdate() {
        return registrationInviteStateRepository.findByIdForUpdate(STATE_ID)
                .orElseGet(() -> {
                    createInitialState();
                    return registrationInviteStateRepository.findByIdForUpdate(STATE_ID)
                            .orElseThrow(() -> new IllegalStateException("邀请码状态初始化失败"));
                });
    }

    private RegistrationInviteState createInitialState() {
        RegistrationInviteState state = new RegistrationInviteState();
        state.setId(STATE_ID);
        state.setInviteCode(resolveInitialInviteCode());
        try {
            return registrationInviteStateRepository.saveAndFlush(state);
        } catch (DataIntegrityViolationException ignored) {
            return registrationInviteStateRepository.findById(STATE_ID)
                    .orElseThrow(() -> ignored);
        }
    }

    private String resolveInitialInviteCode() {
        String configuredInviteCode = normalize(registrationProperties.getInviteCode());
        if (StringUtils.hasText(configuredInviteCode)) {
            return configuredInviteCode;
        }
        return generateInviteCode();
    }

    private String generateNextInviteCode(String currentInviteCode) {
        String nextCode = generateInviteCode();
        while (nextCode.equals(currentInviteCode)) {
            nextCode = generateInviteCode();
        }
        return nextCode;
    }

    private String generateInviteCode() {
        StringBuilder builder = new StringBuilder(INVITE_LENGTH);
        for (int i = 0; i < INVITE_LENGTH; i += 1) {
            builder.append(INVITE_CHARS.charAt(secureRandom.nextInt(INVITE_CHARS.length())));
        }
        return builder.toString();
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
