package com.yoyuzh.identity.access.api;

import java.util.Optional;

public interface IdentityWebDavCredentialApi {

    IdentityWebDavCredentialStatus getCredentialStatus(Long userId);

    IdentityWebDavCredentialIssueResult issueOrReplaceCredential(Long userId);

    Optional<IdentityAuthenticatedUser> authenticate(String username, String plaintextPassword);
}
