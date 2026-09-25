package itmo.infrastructure.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import itmo.task.dto.TaskResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.time.Duration;

@Configuration
@EnableCaching
public class TaskCacheConfiguration {

    public static final String TASKS_CACHE = "tasks";

    @Bean
    CacheManager cacheManager(
            RedisConnectionFactory connectionFactory,
            ObjectMapper objectMapper,
            @Value("${app.cache.tasks.ttl}") Duration taskCacheTtl
    ) {
        var valueSerializer = new Jackson2JsonRedisSerializer<>(objectMapper, TaskResponse.class);
        var taskCacheConfiguration = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(taskCacheTtl)
                .disableCachingNullValues()
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(valueSerializer)
                );

        return RedisCacheManager.builder(connectionFactory)
                .withCacheConfiguration(TASKS_CACHE, taskCacheConfiguration)
                .disableCreateOnMissingCache()
                .transactionAware()
                .build();
    }
}
