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
import com.aerospike.client.IAerospikeClient;
import com.aerospike.client.Key;
import com.aerospike.client.policy.WritePolicy;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.livetheoogway.crudstore.aerospike.data.ProfileData;
import com.livetheoogway.crudstore.aerospike.data.UserData;
import com.livetheoogway.crudstore.aerospike.stores.TestTypeRefAerospikeStore;
import com.livetheoogway.crudstore.aerospike.stores.UserAerospikeReplaceStore;
import com.livetheoogway.crudstore.aerospike.stores.UserAerospikeStore;
import com.livetheoogway.crudstore.aerospike.util.DataUtils;
import com.livetheoogway.crudstore.aerospike.util.TestUtils;
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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AerospikeStoreTest {
    private static AerospikeContainer aerospikeContainer;
    private static AerospikeStore<UserData> store;
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
        store = new UserAerospikeStore(aerospikeClient,
                                       new NamespaceSet("test", "test"),
                                       new ObjectMapper(),
                                       new DefaultErrorHandler<>());
    }

    @AfterAll
    static void afterAll() {
        aerospikeContainer.stop();
    }

    private static void validateTestData(final Optional<UserData> testData, final UserData meToo) {
        assertTrue(testData.isPresent());
        assertEquals(meToo.id(), testData.get().id());
        assertEquals(meToo.name(), testData.get().name());
        assertEquals(meToo.age(), testData.get().age());
    }

    @Test
    void testStoreOperations() {

        final UserData me = DataUtils.generateTestData();
        TestUtils.testStoreOperations(store,
                                      () -> me,
                                      () -> DataUtils.generateTestData(me.id()),
                                      () -> DataUtils.generateTestData("2", "you", 5),
                                      () -> new UserData("unknown", "me too", 5),
                                      (data, result) -> {
                                          assertTrue(result.isPresent());
                                          assertEquals(data.id(), result.get().id());
                                          assertEquals(data.name(), result.get().name());
                                          assertEquals(data.age(), result.get().age());
                                          return true;
                                      });
    }

    @Test
    void testStoreOperationsForTypeRef() {

        final TestTypeRefAerospikeStore<ProfileData<UserData>> store
                = new TestTypeRefAerospikeStore<>(aerospikeClient,
                                                  new NamespaceSet("test", "test-2"),
                                                  new ObjectMapper(),
                                                  new TypeReference<>() {},
                                                  new DefaultErrorHandler<>());

        final ProfileData<UserData> me = DataUtils.generateProfileData();
        TestUtils.testStoreOperations(store,
                                      () -> me,
                                      () -> DataUtils.generateProfileData(me.id()),
                                      () -> DataUtils.generateProfileData("2"),
                                      () -> new ProfileData<>("unknown", new UserData("unknown", "me too", 5)),
                                      (data, result) -> {
                                          assertTrue(result.isPresent());
                                          assertEquals(data.id(), result.get().id());
                                          assertEquals(data.profile().name(), result.get().profile().name());
                                          assertEquals(data.profile().age(), result.get().profile().age());
                                          return true;
                                      });
    }


    @Test
    void testStoreOperationsOnReplace() {

        final UserAerospikeReplaceStore storeWithReplace
                = new UserAerospikeReplaceStore(aerospikeClient,
                                                new NamespaceSet("test", "test-3"),
                                                new ObjectMapper(),
                                                new DefaultErrorHandler<>());
        /* put some data */
        final var me = DataUtils.generateTestData("11");
        storeWithReplace.create(me);

        /* get it back */
        Optional<UserData> testData = storeWithReplace.get(me.id());
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
        Optional<UserData> testData = store.get(me.id());
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
        final ErrorHandler<UserData> errorHandler = mock(ErrorHandler.class);
        when(mapper.writeValueAsString(any())).thenThrow(JsonProcessingException.class);
        final UserAerospikeStore newStore
                = new UserAerospikeStore(aerospikeClient,
                                         new NamespaceSet("test", "json-error-1"),
                                         mapper, errorHandler);
        newStore.create(DataUtils.generateTestData());
        Mockito.verify(errorHandler, Mockito.times(1)).onSerializationError(any(), any());

    }

    @Test
    void testHandlerForJsonSerializationExceptionDuringGet() throws JsonProcessingException {
        final ObjectMapper validObjectMapper = new ObjectMapper();
        final ObjectMapper mapper = mock(ObjectMapper.class);
        final ErrorHandler<UserData> errorHandler = mock(ErrorHandler.class);
        when(mapper.readValue(anyString(), any(TypeReference.class))).thenThrow(JsonProcessingException.class);
        final var testData = DataUtils.generateTestData("31");
        when(mapper.writeValueAsString(any())).thenReturn(validObjectMapper.writeValueAsString(testData));
        final UserAerospikeStore newStore
                = new UserAerospikeStore(aerospikeClient,
                                         new NamespaceSet("test", "json-error-2"),
                                         mapper, errorHandler);
        newStore.create(testData);
        final Optional<UserData> result = newStore.get(testData.id());
        assertFalse(result.isPresent());
        Mockito.verify(errorHandler, Mockito.times(1)).onDeSerializationError(any(), any());

        final var anotherTestData = DataUtils.generateTestData("32");
        newStore.create(anotherTestData);
        final Map<String, UserData> result2 = newStore.get(List.of(testData.id(), anotherTestData.id()));
        assertTrue(result2.isEmpty());
        Mockito.verify(errorHandler, Mockito.times(3)).onDeSerializationError(any(), any());
    }

    @Test
    void testHandlerForNoRecordFoundExceptionDuringGet() {
        final ErrorHandler<UserData> errorHandler = mock(ErrorHandler.class);
        final UserAerospikeStore newStore
                = new UserAerospikeStore(aerospikeClient,
                                         new NamespaceSet("test", "no-record-error-2"),
                                         new ObjectMapper(), errorHandler);

        final var testData = DataUtils.generateTestData();
        final Optional<UserData> result = newStore.get(testData.id());
        assertFalse(result.isPresent());
        Mockito.verify(errorHandler, Mockito.times(1)).onNoRecordFound(any());

        final var anotherTestData = DataUtils.generateTestData("2");
        final Map<String, UserData> result2 = newStore.get(List.of(testData.id(), anotherTestData.id()));
        assertTrue(result2.isEmpty());
        Mockito.verify(errorHandler, Mockito.times(3)).onNoRecordFound(any());
    }

    @Test
    void testHandlerForAerospikeExceptionDuringPut() {
        final IAerospikeClient newASClient = mock(AerospikeClient.class);
        when(newASClient.getWritePolicyDefault()).thenReturn(new WritePolicy());
        doNothing().when(newASClient).put(any(WritePolicy.class), any(Key.class), any());
        doThrow(new AerospikeException(22, "test-error-on-put")).when(newASClient).put(any(WritePolicy.class),
                                                                                       any(Key.class), any());
        final ErrorHandler<UserData> errorHandler = mock(ErrorHandler.class);
        final UserAerospikeStore newStore
                = new UserAerospikeStore(newASClient,
                                         new NamespaceSet("test", "no-record-error-2"),
                                         new ObjectMapper(), errorHandler);

        final var testData = DataUtils.generateTestData("77");
        newStore.create(testData);
        Mockito.verify(errorHandler, Mockito.times(1)).onAerospikeError(any(), any());
    }
}