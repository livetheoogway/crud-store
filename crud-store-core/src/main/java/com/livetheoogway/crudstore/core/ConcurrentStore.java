/*
 * Copyright 2026. Live the Oogway, Tushar Naik
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

import java.util.Optional;

/**
 * A specialized {@link Store} that supports atomic optimistic locking (compare-and-set) operations.
 * <p>
 * This is deliberately kept separate from the base {@link Store} interface (interface segregation): only stores that
 * can honour an atomic check-and-set expose these operations, so callers handed a plain {@link Store} never risk a
 * runtime {@code UnsupportedOperationException}.
 * <p>
 * The version is modelled as an opaque token of type {@code V} whose only guarantee is that it changes whenever the
 * underlying item is mutated. Leaving the type to the implementation lets each backend use its natural concurrency
 * token: a numeric generation ({@code Long}) for Aerospike/HBase, or an opaque {@code String} ETag for object stores
 * such as S3 or CosmosDB. Callers should treat it as opaque, reading it via {@link #getWithVersion(String)} and passing
 * it back to {@link #checkAndUpdate(Id, Object)} without interpreting it.
 *
 * @param <T> type of item being stored, must implement the {@link Id} interface
 * @param <V> type of the opaque version stamp
 * @since 1.3.0
 */
public interface ConcurrentStore<T extends Id, V> extends Store<T> {

    /**
     * Fetch an existing item along with its concurrency version stamp.
     * <p>
     * The returned {@link Versioned#version()} can be passed to {@link #checkAndUpdate(Id, Object)} to perform an
     * optimistic, compare-and-set style update.
     *
     * @param id id of the item to be fetched
     * @return the item and its version if found, otherwise empty
     */
    Optional<Versioned<T, V>> getWithVersion(final String id);

    /**
     * Atomically update an existing item only if its current version matches {@code expectedVersion}
     * (compare-and-set). The {@code expectedVersion} is typically obtained from a prior
     * {@link #getWithVersion(String)} call.
     *
     * @param item            the new value to store (its {@link Id#id()} identifies the record)
     * @param expectedVersion the version the caller expects the stored item to currently have
     * @throws ConcurrentUpdateException if the item is absent or its version has changed concurrently
     */
    void checkAndUpdate(final T item, final V expectedVersion);
}
