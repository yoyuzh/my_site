package com.yoyuzh.identity.access.internal.domain;

import com.yoyuzh.identity.access.api.IdentityRoleName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultDevLoginRoleResolverTest {

    private final DefaultDevLoginRoleResolver resolver = new DefaultDevLoginRoleResolver();

    @Test
    void shouldResolveAdminUsernameToAdminRole() {
        assertThat(resolver.resolveRoleForUsername("admin")).isEqualTo(IdentityRoleName.ADMIN);
    }

    @Test
    void shouldResolveOperatorUsernameToModeratorRole() {
        assertThat(resolver.resolveRoleForUsername("operator")).isEqualTo(IdentityRoleName.MODERATOR);
        assertThat(resolver.resolveRoleForUsername("moderator")).isEqualTo(IdentityRoleName.MODERATOR);
    }

    @Test
    void shouldResolveOtherUsernamesToUserRole() {
        assertThat(resolver.resolveRoleForUsername("demo")).isEqualTo(IdentityRoleName.USER);
    }
}
