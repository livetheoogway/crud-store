package com.livetheoogway.crudstore.aerospike.util;

import com.livetheoogway.crudstore.aerospike.AerospikeStore;
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

    public <T extends Id> void testStoreOperations(final AerospikeStore<T> store,
                                                   final Supplier<T> initialData,
                                                   final Supplier<T> updatedData,
                                                   final Supplier<T> anotherData,
                                                   final Supplier<T> unknownData,
                                                   final BiFunction<T, Optional<T>, Boolean> validator) {

        /* put some data */
        final T data = initialData.get();
        store.create(data);

        /* get it back */
        final var result = store.get(data.id());
        assertTrue(validator.apply(data, result));

        /* get on unknown id */
        assertFalse(store.get("unknown").isPresent());

        /* update existing data */
        final var updated = updatedData.get();
        store.update(updated);
        final var updatedResult = store.get(updated.id());
        assertTrue(validator.apply(updated, updatedResult));

        /* update unknown id */
        assertThrows(RuntimeException.class, () -> store.update(unknownData.get()));

        /* create already existing id */
        assertThrows(RuntimeException.class, () -> store.create(data));

        /* get bulk */
        final T another = anotherData.get();
        store.create(another);
        final Map<String, T> resultMap = store.get(List.of(updated.id(), another.id()));
        assertTrue(resultMap.containsKey(updated.id()));
        assertTrue(resultMap.containsKey(another.id()));
        assertEquals(2, resultMap.size());

        /* list */
        final List<T> result2 = store.list();
        assertEquals(2, result2.size());
    }
}
