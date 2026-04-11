package com.yoyuzh.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yoyuzh.files.storage.FileContentStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringJUnitConfig(AndroidReleaseServiceCacheTest.CacheTestConfiguration.class)
class AndroidReleaseServiceCacheTest {

    @Autowired
    private AndroidReleaseService androidReleaseService;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private FileContentStorage fileContentStorage;

    @BeforeEach
    void setUp() {
        cacheManager.getCache(RedisCacheNames.ANDROID_RELEASE).clear();
        reset(fileContentStorage);
    }

    @Test
    void shouldCacheLatestReleaseMetadata() {
        when(fileContentStorage.readBlob("android/releases/latest.json")).thenReturn("""
                {
                  "objectKey": "android/releases/yoyuzh-portal-2026.04.03.1754.apk",
                  "fileName": "yoyuzh-portal-2026.04.03.1754.apk",
                  "versionCode": "260931754",
                  "versionName": "2026.04.03.1754",
                  "publishedAt": "2026-04-03T09:54:00Z"
                }
                """.getBytes());

        androidReleaseService.getLatestRelease();
        androidReleaseService.getLatestRelease();

        verify(fileContentStorage, times(1)).readBlob("android/releases/latest.json");
    }

    @Configuration
    @EnableCaching
    static class CacheTestConfiguration {

        @Bean
        CacheManager cacheManager() {
            return new ConcurrentMapCacheManager(RedisCacheNames.ANDROID_RELEASE);
        }

        @Bean
        FileContentStorage fileContentStorage() {
            return mock(FileContentStorage.class);
        }

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        @Bean
        AndroidReleaseProperties androidReleaseProperties() {
            return new AndroidReleaseProperties();
        }

        @Bean
        AndroidReleaseService androidReleaseService(FileContentStorage fileContentStorage,
                                                    ObjectMapper objectMapper,
                                                    AndroidReleaseProperties androidReleaseProperties) {
            return new AndroidReleaseService(fileContentStorage, objectMapper, androidReleaseProperties);
        }
    }
}
