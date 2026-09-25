package itmo.infrastructure.cache;

import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;

import java.util.concurrent.Callable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ResilientCacheTest {

    @Test
    void shouldUseLoaderWhenCacheReadFails() {
        Cache delegate = failingCache();
        ResilientCache cache = new ResilientCache(delegate);

        assertThat(cache.get(1L, () -> "from-database")).isEqualTo("from-database");
    }

    @Test
    void shouldIgnoreCacheWriteAndEvictionFailures() {
        Cache delegate = failingCache();
        ResilientCache cache = new ResilientCache(delegate);

        assertThatCode(() -> cache.put(1L, "value")).doesNotThrowAnyException();
        assertThatCode(() -> cache.evict(1L)).doesNotThrowAnyException();
        assertThatCode(cache::clear).doesNotThrowAnyException();
    }

    private Cache failingCache() {
        Cache cache = mock(Cache.class);
        when(cache.getName()).thenReturn("tasks");
        when(cache.get(org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.<Callable<String>>any()))
                .thenThrow(new IllegalStateException("Redis unavailable"));
        org.mockito.Mockito.doThrow(new IllegalStateException("Redis unavailable"))
                .when(cache).put(1L, "value");
        org.mockito.Mockito.doThrow(new IllegalStateException("Redis unavailable"))
                .when(cache).evict(1L);
        org.mockito.Mockito.doThrow(new IllegalStateException("Redis unavailable"))
                .when(cache).clear();
        return cache;
    }
}
