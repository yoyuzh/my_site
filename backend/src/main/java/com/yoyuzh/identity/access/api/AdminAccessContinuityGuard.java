package com.yoyuzh.identity.access.api;

public interface AdminAccessContinuityGuard {

    void ensureAdminAccessRemainsAvailable(
            String currentRole,
            boolean currentlyBanned,
            String nextRole,
            boolean bannedAfterUpdate);
}
