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

import com.livetheoogway.crudstore.core.impl.InMemoryStore;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.livetheoogway.crudstore.core.TestUtils.assertIfValid;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CachingStoreTest {

    @Test
    void storeOperations() {
        /* initialize */
        Store<TestData> store = new CachingStore<>(new InMemoryStore<>(), 5, 2, 1);

        /* put some data */
        final var expected = new TestData("1", "me", 2);
        store.create(expected);

        /* get it back */
        assertIfValid(expected, store.get("1"));

        /* get on unknown id */
        assertFalse(store.get("unknown").isPresent());

        /* update existing data */
        final var updated = new TestData("1", "me too", 5);
        store.update(updated);
        assertIfValid(updated, store.get("1"));

        /* update unknown id */
        assertThrows(RuntimeException.class, () -> store.update(new TestData("unknown", "me too", 5)));

        /* create already existing id */
        assertThrows(RuntimeException.class, () -> store.create(updated));

        /* get bulk */
        store.create(new TestData("2", "you", 5));
        final Map<String, TestData> result = store.get(List.of("1", "2"));
        assertTrue(result.containsKey("1"));
        assertTrue(result.containsKey("2"));
        assertEquals(2, result.size());

        /* list */
        final List<TestData> result2 = store.list();
        assertEquals(2, result2.size());
    }

    @Test
    void testExpireAndRefreshAfterWrite() {
        /* initialize */
        Store<TestData> store = new CachingStore<>(new InMemoryStore<>(), 5, 2, 1);

        /* after 1s, refresh after write should kick in */
        final var expected = new TestData("3", "you", 5);
        store.create(expected);
        assertIfValid(expected, store.get("3"));

        final var updated = new TestData("3", "you too", 7);
        store.update(updated);
        Awaitility
                .await()
                .atMost(2, TimeUnit.SECONDS)
                .pollDelay(100, TimeUnit.MILLISECONDS)
                .until(() -> "you".equals(store.get("3").get().name()));
        Awaitility
                .await()
                .atMost(5, TimeUnit.SECONDS)
                .pollDelay(100, TimeUnit.MILLISECONDS)
                .until(() -> "you too".equals(store.get("3").get().name()));

        /* after 2s, refresh after write should kick in */
        final var four = new TestData("4", "four", 5);
        store.create(four);
        assertIfValid(four, store.get("4"));
        final var fourToo = new TestData("4", "four too", 7);
        store.update(fourToo);
        Awaitility
                .await()
                .atMost(5, TimeUnit.SECONDS)
                .pollDelay(2000, TimeUnit.MILLISECONDS)
                .pollInterval(100, TimeUnit.MILLISECONDS)
                .until(() -> "four too".equals(store.get("4").get().name()));
        assertIfValid(fourToo, store.get("4"));
    }
}