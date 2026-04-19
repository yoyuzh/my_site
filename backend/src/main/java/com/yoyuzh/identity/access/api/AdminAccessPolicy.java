package com.yoyuzh.identity.access.api;

import org.springframework.security.core.Authentication;

public interface AdminAccessPolicy {

    boolean hasAdminAccess(Authentication authentication);
}
