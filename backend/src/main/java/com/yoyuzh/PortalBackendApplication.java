package com.yoyuzh;

import com.yoyuzh.config.CquApiProperties;
import com.yoyuzh.config.CorsProperties;
import com.yoyuzh.config.FileStorageProperties;
import com.yoyuzh.config.JwtProperties;
import com.yoyuzh.config.AdminProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({
        JwtProperties.class,
        FileStorageProperties.class,
        CquApiProperties.class,
        CorsProperties.class,
        AdminProperties.class
})
public class PortalBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(PortalBackendApplication.class, args);
    }
}
