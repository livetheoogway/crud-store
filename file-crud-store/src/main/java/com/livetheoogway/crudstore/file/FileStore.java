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
import com.livetheoogway.crudstore.core.Id;
import com.livetheoogway.crudstore.core.ReferenceExtendedStore;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * A simple file backed {@link ReferenceExtendedStore} implementation.
 * <p>
 * All items are serialized to JSON and persisted to a single backing file on disk. The entire dataset is held in
 * memory and flushed to disk on every mutation, making this implementation suitable for small datasets, local
 * development, tests and tooling, rather than high throughput production workloads.
 * <p>
 * The store is thread-safe; reads and writes are guarded by an internal lock so that the in-memory view and the
 * on-disk file never diverge. Writes are atomic: data is written to a temporary file and then moved into place.
 *
 * @param <T> type of item being stored, must implement the {@link Id} interface
 * @since 1.3.0
 */
@Slf4j
public class FileStore<T extends Id> implements ReferenceExtendedStore<T> {

    private final ObjectMapper mapper;
    private final Path dataFile;
    private final TypeReference<T> typeReference;
    private final Object lock = new Object();

    /**
     * @param mapper   jackson object mapper used for (de)serialization
     * @param dataFile path to the backing json file (created if absent)
     * @param clazz    type of item being stored
     */
    public FileStore(final ObjectMapper mapper, final Path dataFile, final Class<T> clazz) {
        this(mapper, dataFile, new TypeReference<>() {
            @Override
            public Type getType() {
                return clazz;
            }
        });
    }

    /**
     * @param mapper        jackson object mapper used for (de)serialization
     * @param dataFile      path to the backing json file (created if absent)
     * @param typeReference type reference of item being stored (use this for generic types)
     */
    public FileStore(final ObjectMapper mapper, final Path dataFile, final TypeReference<T> typeReference) {
        this.mapper = mapper;
        this.dataFile = dataFile;
        this.typeReference = typeReference;
        initializeBackingFile();
    }

    @Override
    public void create(final T item) {
        synchronized (lock) {
            final Persisted<T> persisted = read();
            if (persisted.data().containsKey(item.id())) {
                throw new IllegalArgumentException("item already created for id:" + item.id());
            }
            persisted.data().put(item.id(), item);
            write(persisted);
        }
    }

    @Override
    public void create(final T item, final List<String> refIds) {
        synchronized (lock) {
            final Persisted<T> persisted = read();
            if (persisted.data().containsKey(item.id())) {
                throw new IllegalArgumentException("item already created for id:" + item.id());
            }
            persisted.data().put(item.id(), item);
            if (refIds != null) {
                refIds.forEach(refId -> persisted.index()
                        .computeIfAbsent(refId, k -> new ArrayList<>())
                        .add(item.id()));
            }
            write(persisted);
        }
    }

    @Override
    public void update(final T item) {
        synchronized (lock) {
            final Persisted<T> persisted = read();
            if (!persisted.data().containsKey(item.id())) {
                throw new IllegalArgumentException("update cannot be done on unknown item id:" + item.id());
            }
            persisted.data().put(item.id(), item);
            write(persisted);
        }
    }

    @Override
    public void delete(final String id) {
        synchronized (lock) {
            final Persisted<T> persisted = read();
            if (!persisted.data().containsKey(id)) {
                throw new IllegalArgumentException("id doesn't exist, cannot be deleted id:" + id);
            }
            persisted.data().remove(id);
            persisted.index().values().forEach(ids -> ids.remove(id));
            write(persisted);
        }
    }

    @Override
    public Optional<T> get(final String id) {
        synchronized (lock) {
            return Optional.ofNullable(read().data().get(id));
        }
    }

    @Override
    public Map<String, T> get(final List<String> ids) {
        synchronized (lock) {
            final Persisted<T> persisted = read();
            return ids.stream()
                    .filter(persisted.data()::containsKey)
                    .collect(Collectors.toMap(Function.identity(), persisted.data()::get));
        }
    }

    @Override
    public List<T> list() {
        synchronized (lock) {
            return new ArrayList<>(read().data().values());
        }
    }

    @Override
    public List<T> getByRefId(final String refId) {
        synchronized (lock) {
            final Persisted<T> persisted = read();
            return persisted.index().getOrDefault(refId, List.of()).stream()
                    .map(persisted.data()::get)
                    .filter(Objects::nonNull)
                    .toList();
        }
    }

    private void initializeBackingFile() {
        try {
            if (dataFile.getParent() != null) {
                Files.createDirectories(dataFile.getParent());
            }
            if (!Files.exists(dataFile)) {
                write(new Persisted<>(new LinkedHashMap<>(), new LinkedHashMap<>()));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to initialize backing file:" + dataFile, e);
        }
    }

    private Persisted<T> read() {
        try {
            final String content = Files.readString(dataFile);
            if (content.isBlank()) {
                return new Persisted<>(new LinkedHashMap<>(), new LinkedHashMap<>());
            }
            final FileEnvelope envelope = mapper.readValue(content, FileEnvelope.class);
            final Map<String, T> data = new LinkedHashMap<>();
            for (final Map.Entry<String, Object> entry : envelope.data().entrySet()) {
                data.put(entry.getKey(), mapper.convertValue(entry.getValue(), typeReference));
            }
            final Map<String, List<String>> index = envelope.index() == null
                                                     ? new LinkedHashMap<>()
                                                     : new LinkedHashMap<>(envelope.index());
            return new Persisted<>(data, index);
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to read backing file:" + dataFile, e);
        }
    }

    private void write(final Persisted<T> persisted) {
        try {
            final FileEnvelope envelope = new FileEnvelope(
                    new LinkedHashMap<>(persisted.data()), new LinkedHashMap<>(persisted.index()));
            final String content = mapper.writeValueAsString(envelope);
            final Path tmp = Files.createTempFile(
                    dataFile.toAbsolutePath().getParent(), dataFile.getFileName().toString(), ".tmp");
            Files.writeString(tmp, content);
            Files.move(tmp, dataFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to write backing file:" + dataFile, e);
        }
    }

    /**
     * in-memory, typed view of the backing file
     */
    private record Persisted<T extends Id>(Map<String, T> data, Map<String, List<String>> index) {}

    /**
     * on-disk, loosely typed representation. The item values are kept as raw objects so that the concrete type can be
     * resolved against {@link #typeReference} on read, supporting generic stores.
     */
    private record FileEnvelope(Map<String, Object> data, Map<String, List<String>> index) {}
}
