package com.livetheoogway.crudstore.core;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import lombok.AllArgsConstructor;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A delegating wrapper on Store that caches data for some duration
 * It uses caffine underneath
 *
 * @param <T> type of Id
 */
@AllArgsConstructor
public class CachingStore<T extends Id> implements Store<T> {

    private final Store<T> store;
    private final LoadingCache<String, T> cache;

    public CachingStore(final Store<T> store) {
        this(store, 10_000, 30 * 60L, 60);
    }

    /**
     * @param store                      delegated store
     * @param maxCacheSize               max size of the cache
     * @param expireAfterWriteInSeconds  evict key entirely after these many seconds since creation
     * @param refreshAfterWriteInSeconds schedule for lazy refresh of data, after these many seconds
     */
    public CachingStore(final Store<T> store,
                        final int maxCacheSize,
                        final long expireAfterWriteInSeconds,
                        final long refreshAfterWriteInSeconds) {
        this.store = store;
        this.cache = Caffeine.newBuilder()
                .maximumSize(maxCacheSize)
                .expireAfterWrite(Duration.ofSeconds(expireAfterWriteInSeconds))
                .refreshAfterWrite(Duration.ofSeconds(refreshAfterWriteInSeconds))
                .build(this::retrieveFromStore);
    }

    @Override
    public void create(final T item) {
        store.create(item);
    }

    @Override
    public void update(final T item) {
        store.update(item);
    }

    @Override
    public void delete(final String id) {
        store.delete(id);
    }

    @Override
    public Optional<T> get(final String id) {
        return Optional.ofNullable(cache.get(id));
    }

    @Override
    public Map<String, T> get(final List<String> ids) {
        return cache.getAll(ids);
    }

    @Override
    public List<T> list() {
        return cache.asMap().values().stream().toList();
    }

    private T retrieveFromStore(final String key) {
        return store.get(key).orElse(null); // null values are not cached by caffine
    }
}
