package com.yoyuzh.identity.access.api;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

public interface IdentityUserDirectoryApi {

    Map<Long, IdentityUserProfileSummary> findProfilesByIds(Set<Long> userIds);

    Optional<IdentityUserProfileSummary> findProfileById(Long userId);

    Optional<IdentityUserProfileSummary> findProfileByUsername(String username);
}
