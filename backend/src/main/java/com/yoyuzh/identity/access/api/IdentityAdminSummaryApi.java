package com.yoyuzh.identity.access.api;

public interface IdentityAdminSummaryApi {

    long countUsersAsAdmin();

    String currentInviteCode();

    String updateInviteCode(String inviteCode);

    String rotateInviteCode();
}
