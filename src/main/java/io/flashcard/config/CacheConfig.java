package io.flashcard.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;


@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    @org.springframework.boot.autoconfigure.condition.ConditionalOnBean(RedisConnectionFactory.class)
    @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "spring.cache.type", havingValue = "redis", matchIfMissing = true)
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        var jsonSerializer = RedisSerializationContext.SerializationPair
            .fromSerializer(new GenericJackson2JsonRedisSerializer());

        RedisCacheConfiguration defaults = RedisCacheConfiguration.defaultCacheConfig()
            .serializeValuesWith(jsonSerializer)
            .prefixCacheNameWith("fcio:");

        return RedisCacheManager.builder(connectionFactory)
            .cacheDefaults(defaults)
            .build();
    }

    @Bean
    @org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean(org.springframework.cache.CacheManager.class)
    public org.springframework.cache.concurrent.ConcurrentMapCacheManager fallbackCacheManager() {
        return new org.springframework.cache.concurrent.ConcurrentMapCacheManager();
    }
}
