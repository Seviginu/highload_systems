package itmo.infrastructure.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

final class ResilientCache implements Cache {

    private static final Logger log = LoggerFactory.getLogger(ResilientCache.class);

    private final Cache delegate;

    ResilientCache(Cache delegate) {
        this.delegate = delegate;
    }

    @Override
    public String getName() {
        return delegate.getName();
    }

    @Override
    public Object getNativeCache() {
        return delegate.getNativeCache();
    }

    @Override
    public ValueWrapper get(Object key) {
        try {
            return delegate.get(key);
        } catch (RuntimeException exception) {
            logFailure("get", key, exception);
            return null;
        }
    }

    @Override
    public <T> T get(Object key, Class<T> type) {
        try {
            return delegate.get(key, type);
        } catch (RuntimeException exception) {
            logFailure("get", key, exception);
            return null;
        }
    }

    @Override
    public <T> T get(Object key, Callable<T> valueLoader) {
        try {
            return delegate.get(key, valueLoader);
        } catch (ValueRetrievalException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            logFailure("get", key, exception);
            try {
                return valueLoader.call();
            } catch (Exception loaderException) {
                throw new ValueRetrievalException(key, valueLoader, loaderException);
            }
        }
    }

    @Override
    public CompletableFuture<?> retrieve(Object key) {
        try {
            return delegate.retrieve(key).exceptionally(exception -> {
                logFailure("retrieve", key, exception);
                return null;
            });
        } catch (RuntimeException exception) {
            logFailure("retrieve", key, exception);
            return CompletableFuture.completedFuture(null);
        }
    }

    @Override
    public <T> CompletableFuture<T> retrieve(
            Object key,
            Supplier<CompletableFuture<T>> valueLoader
    ) {
        try {
            return delegate.retrieve(key, valueLoader)
                    .handle((value, exception) -> {
                        if (exception == null) {
                            return CompletableFuture.completedFuture(value);
                        }
                        logFailure("retrieve", key, exception);
                        return valueLoader.get();
                    })
                    .thenCompose(future -> future);
        } catch (RuntimeException exception) {
            logFailure("retrieve", key, exception);
            return valueLoader.get();
        }
    }

    @Override
    public void put(Object key, Object value) {
        try {
            delegate.put(key, value);
        } catch (RuntimeException exception) {
            logFailure("put", key, exception);
        }
    }

    @Override
    public ValueWrapper putIfAbsent(Object key, Object value) {
        try {
            return delegate.putIfAbsent(key, value);
        } catch (RuntimeException exception) {
            logFailure("putIfAbsent", key, exception);
            return null;
        }
    }

    @Override
    public void evict(Object key) {
        try {
            delegate.evict(key);
        } catch (RuntimeException exception) {
            logFailure("evict", key, exception);
        }
    }

    @Override
    public boolean evictIfPresent(Object key) {
        try {
            return delegate.evictIfPresent(key);
        } catch (RuntimeException exception) {
            logFailure("evict", key, exception);
            return false;
        }
    }

    @Override
    public void clear() {
        try {
            delegate.clear();
        } catch (RuntimeException exception) {
            logFailure("clear", null, exception);
        }
    }

    @Override
    public boolean invalidate() {
        try {
            return delegate.invalidate();
        } catch (RuntimeException exception) {
            logFailure("clear", null, exception);
            return false;
        }
    }

    private void logFailure(String operation, Object key, Throwable exception) {
        log.warn(
                "Cache operation '{}' failed for cache '{}' and key '{}'; continuing without cache: {}",
                operation,
                getName(),
                key,
                exception.toString()
        );
    }
}
