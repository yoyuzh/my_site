package com.yoyuzh.identity.access.internal.application;

import com.yoyuzh.shared.kernel.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SpringLoginAdmissionPolicyTest {

    @Mock
    private AuthenticationManager authenticationManager;

    @Test
    void shouldAuthenticateWhenCredentialsAreValid() {
        SpringLoginAdmissionPolicy policy = new SpringLoginAdmissionPolicy(authenticationManager);

        assertThatCode(() -> policy.assertAllowed("alice", "plain-password")).doesNotThrowAnyException();

        verify(authenticationManager)
                .authenticate(new UsernamePasswordAuthenticationToken("alice", "plain-password"));
    }

    @Test
    void shouldTranslateBadCredentialsToBusinessException() {
        SpringLoginAdmissionPolicy policy = new SpringLoginAdmissionPolicy(authenticationManager);
        when(authenticationManager.authenticate(new UsernamePasswordAuthenticationToken("alice", "wrong-password")))
                .thenThrow(new BadCredentialsException("bad credentials"));

        assertThatThrownBy(() -> policy.assertAllowed("alice", "wrong-password"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("用户名或密码错误");
    }

    @Test
    void shouldTranslateDisabledUserToBusinessException() {
        SpringLoginAdmissionPolicy policy = new SpringLoginAdmissionPolicy(authenticationManager);
        when(authenticationManager.authenticate(new UsernamePasswordAuthenticationToken("alice", "plain-password")))
                .thenThrow(new DisabledException("disabled"));

        assertThatThrownBy(() -> policy.assertAllowed("alice", "plain-password"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("账号已被封禁");
    }
}
