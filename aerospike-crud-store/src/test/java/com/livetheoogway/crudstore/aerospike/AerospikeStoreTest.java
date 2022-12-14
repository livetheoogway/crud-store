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

package com.livetheoogway.crudstore.aerospike;

import com.aerospike.client.AerospikeClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.appform.testcontainers.aerospike.AerospikeContainerConfiguration;
import io.appform.testcontainers.aerospike.container.AerospikeContainer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AerospikeStoreTest {
    private static AerospikeContainer aerospikeContainer;
    private static AerospikeStore<TestData> store;
    private static AerospikeClient aerospikeClient;

    @BeforeAll
    static void beforeAll() {
        AerospikeContainerConfiguration config = new AerospikeContainerConfiguration();
        config.setNamespace("test");
        config.setDockerImage("aerospike/aerospike-server:6.1.0.3");
        config.setWaitTimeoutInSeconds(300);
        aerospikeContainer = new AerospikeContainer(config);
        aerospikeContainer.start();
        aerospikeClient = ContainerHelper.provideAerospikeClient(aerospikeContainer, config);
        store = new TestAerospikeStore(aerospikeClient,
                                       new NamespaceSet("test", "test"),
                                       new ObjectMapper(),
                                       TestData.class,
                                       new DefaultErrorHandler<>());
    }

    @AfterAll
    static void afterAll() {
        aerospikeContainer.stop();
    }

    @Test
    void testStoreOperations() {

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
        assertEquals("me too", testData.get().name());
        assertEquals(5, testData.get().age());

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

    }

    @Test
    void testStoreOperationsOnReplace() {

        final TestAerospikeStoreReplace storeWithReplace
                = new TestAerospikeStoreReplace(aerospikeClient,
                                                new NamespaceSet("test", "test"),
                                                new ObjectMapper(),
                                                TestData.class,
                                                new DefaultErrorHandler<>());
        /* put some data */
        storeWithReplace.create(new TestData("1", "me", 2));

        /* get it back */
        Optional<TestData> testData = storeWithReplace.get("1");
        assertTrue(testData.isPresent());
        assertEquals("1", testData.get().id());
        assertEquals("me", testData.get().name());
        assertEquals(2, testData.get().age());

        /* get on unknown id */
        assertFalse(storeWithReplace.get("unknown").isPresent());

        /* update existing data */
        storeWithReplace.create(new TestData("1", "me too", 5));
        testData = storeWithReplace.get("1");
        assertTrue(testData.isPresent());
        assertEquals("1", testData.get().id());
        assertEquals("me too", testData.get().name());
        assertEquals(5, testData.get().age());
    }
}