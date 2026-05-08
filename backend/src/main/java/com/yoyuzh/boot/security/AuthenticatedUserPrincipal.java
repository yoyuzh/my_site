package com.yoyuzh.boot.security;

import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public class AuthenticatedUserPrincipal implements UserDetails {

    private final Long userId;
    private final String username;
    private final String password;
    private final Long storageQuotaBytes;
    private final Long maxUploadSizeBytes;
    private final List<? extends GrantedAuthority> authorities;
    private final boolean enabled;

    public AuthenticatedUserPrincipal(Long userId,
                                      String username,
                                      String password,
                                      Long storageQuotaBytes,
                                      Long maxUploadSizeBytes,
                                      Collection<? extends GrantedAuthority> authorities,
                                      boolean enabled) {
        this.userId = userId;
        this.username = username;
        this.password = password;
        this.storageQuotaBytes = storageQuotaBytes;
        this.maxUploadSizeBytes = maxUploadSizeBytes;
        this.authorities = List.copyOf(authorities);
        this.enabled = enabled;
    }

    public Long getUserId() {
        return userId;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return password;
    }

    public Long getStorageQuotaBytes() {
        return storageQuotaBytes;
    }

    public Long getMaxUploadSizeBytes() {
        return maxUploadSizeBytes;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public String toString() {
        return username;
    }
}
