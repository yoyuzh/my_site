package com.yoyuzh.admin;

import com.yoyuzh.identity.access.api.AdminAccessPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminAccessEvaluatorTest {

    @Mock
    private AdminAccessPolicy adminAccessPolicy;

    private AdminAccessEvaluator adminAccessEvaluator;

    @BeforeEach
    void setUp() {
        adminAccessEvaluator = new AdminAccessEvaluator(adminAccessPolicy);
    }

    @Test
    void shouldReturnFalseWhenAuthenticationIsMissing() {
        assertThat(adminAccessEvaluator.isAdmin(null)).isFalse();
    }

    @Test
    void shouldReturnFalseWhenAuthenticationIsNotAuthenticated() {
        Authentication authentication = new TestingAuthenticationToken("alice", "password");
        authentication.setAuthenticated(false);

        assertThat(adminAccessEvaluator.isAdmin(authentication)).isFalse();
    }

    @Test
    void shouldDelegateAdminCapabilityCheck() {
        Authentication authentication = new TestingAuthenticationToken(
                "alice",
                "password",
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
        );
        when(adminAccessPolicy.hasAdminAccess(authentication)).thenReturn(true);

        assertThat(adminAccessEvaluator.isAdmin(authentication)).isTrue();
    }
}
