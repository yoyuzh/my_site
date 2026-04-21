package com.yoyuzh.files.search.internal.infra;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

@Configuration
public class FileEventRedisPubSubConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "app.redis", name = "enabled", havingValue = "true")
    public RedisMessageListenerContainer fileEventRedisMessageListenerContainer(
            RedisConnectionFactory redisConnectionFactory,
            RedisFileEventPubSubListener listener) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(redisConnectionFactory);
        container.addMessageListener(listener, new ChannelTopic(listener.buildTopic()));
        return container;
    }
}
