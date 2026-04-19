package com.yoyuzh;

import com.yoyuzh.app.android.internal.infra.AndroidReleaseProperties;
import com.yoyuzh.infra.cache.AppRedisProperties;
import com.yoyuzh.boot.security.CorsProperties;
import com.yoyuzh.platform.storage.internal.infra.FileStorageProperties;
import com.yoyuzh.boot.security.JwtProperties;
import com.yoyuzh.identity.access.internal.infra.AdminProperties;
import com.yoyuzh.identity.access.internal.infra.RegistrationProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({
        JwtProperties.class,
        FileStorageProperties.class,
        CorsProperties.class,
        AdminProperties.class,
        RegistrationProperties.class,
        AndroidReleaseProperties.class,
        AppRedisProperties.class
})
public class PortalBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(PortalBackendApplication.class, args);
    }
}
