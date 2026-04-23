package com.yoyuzh.identity.access.api;

import java.util.Optional;

public interface IdentityAuthenticationApi {

    Optional<IdentityAuthenticatedUser> findByUsername(String username);
}
