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

package com.livetheoogway.crudstore.core.impl;

import com.livetheoogway.crudstore.core.Id;
import com.livetheoogway.crudstore.core.ReferenceExtendedStore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

public class InMemoryStore<T extends Id> implements ReferenceExtendedStore<T> {
    private final Map<String, T> store;
    private final Map<String, List<String>> index;

    public InMemoryStore() {
        this(new HashMap<>(), new HashMap<>());
    }

    public InMemoryStore(final Map<String, T> store, Map<String, List<String>> index) {
        this.store = store;
        this.index = index;
    }

    @Override
    public void create(final T item) {
        if(store.containsKey(item.id())) {
            throw new RuntimeException("item already created");
        }
        store.put(item.id(), item);
    }

    @Override
    public void update(final T item) {
        if(!store.containsKey(item.id())) {
            throw new RuntimeException("update cannot be done on unknown item");
        }
        store.put(item.id(), item);
    }

    @Override
    public void delete(final String id) {
        if(!store.containsKey(id)) {
            throw new RuntimeException("id doesn't exist cannot be deleted");
        }
        store.remove(id);
    }

    @Override
    public Optional<T> get(final String id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public Map<String, T> get(final List<String> ids) {
        return ids.stream()
                .collect(Collectors.toMap(Function.identity(), store::get));
    }

    @Override
    public List<T> list() {
        return store.values().stream().toList();
    }

    @Override
    public void create(final T item, final List<String> refIds) {
        create(item);
        refIds.forEach(refId -> {
            if(!index.containsKey(refId)) {
                index.put(refId, new ArrayList<>());
            }
            index.get(refId).add(item.id());
        });
    }

    @Override
    public List<T> getByRefId(final String refId) {
        return index.getOrDefault(refId, List.of()).stream()
                .map(store::get)
                .toList();
    }
}
