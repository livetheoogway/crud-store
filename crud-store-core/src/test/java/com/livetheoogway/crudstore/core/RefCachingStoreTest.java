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

class RefCachingStoreTest {

    @Test
    void storeOperations() {
        /* initialize */
        ReferenceExtendedStore<TestData> store = new RefCachingStore<>(new InMemoryStore<>(), 5, 2, 1);

        /* put some data */
        final TestData expected = new TestData("1", "me", 2);
        store.create(expected, List.of("ref1", "ref2"));

        /* get it back */
        assertIfValid(expected, store.get("1"));
        var byRefId = store.getByRefId("ref1");
        assertEquals(1, byRefId.size());
        assertIfValid(expected, byRefId.stream().findFirst());

        /* get on unknown id */
        assertFalse(store.get("unknown").isPresent());

        /* update existing data */
        final TestData updated = new TestData("1", "me too", 5);
        store.update(updated);
        assertIfValid(expected, store.get("1")); // as its a cache, it will return old value
        byRefId = store.getByRefId("ref1");
        assertEquals(1, byRefId.size());
        assertIfValid(expected, byRefId.stream().findFirst());

        /* update unknown id */
        assertThrows(RuntimeException.class, () -> store.update(new TestData("unknown", "me too", 5)));

        /* error on create already existing id */
        assertThrows(RuntimeException.class, () -> store.create(updated));

        /* get bulk */
        store.create(new TestData("2", "you", 5), List.of("ref1", "ref3"));
        final Map<String, TestData> result = store.get(List.of("1", "2"));
        assertTrue(result.containsKey("1"));
        assertTrue(result.containsKey("2"));
        assertEquals(2, result.size());

        final List<TestData> byRefId1 = store.getByRefId("ref1");
        assertEquals(1, byRefId1.size()); // again due to cache, it will return only 1 ref, until expiry
        final List<TestData> byRefId2 = store.getByRefId("ref2");
        assertEquals(1, byRefId2.size());

        /* list */
        final List<TestData> result2 = store.list();
        assertEquals(2, result2.size());
    }

    @Test
    void testExpireAndRefreshAfterWrite() {
        /* initialize */
        ReferenceExtendedStore<TestData> store = new RefCachingStore<>(new InMemoryStore<>(), 5, 2, 1);

        /* after 1s, refresh after write should kick in */
        final var expected = new TestData("3", "you", 5);
        store.create(expected, List.of("ref1", "ref2"));
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

        final var updatedAgain = new TestData("3", "you three", 7);
        store.update(updatedAgain);
        Awaitility
                .await()
                .atMost(2, TimeUnit.SECONDS)
                .pollDelay(100, TimeUnit.MILLISECONDS)
                .until(() -> "you too".equals(store.getByRefId("ref1").get(0).name()));
        Awaitility
                .await()
                .atMost(5, TimeUnit.SECONDS)
                .pollDelay(100, TimeUnit.MILLISECONDS)
                .until(() -> "you three".equals(store.getByRefId("ref1").get(0).name()));

        /* check update of reference index */
        final var moreCreated = new TestData("4", "more you", 15);
        store.create(moreCreated, List.of("ref1", "ref3"));
        assertIfValid(moreCreated, store.get("4"));

        Awaitility
                .await()
                .atMost(2, TimeUnit.SECONDS)
                .pollDelay(100, TimeUnit.MILLISECONDS)
                .until(() -> "you three".equals(store.getByRefId("ref1").get(0).name()));

        /* now after refresh, if should have the moreCreated value as well */
        Awaitility
                .await()
                .atMost(5, TimeUnit.SECONDS)
                .pollDelay(100, TimeUnit.MILLISECONDS)
                .until(() -> store.getByRefId("ref1").stream().anyMatch(k -> "more you".equals(k.name())));

    }
}