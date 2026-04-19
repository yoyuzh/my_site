package com.yoyuzh.identity.access.internal.domain;

import com.yoyuzh.identity.access.api.DevLoginRoleResolver;
import com.yoyuzh.identity.access.api.IdentityRoleName;
import org.springframework.stereotype.Component;

@Component
public class DefaultDevLoginRoleResolver implements DevLoginRoleResolver {

    @Override
    public IdentityRoleName resolveRoleForUsername(String username) {
        if ("admin".equalsIgnoreCase(username)) {
            return IdentityRoleName.ADMIN;
        }
        if ("operator".equalsIgnoreCase(username) || "moderator".equalsIgnoreCase(username)) {
            return IdentityRoleName.MODERATOR;
        }
        return IdentityRoleName.USER;
    }
}
