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

/**
 * A stored item paired with an opaque version stamp, used to support optimistic concurrency
 * (compare-and-set) in a store agnostic manner.
 * <p>
 * The {@code version} is an opaque token whose only guarantee is that it changes whenever the
 * underlying item is mutated. Callers should treat it as opaque: read it via
 * {@link ConcurrentStore#getWithVersion(String)} and pass it back to
 * {@link ConcurrentStore#checkAndUpdate(Id, Object)} without interpreting or computing on it.
 * <p>
 * The version type {@code V} is left to the implementing store, so that each backend can use its
 * natural concurrency token: a numeric generation ({@code Long}) for Aerospike/HBase, or an opaque
 * {@code String} ETag for object stores such as S3 or CosmosDB.
 *
 * @param value   the stored item
 * @param version opaque version stamp of the item at the time it was read
 * @param <T>     type of item being stored, must implement the {@link Id} interface
 * @param <V>     type of the opaque version stamp
 * @since 1.3.0
 */
public record Versioned<T extends Id, V>(T value, V version) {

    public static <T extends Id, V> Versioned<T, V> of(final T value, final V version) {
        return new Versioned<>(value, version);
    }
}
