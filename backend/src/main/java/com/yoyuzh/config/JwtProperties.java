package com.yoyuzh.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.jwt")
public class JwtProperties {

    private String secret = "";
    private long accessExpirationSeconds = 900;
    private long refreshExpirationSeconds = 1209600;

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public long getAccessExpirationSeconds() {
        return accessExpirationSeconds;
    }

    public void setAccessExpirationSeconds(long accessExpirationSeconds) {
        this.accessExpirationSeconds = accessExpirationSeconds;
    }

    public long getRefreshExpirationSeconds() {
        return refreshExpirationSeconds;
    }

    public void setRefreshExpirationSeconds(long refreshExpirationSeconds) {
        this.refreshExpirationSeconds = refreshExpirationSeconds;
    }
}
