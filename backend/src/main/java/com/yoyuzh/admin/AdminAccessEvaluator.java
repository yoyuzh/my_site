package com.yoyuzh.admin;

import com.yoyuzh.auth.UserRole;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class AdminAccessEvaluator {

    public boolean isAdmin(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .map(this::toUserRole)
                .filter(Objects::nonNull)
                .anyMatch(UserRole::canAccessAdmin);
    }

    private UserRole toUserRole(String authority) {
        if (authority == null || !authority.startsWith("ROLE_")) {
            return null;
        }
        try {
            return UserRole.valueOf(authority.substring("ROLE_".length()));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
