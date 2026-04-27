package com.yoyuzh.boot.security;

import com.yoyuzh.identity.access.api.IdentityAuthenticatedUser;
import com.yoyuzh.identity.access.api.IdentityAuthenticationApi;
import com.yoyuzh.shared.kernel.BusinessException;
import com.yoyuzh.shared.kernel.ErrorCode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final IdentityAuthenticationApi identityAuthenticationApi;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        IdentityAuthenticatedUser user = identityAuthenticationApi.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("用户不存在"));
        return toUserDetails(user);
    }

    public UserDetails toUserDetails(IdentityAuthenticatedUser user) {
        return new AuthenticatedUserPrincipal(
                user.id(),
                user.username(),
                user.passwordHash(),
                List.of(new SimpleGrantedAuthority("ROLE_" + user.role().name())),
                !user.banned()
        );
    }

    public IdentityAuthenticatedUser loadAuthenticatedUser(String username) {
        return identityAuthenticationApi.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_LOGGED_IN, "用户不存在"));
    }

    public Long loadUserId(String username) {
        return loadAuthenticatedUser(username).id();
    }
}
