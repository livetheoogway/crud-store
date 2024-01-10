/*
 * Copyright 2022. Live the Oogway, Tushar Naik
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is
 * distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and limitations
 * under the License.
 */

package com.livetheoogway.crudstore.core;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;

import java.time.Duration;
import java.util.List;

/**
 * An extension to the caching store that supports reference based queries
 */
public class RefCachingStore<T extends Id> extends CachingStore<T> implements ReferenceExtendedStore<T> {
    private final ReferenceExtendedStore<T> refStore;
    private final LoadingCache<String, List<String>> index;

    public RefCachingStore(final ReferenceExtendedStore<T> store) {
        this(store, 10_000, 30 * 60L, 60);
    }

    /**
     * @param store                      delegated store
     * @param maxCacheSize               max size of the cache
     * @param expireAfterWriteInSeconds  evict key entirely after these many seconds since creation
     * @param refreshAfterWriteInSeconds schedule for lazy refresh of data, after these many seconds
     */
    public RefCachingStore(final ReferenceExtendedStore<T> store,
                           final int maxCacheSize,
                           final long expireAfterWriteInSeconds,
                           final long refreshAfterWriteInSeconds) {
        super(store, maxCacheSize, expireAfterWriteInSeconds, refreshAfterWriteInSeconds);
        this.index = Caffeine.newBuilder()
                .maximumSize(maxCacheSize)
                .expireAfterWrite(Duration.ofSeconds(expireAfterWriteInSeconds))
                .refreshAfterWrite(Duration.ofSeconds(refreshAfterWriteInSeconds))
                .build(this::retrieveFromRefStore);
        this.refStore = store;
    }

    @Override
    public void create(final T item, final List<String> refIds) {
        refStore.create(item, refIds);
    }

    @Override
    public List<T> getByRefId(final String refId) {
        return get(index.get(refId)).values().stream().toList();
    }

    private List<String> retrieveFromRefStore(final String key) {
        return refStore.getByRefId(key).stream().map(Id::id).toList();
    }
}
