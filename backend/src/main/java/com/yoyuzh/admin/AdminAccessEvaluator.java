package com.yoyuzh.admin;

import com.yoyuzh.config.AdminProperties;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.stream.Collectors;

@Component
public class AdminAccessEvaluator {

    private final Set<String> adminUsernames;

    public AdminAccessEvaluator(AdminProperties adminProperties) {
        this.adminUsernames = adminProperties.getUsernames().stream()
                .map(username -> username == null ? "" : username.trim())
                .filter(username -> !username.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    public boolean isAdmin(Authentication authentication) {
        return authentication != null
                && authentication.isAuthenticated()
                && adminUsernames.contains(authentication.getName());
    }
}
