package com.yoyuzh.infra.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.support.NoOpCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
@EnableCaching
public class RedisConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "app.redis", name = "enabled", havingValue = "true")
    public CacheManager redisCacheManager(RedisConnectionFactory redisConnectionFactory,
                                          ObjectMapper objectMapper,
                                          AppRedisProperties redisProperties) {
        RedisCacheConfiguration baseConfiguration = RedisCacheConfiguration.defaultCacheConfig()
                .computePrefixWith(cacheName -> redisProperties.getKeyPrefix()
                        + ":" + redisProperties.getNamespaces().getCache()
                        + ":" + cacheName + ":")
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(
                        redisValueSerializer(objectMapper)))
                .disableCachingNullValues();

        Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();
        cacheConfigurations.put(
                RedisCacheNames.FILES_LIST,
                baseConfiguration.entryTtl(Duration.ofSeconds(redisProperties.getCache().getFilesListTtlSeconds()))
        );
        cacheConfigurations.put(
                RedisCacheNames.ADMIN_SUMMARY,
                baseConfiguration.entryTtl(Duration.ofSeconds(redisProperties.getCache().getAdminSummaryTtlSeconds()))
        );
        cacheConfigurations.put(
                RedisCacheNames.STORAGE_POLICIES,
                baseConfiguration.entryTtl(Duration.ofSeconds(redisProperties.getCache().getStoragePoliciesTtlSeconds()))
        );
        cacheConfigurations.put(
                RedisCacheNames.ANDROID_RELEASE,
                baseConfiguration.entryTtl(Duration.ofSeconds(redisProperties.getCache().getAndroidReleaseTtlSeconds()))
        );

        return RedisCacheManager.builder(redisConnectionFactory)
                .cacheDefaults(baseConfiguration)
                .withInitialCacheConfigurations(cacheConfigurations)
                .initialCacheNames(RedisCacheNames.ALL)
                .build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "app.redis", name = "enabled", havingValue = "false", matchIfMissing = true)
    public CacheManager noOpCacheManager() {
        return new NoOpCacheManager();
    }

    static GenericJackson2JsonRedisSerializer redisValueSerializer(ObjectMapper objectMapper) {
        return new GenericJackson2JsonRedisSerializer(objectMapper.copy());
    }
}
