package com.yoyuzh.identity.access.api;

public interface DevLoginRoleResolver {

    IdentityRoleName resolveRoleForUsername(String username);
}
