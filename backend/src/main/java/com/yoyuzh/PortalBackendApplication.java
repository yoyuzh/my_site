package com.yoyuzh;

import com.yoyuzh.infra.cache.AppRedisProperties;
import com.yoyuzh.boot.security.CorsProperties;
import com.yoyuzh.boot.security.JwtProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({
        JwtProperties.class,
        CorsProperties.class,
        AppRedisProperties.class
})
public class PortalBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(PortalBackendApplication.class, args);
    }
}
