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

package com.livetheoogway.crudstore.file;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.livetheoogway.crudstore.core.ReferenceExtendedStore;
import com.livetheoogway.crudstore.core.Store;
import com.livetheoogway.crudstore.file.data.ProfileData;
import com.livetheoogway.crudstore.file.data.UserData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileStoreTest {

    @TempDir
    Path tempDir;

    private Path dataFile;
    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper();
        dataFile = tempDir.resolve("store.json");
    }

    private FileStore<UserData> newStore() {
        return new FileStore<>(mapper, dataFile, UserData.class);
    }

    @Test
    void storeOperations() {
        final Store<UserData> store = newStore();

        /* put some data */
        store.create(new UserData("1", "me", 2));

        /* get it back */
        Optional<UserData> data = store.get("1");
        assertTrue(data.isPresent());
        assertEquals("1", data.get().id());
        assertEquals("me", data.get().name());
        assertEquals(2, data.get().age());

        /* get on unknown id */
        assertFalse(store.get("unknown").isPresent());

        /* update existing data */
        store.update(new UserData("1", "me too", 5));
        data = store.get("1");
        assertTrue(data.isPresent());
        assertEquals("me too", data.get().name());
        assertEquals(5, data.get().age());

        /* update unknown id */
        assertThrows(RuntimeException.class, () -> store.update(new UserData("unknown", "me too", 5)));

        /* create already existing id */
        assertThrows(RuntimeException.class, () -> store.create(new UserData("1", "me too", 5)));

        /* get bulk */
        store.create(new UserData("2", "you", 5));
        final Map<String, UserData> result = store.get(List.of("1", "2", "unknown"));
        assertTrue(result.containsKey("1"));
        assertTrue(result.containsKey("2"));
        assertEquals(2, result.size());

        /* list */
        assertEquals(2, store.list().size());

        /* delete */
        store.delete("1");
        assertFalse(store.get("1").isPresent());
        assertEquals(1, store.list().size());

        /* delete unknown */
        assertThrows(RuntimeException.class, () -> store.delete("unknown"));
    }

    @Test
    void dataIsPersistedAcrossInstances() {
        final FileStore<UserData> store = newStore();
        store.create(new UserData("1", "me", 2));
        store.create(new UserData("2", "you", 5));

        /* a brand new store instance pointed at the same file should see the data */
        final FileStore<UserData> reopened = newStore();
        assertEquals(2, reopened.list().size());
        final Optional<UserData> data = reopened.get("1");
        assertTrue(data.isPresent());
        assertEquals("me", data.get().name());
    }

    @Test
    void referenceOperations() {
        final ReferenceExtendedStore<UserData> store = newStore();

        store.create(new UserData("EMP001", "Tushar", 30), List.of("Smart", "Handsome"));
        store.create(new UserData("EMP002", "Pickle Rick", 70), List.of("Cucumber", "Ugly"));
        store.create(new UserData("EMP003", "Morty", 14), List.of("Stupid", "Smart"));

        List<UserData> smart = store.getByRefId("Smart");
        assertEquals(2, smart.size());
        assertTrue(smart.stream().anyMatch(u -> u.id().equals("EMP001")));
        assertTrue(smart.stream().anyMatch(u -> u.id().equals("EMP003")));

        assertEquals(1, store.getByRefId("Cucumber").size());
        assertTrue(store.getByRefId("unknown").isEmpty());

        /* references survive reopening */
        final ReferenceExtendedStore<UserData> reopened = newStore();
        assertEquals(2, reopened.getByRefId("Smart").size());

        /* deleting an item removes it from reference lookups */
        store.delete("EMP001");
        assertEquals(1, store.getByRefId("Smart").size());
    }

    @Test
    void supportsGenericTypeReference() {
        final Path genericFile = tempDir.resolve("generic.json");
        final FileStore<ProfileData<UserData>> store =
                new FileStore<>(mapper, genericFile, new TypeReference<>() {});

        store.create(new ProfileData<>("p1", new UserData("u1", "Rick", 70)));

        final Optional<ProfileData<UserData>> result = store.get("p1");
        assertTrue(result.isPresent());
        assertEquals("p1", result.get().id());
        assertEquals("u1", result.get().profile().id());
        assertEquals("Rick", result.get().profile().name());
        assertEquals(70, result.get().profile().age());
    }
}
