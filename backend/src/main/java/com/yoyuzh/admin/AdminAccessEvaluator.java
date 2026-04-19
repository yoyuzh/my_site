package com.yoyuzh.admin;

import com.yoyuzh.identity.access.api.AdminAccessPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AdminAccessEvaluator {

    private final AdminAccessPolicy adminAccessPolicy;

    public boolean isAdmin(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        return adminAccessPolicy.hasAdminAccess(authentication);
    }
}
