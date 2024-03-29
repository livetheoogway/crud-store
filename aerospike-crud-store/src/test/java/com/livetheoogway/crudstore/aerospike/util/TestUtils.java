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

package com.livetheoogway.crudstore.aerospike.util;

import com.livetheoogway.crudstore.aerospike.AerospikeStore;
import com.livetheoogway.crudstore.aerospike.data.IdWithRefs;
import com.livetheoogway.crudstore.core.Id;
import lombok.experimental.UtilityClass;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@UtilityClass
public class TestUtils {

    /**
     * Test basic store operations
     *
     * @param store       store to test
     * @param initialData initial data
     * @param updatedData data after update
     * @param anotherData another data to test bulk get
     * @param unknownData unknown data to test update fails when create wasnt done
     * @param validator   validator to check if data from store is correct
     * @param <T>         type of data
     */
    public <T extends Id> void assertStoreOperations(final AerospikeStore<T> store,
                                                     final Supplier<IdWithRefs<T>> initialData,
                                                     final Supplier<IdWithRefs<T>> updatedData,
                                                     final Supplier<IdWithRefs<T>> anotherData,
                                                     final Supplier<IdWithRefs<T>> unknownData,
                                                     final BiFunction<T, Optional<T>, Boolean> validator) {

        /* put some data */
        final T data = initialData.get().item();
        store.create(data, initialData.get().refIds());

        /* get it back */
        final var result = store.get(data.id());
        assertTrue(validator.apply(data, result));

        /* get on unknown id */
        assertFalse(store.get("unknown").isPresent());

        /* update existing data */
        final var updated = updatedData.get();
        store.update(updated.item());
        final var updatedResult = store.get(updated.item().id());
        assertTrue(validator.apply(updated.item(), updatedResult));

        /* update unknown id */
        assertThrows(RuntimeException.class, () -> store.update(unknownData.get().item()));

        /* create already existing id */
        assertThrows(RuntimeException.class, () -> store.create(data));

        /* get bulk */
        final T another = anotherData.get().item();
        store.create(another, anotherData.get().refIds());
        final Map<String, T> resultMap = store.get(List.of(updated.item().id(), another.id()));
        assertTrue(resultMap.containsKey(updated.item().id()));
        assertTrue(resultMap.containsKey(another.id()));
        assertEquals(2, resultMap.size());

        /* list */
        final List<T> result2 = store.list();
        assertEquals(2, result2.size());
    }
}
