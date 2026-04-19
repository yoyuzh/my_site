package com.yoyuzh.identity.access.internal.application;

import com.yoyuzh.common.BusinessException;
import com.yoyuzh.common.ErrorCode;
import com.yoyuzh.identity.access.api.LoginAdmissionPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SpringLoginAdmissionPolicy implements LoginAdmissionPolicy {

    private final AuthenticationManager authenticationManager;

    @Override
    public void assertAllowed(String username, String password) {
        try {
            authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(username, password));
        } catch (DisabledException ex) {
            throw new BusinessException(ErrorCode.PERMISSION_DENIED, "账号已被封禁");
        } catch (BadCredentialsException ex) {
            throw new BusinessException(ErrorCode.NOT_LOGGED_IN, "用户名或密码错误");
        }
    }
}
