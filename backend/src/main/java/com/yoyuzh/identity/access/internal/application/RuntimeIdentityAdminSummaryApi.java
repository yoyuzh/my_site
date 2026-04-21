package com.yoyuzh.identity.access.internal.application;

import com.yoyuzh.identity.access.internal.infra.UserRepository;
import com.yoyuzh.identity.access.api.IdentityAdminSummaryApi;
import com.yoyuzh.identity.access.internal.application.RegistrationInviteService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RuntimeIdentityAdminSummaryApi implements IdentityAdminSummaryApi {

    private final UserRepository userRepository;
    private final RegistrationInviteService registrationInviteService;

    @Override
    public long countUsersAsAdmin() {
        return userRepository.count();
    }

    @Override
    public String currentInviteCode() {
        return registrationInviteService.getCurrentInviteCode();
    }

    @Override
    public String updateInviteCode(String inviteCode) {
        return registrationInviteService.updateCurrentInviteCode(inviteCode);
    }

    @Override
    public String rotateInviteCode() {
        return registrationInviteService.rotateCurrentInviteCode();
    }
}
