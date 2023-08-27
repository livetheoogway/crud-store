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
import com.aerospike.client.AerospikeException;
import com.aerospike.client.Key;
import com.aerospike.client.policy.Policy;
import com.aerospike.client.policy.WritePolicy;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.appform.testcontainers.aerospike.AerospikeContainerConfiguration;
import io.appform.testcontainers.aerospike.container.AerospikeContainer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AerospikeStoreTest {
    private static AerospikeContainer aerospikeContainer;
    private static AerospikeStore<TestData> store;
    private static AerospikeClient aerospikeClient;

    @BeforeAll
    static void beforeAll() {
        AerospikeContainerConfiguration config = new AerospikeContainerConfiguration();
        config.setNamespace("test");
        config.setPort(4000);
        config.setDockerImage("aerospike/aerospike-server-enterprise:latest");
        config.setWaitTimeoutInSeconds(300);
        aerospikeContainer = new AerospikeContainer(config);
        aerospikeContainer.start();
        aerospikeClient = ContainerHelper.provideAerospikeClient(aerospikeContainer, config);
        store = new TestAerospikeStore(aerospikeClient,
                                       new NamespaceSet("test", "test"),
                                       new ObjectMapper(),
                                       new DefaultErrorHandler<>());
    }

    @AfterAll
    static void afterAll() {
        aerospikeContainer.stop();
    }

    private static void validateTestData(final Optional<TestData> testData, final TestData meToo) {
        assertTrue(testData.isPresent());
        assertEquals(meToo.id(), testData.get().id());
        assertEquals(meToo.name(), testData.get().name());
        assertEquals(meToo.age(), testData.get().age());
    }

    @Test
    void testStoreOperations() {

        /* put some data */
        final var me = DataUtils.generateTestData();
        store.create(me);

        /* get it back */
        Optional<TestData> testData = store.get(me.id());
        validateTestData(testData, me);

        /* get on unknown id */
        assertFalse(store.get("unknown").isPresent());

        /* update existing data */
        final var meToo = DataUtils.generateTestData(me.id());
        store.update(meToo);
        testData = store.get(meToo.id());
        validateTestData(testData, meToo);

        /* update unknown id */
        assertThrows(RuntimeException.class, () -> store.update(new TestData("unknown", "me too", 5)));

        /* create already existing id */
        assertThrows(RuntimeException.class, () -> store.create(new TestData("1", "me too", 5)));

        /* get bulk */
        final TestData you = DataUtils.generateTestData("2", "you", 5);
        store.create(you);
        final Map<String, TestData> result = store.get(List.of(meToo.id(), you.id()));
        assertTrue(result.containsKey(meToo.id()));
        assertTrue(result.containsKey(you.id()));
        assertEquals(2, result.size());

        /* list */
        final List<TestData> result2 = store.list();
        assertEquals(2, result2.size());

    }

    @Test
    void testStoreOperationsOnReplace() {

        final TestAerospikeStoreReplace storeWithReplace
                = new TestAerospikeStoreReplace(aerospikeClient,
                                                new NamespaceSet("test", "test-2"),
                                                new ObjectMapper(),
                                                new DefaultErrorHandler<>());
        /* put some data */
        final var me = DataUtils.generateTestData("11");
        storeWithReplace.create(me);

        /* get it back */
        Optional<TestData> testData = storeWithReplace.get(me.id());
        validateTestData(testData, me);

        /* get on unknown id */
        assertFalse(storeWithReplace.get("unknown").isPresent());

        /* create same data */
        final var meAgain = DataUtils.generateTestData("11");
        storeWithReplace.create(meAgain);
        testData = storeWithReplace.get(meAgain.id());
        validateTestData(testData, meAgain);
    }

    @Test
    void testStoreDeleteExistingKeySuccessful() {

        /* put some data */
        final var me = DataUtils.generateTestData("12");
        store.create(me);

        /* get it back */
        Optional<TestData> testData = store.get(me.id());
        validateTestData(testData, me);

        /* delete it */
        store.delete(me.id());

        /* get it back */
        assertFalse(store.get(me.id()).isPresent());

        /* delete unknown key */
        assertThrows(RuntimeException.class, () -> store.delete("unknown"));
    }

    @Test
    void testHandlerForJsonSerializationExceptionDuringCreate() throws JsonProcessingException {
        final ObjectMapper mapper = mock(ObjectMapper.class);
        final ErrorHandler<TestData> errorHandler = mock(ErrorHandler.class);
        when(mapper.writeValueAsString(any())).thenThrow(JsonProcessingException.class);
        final TestAerospikeStore newStore
                = new TestAerospikeStore(aerospikeClient,
                                         new NamespaceSet("test", "json-error-1"),
                                         mapper, errorHandler);
        newStore.create(DataUtils.generateTestData());
        Mockito.verify(errorHandler, Mockito.times(1)).onSerializationError(any(), any());

    }

    @Test
    void testHandlerForJsonSerializationExceptionDuringGet() throws JsonProcessingException {
        final ObjectMapper validObjectMapper = new ObjectMapper();
        final ObjectMapper mapper = mock(ObjectMapper.class);
        final ErrorHandler<TestData> errorHandler = mock(ErrorHandler.class);
        when(mapper.readValue(anyString(), any(TypeReference.class))).thenThrow(JsonProcessingException.class);
        final var testData = DataUtils.generateTestData("31");
        when(mapper.writeValueAsString(any())).thenReturn(validObjectMapper.writeValueAsString(testData));
        final TestAerospikeStore newStore
                = new TestAerospikeStore(aerospikeClient,
                                         new NamespaceSet("test", "json-error-2"),
                                         mapper, errorHandler);
        newStore.create(testData);
        final Optional<TestData> result = newStore.get(testData.id());
        assertFalse(result.isPresent());
        Mockito.verify(errorHandler, Mockito.times(1)).onDeSerializationError(any(), any());

        final var anotherTestData = DataUtils.generateTestData("32");
        newStore.create(anotherTestData);
        final Map<String, TestData> result2 = newStore.get(List.of(testData.id(), anotherTestData.id()));
        assertTrue(result2.isEmpty());
        Mockito.verify(errorHandler, Mockito.times(3)).onDeSerializationError(any(), any());
    }

    @Test
    void testHandlerForNoRecordFoundExceptionDuringGet() {
        final ErrorHandler<TestData> errorHandler = mock(ErrorHandler.class);
        final TestAerospikeStore newStore
                = new TestAerospikeStore(aerospikeClient,
                                         new NamespaceSet("test", "no-record-error-2"),
                                         new ObjectMapper(), errorHandler);

        final var testData = DataUtils.generateTestData();
        final Optional<TestData> result = newStore.get(testData.id());
        assertFalse(result.isPresent());
        Mockito.verify(errorHandler, Mockito.times(1)).onNoRecordFound(any());

        final var anotherTestData = DataUtils.generateTestData("2");
        final Map<String, TestData> result2 = newStore.get(List.of(testData.id(), anotherTestData.id()));
        assertTrue(result2.isEmpty());
        Mockito.verify(errorHandler, Mockito.times(3)).onNoRecordFound(any());
    }

    @Test
    void testHandlerForAerospikeExceptionDuringGet() {
        final AerospikeClient newASClient = mock(AerospikeClient.class);
        when(newASClient.get(any(Policy.class), any(Key.class))).thenThrow(AerospikeException.class);
        when(newASClient.getWritePolicyDefault()).thenReturn(aerospikeClient.getWritePolicyDefault());
        doNothing().when(newASClient).put(any(WritePolicy.class), any(Key.class), any());
        final ErrorHandler<TestData> errorHandler = mock(ErrorHandler.class);
        final TestAerospikeStore newStore
                = new TestAerospikeStore(newASClient,
                                         new NamespaceSet("test", "no-record-error-2"),
                                         new ObjectMapper(), errorHandler);

        final var testData = DataUtils.generateTestData();
        newStore.create(testData);
        final Optional<TestData> result = newStore.get(testData.id());
        assertFalse(result.isPresent());
        Mockito.verify(errorHandler, Mockito.times(1)).onAerospikeError(any(), any());
    }
}