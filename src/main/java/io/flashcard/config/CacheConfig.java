package io.flashcard.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.time.Duration;
import java.util.Map;

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
            .prefixCacheNameWith("fcio:")
            .entryTtl(Duration.ofMinutes(30));

        Map<String, RedisCacheConfiguration> perCache = Map.ofEntries(
            // Subscriber data — read per request, written rarely
            entry("subscriber_level", Duration.ofHours(1)),
            entry("subscriber_schedule", Duration.ofHours(4)),
            entry("subscriber_prefs", Duration.ofMinutes(30)),
            entry("subscriber_name", Duration.ofHours(24)),

            // Sent/learned data — per user, evicted on delivery
            entry("sent_word_ids", Duration.ofMinutes(30)),
            entry("sent_word_values", Duration.ofMinutes(30)),
            entry("grammar_sent_ids", Duration.ofHours(1)),
            entry("learned_quiz_words", Duration.ofMinutes(10)),

            // Stats — expensive aggregations
            entry("activity_stats", Duration.ofMinutes(5)),
            entry("today_activity", Duration.ofMinutes(5)),
            entry("activity_streak", Duration.ofHours(1)),
            entry("word_counts", Duration.ofMinutes(5)),
            entry("quiz_stats", Duration.ofHours(1)),
            entry("most_missed_words", Duration.ofMinutes(30)),

            // User bookmarks/reads
            entry("saved_word_ids", Duration.ofHours(1)),
            entry("saved_word_count", Duration.ofHours(1)),
            entry("read_story_ids", Duration.ofHours(2)),

            // Content — static, long TTL
            entry("dialog_list", Duration.ofHours(24)),
            entry("dialog_detail", Duration.ofHours(24)),
            entry("adventure_stories", Duration.ofHours(24)),
            entry("adventure_story_detail", Duration.ofHours(24)),
            entry("story_progress", Duration.ofHours(2)),

            // Word data
            entry("word_detail", Duration.ofHours(24)),
            entry("word_comments", Duration.ofMinutes(30)),
            entry("word_comment_count", Duration.ofMinutes(30))
        );

        return RedisCacheManager.builder(connectionFactory)
            .cacheDefaults(defaults)
            .withInitialCacheConfigurations(perCache)
            .build();
    }

    private static Map.Entry<String, RedisCacheConfiguration> entry(String name, Duration ttl) {
        var jsonSerializer = RedisSerializationContext.SerializationPair
            .fromSerializer(new GenericJackson2JsonRedisSerializer());
        return Map.entry(name, RedisCacheConfiguration.defaultCacheConfig()
            .serializeValuesWith(jsonSerializer)
            .prefixCacheNameWith("fcio:")
            .entryTtl(ttl));
    }

    @Bean
    @org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean(org.springframework.cache.CacheManager.class)
    public org.springframework.cache.concurrent.ConcurrentMapCacheManager fallbackCacheManager() {
        return new org.springframework.cache.concurrent.ConcurrentMapCacheManager();
    }
}
