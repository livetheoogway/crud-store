package com.livetheoogway.crudstore.aerospike.data;

import com.livetheoogway.crudstore.core.Id;

import java.util.List;

public record IdWithRefs<T extends Id> (T item,List<String> refIds) {
    public static <T extends Id> IdWithRefs<T> of(T item, List<String> refIds) {
        return new IdWithRefs<>(item, refIds);
    }
    public static <T extends Id> IdWithRefs<T> of(T item) {
        return new IdWithRefs<>(item, null);
    }
}
