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
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class CachingStoreTest {

    @Test
    void storeOperations() throws InterruptedException {
        /* initialize */
        Store<TestData> store = new CachingStore<>(new InMemoryStore<>(), 5, 2, 1);

        /* put some data */
        store.create(new TestData("1", "me", 2));

        /* get it back */
        Optional<TestData> testData = store.get("1");
        assertTrue(testData.isPresent());
        assertEquals("1", testData.get().id());
        assertEquals("me", testData.get().name());
        assertEquals(2, testData.get().age());

        /* get on unknown id */
        assertFalse(store.get("unknown").isPresent());

        /* update existing data */
        store.update(new TestData("1", "me too", 5));
        testData = store.get("1");
        assertTrue(testData.isPresent());
        assertEquals("1", testData.get().id());
        assertEquals("me", testData.get().name());
        assertEquals(2, testData.get().age());

        /* update unknown id */
        assertThrows(RuntimeException.class, () -> store.update(new TestData("unknown", "me too", 5)));

        /* create already existing id */
        assertThrows(RuntimeException.class, () -> store.create(new TestData("1", "me too", 5)));

        /* get bulk */
        store.create(new TestData("2", "you", 5));
        final Map<String, TestData> result = store.get(List.of("1", "2"));
        assertTrue(result.containsKey("1"));
        assertTrue(result.containsKey("2"));
        assertEquals(2, result.size());

        /* list */
        final List<TestData> result2 = store.list();
        assertEquals(2, result2.size());

        /* after 1s, refresh after write should kick in */
        store.create(new TestData("3", "you", 5));
        testData = store.get("3");
        assertTrue(testData.isPresent());
        assertEquals("3", testData.get().id());
        assertEquals("you", testData.get().name());
        store.update(new TestData("3", "you too", 7));
        Thread.sleep(1500);
        testData = store.get("3");
        assertTrue(testData.isPresent());
        assertEquals("3", testData.get().id());
        assertEquals("you", testData.get().name());
        assertEquals(5, testData.get().age());
        testData = store.get("3");
        assertTrue(testData.isPresent());
        assertEquals("3", testData.get().id());
        assertEquals("you too", testData.get().name());
        assertEquals(7, testData.get().age());

        /* after 2s, refresh after write should kick in */
        store.create(new TestData("4", "four", 5));
        testData = store.get("4");
        assertTrue(testData.isPresent());
        assertEquals("4", testData.get().id());
        assertEquals("four", testData.get().name());
        store.update(new TestData("4", "four too", 7));
        Thread.sleep(2200);
        testData = store.get("4");
        assertTrue(testData.isPresent());
        assertEquals("4", testData.get().id());
        assertEquals("four too", testData.get().name());
        assertEquals(7, testData.get().age());
    }

}