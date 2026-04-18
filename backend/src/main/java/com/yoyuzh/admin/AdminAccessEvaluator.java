package com.yoyuzh.admin;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class AdminAccessEvaluator {

    private static final String ROLE_PREFIX = "ROLE_";

    private final AdminRuntimeSettingsService adminRuntimeSettingsService;

    public AdminAccessEvaluator(AdminRuntimeSettingsService adminRuntimeSettingsService) {
        this.adminRuntimeSettingsService = adminRuntimeSettingsService;
    }

    public boolean isAdmin(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        Set<String> allowedManagementRoles = resolveAllowedManagementRoles();
        if (allowedManagementRoles.isEmpty()) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .map(this::toRoleName)
                .filter(Objects::nonNull)
                .anyMatch(allowedManagementRoles::contains);
    }

    private Set<String> resolveAllowedManagementRoles() {
        return adminRuntimeSettingsService.snapshot()
                .registrationManagementRoles()
                .stream()
                .map(AdminRuntimeSettingsService::normalizeManagementRole)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private String toRoleName(String authority) {
        if (authority == null || !authority.startsWith(ROLE_PREFIX)) {
            return null;
        }
        String roleName = authority.substring(ROLE_PREFIX.length()).trim();
        if (roleName.isEmpty()) {
            return null;
        }
        return roleName.toUpperCase(Locale.ROOT);
    }
}
