package com.yoyuzh.identity.access.internal.domain;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class ManagementRolePolicy {

    private static final String ROLE_PREFIX = "ROLE_";

    public boolean hasAdminAccess(Authentication authentication, Collection<String> configuredManagementRoles) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        Set<String> allowedManagementRoles = normalizeConfiguredRoles(configuredManagementRoles);
        if (allowedManagementRoles.isEmpty()) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .map(this::toGrantedRoleName)
                .filter(Objects::nonNull)
                .anyMatch(allowedManagementRoles::contains);
    }

    public Set<String> normalizeConfiguredRoles(Collection<String> configuredManagementRoles) {
        if (configuredManagementRoles == null) {
            return Set.of();
        }
        return configuredManagementRoles.stream()
                .map(this::normalizeConfiguredRole)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private String normalizeConfiguredRole(String configuredRole) {
        if (configuredRole == null) {
            return null;
        }
        String normalized = configuredRole.trim().toUpperCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return null;
        }
        if (normalized.startsWith(ROLE_PREFIX)) {
            normalized = normalized.substring(ROLE_PREFIX.length()).trim();
        }
        return normalized.isEmpty() ? null : normalized;
    }

    private String toGrantedRoleName(String authority) {
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
