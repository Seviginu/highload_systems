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
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.time.Duration;
import java.util.Map;

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

        var cacheManager = new ResilientRedisCacheManager(
                RedisCacheWriter.nonLockingRedisCacheWriter(connectionFactory),
                taskCacheConfiguration,
                Map.of(TASKS_CACHE, taskCacheConfiguration)
        );
        cacheManager.setTransactionAware(true);
        return cacheManager;
    }

    private static final class ResilientRedisCacheManager extends RedisCacheManager {

        private ResilientRedisCacheManager(
                RedisCacheWriter cacheWriter,
                RedisCacheConfiguration defaultConfiguration,
                Map<String, RedisCacheConfiguration> initialConfigurations
        ) {
            super(cacheWriter, defaultConfiguration, false, initialConfigurations);
        }

        @Override
        protected org.springframework.cache.Cache decorateCache(org.springframework.cache.Cache cache) {
            return super.decorateCache(new ResilientCache(cache));
        }
    }
}
