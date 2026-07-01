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
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * A simple file backed {@link ReferenceExtendedStore} implementation.
 * <p>
 * The entire dataset is loaded into memory once (at construction) and kept there. Reads are served entirely from the
 * in-memory view, so they never touch the disk. Every mutation is applied to an in-memory copy, persisted to disk, and
 * only swapped in as the live view once the write succeeds; this keeps the in-memory view and the on-disk file from
 * ever diverging, even if a write fails. This makes the store suitable for small datasets, local development, tests and
 * tooling, rather than high throughput or large scale production workloads.
 * <p>
 * The store is thread-safe. A {@link ReadWriteLock} guards access, so reads can proceed concurrently while mutations
 * are exclusive. Writes are atomic on disk: data is written to a temporary file and then moved into place.
 * <p>
 * <b>Single process only:</b> the lock guards a single instance within a single JVM. This store does not provide any
 * cross-process or cross-instance coordination, so pointing multiple {@code FileStore} instances (or multiple
 * processes) at the same backing file concurrently can lead to lost updates. Use a single instance per file.
 *
 * @param <T> type of item being stored, must implement the {@link Id} interface
 * @since 1.3.0
 */
@Slf4j
public class FileStore<T extends Id> implements ReferenceExtendedStore<T> {

    private final ObjectMapper mapper;
    private final Path dataFile;
    private final TypeReference<T> typeReference;
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    /**
     * live in-memory view of the data; replaced wholesale (never mutated in place) once a write succeeds
     */
    private Map<String, T> data;

    /**
     * live in-memory view of the reference index; replaced wholesale once a write succeeds
     */
    private Map<String, List<String>> index;

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
        initialize();
    }

    @Override
    public void create(final T item) {
        mutate((data, index) -> {
            if (data.containsKey(item.id())) {
                throw new IllegalArgumentException("item already created for id:" + item.id());
            }
            data.put(item.id(), item);
        });
    }

    @Override
    public void create(final T item, final List<String> refIds) {
        mutate((data, index) -> {
            if (data.containsKey(item.id())) {
                throw new IllegalArgumentException("item already created for id:" + item.id());
            }
            data.put(item.id(), item);
            if (refIds != null) {
                refIds.forEach(refId -> index.computeIfAbsent(refId, k -> new ArrayList<>()).add(item.id()));
            }
        });
    }

    @Override
    public void update(final T item) {
        mutate((data, index) -> {
            if (!data.containsKey(item.id())) {
                throw new IllegalArgumentException("update cannot be done on unknown item id:" + item.id());
            }
            data.put(item.id(), item);
        });
    }

    @Override
    public void delete(final String id) {
        mutate((data, index) -> {
            if (!data.containsKey(id)) {
                throw new IllegalArgumentException("id doesn't exist, cannot be deleted id:" + id);
            }
            data.remove(id);
            index.values().forEach(ids -> ids.remove(id));
        });
    }

    @Override
    public Optional<T> get(final String id) {
        return read(() -> Optional.ofNullable(data.get(id)));
    }

    @Override
    public Map<String, T> get(final List<String> ids) {
        return read(() -> ids.stream()
                .filter(data::containsKey)
                .collect(Collectors.toMap(Function.identity(), data::get)));
    }

    @Override
    public List<T> list() {
        return read(() -> new ArrayList<>(data.values()));
    }

    @Override
    public List<T> getByRefId(final String refId) {
        return read(() -> index.getOrDefault(refId, List.of()).stream()
                .map(data::get)
                .filter(Objects::nonNull)
                .toList());
    }

    /**
     * Runs a read under the shared read lock, allowing reads to proceed concurrently.
     */
    private <R> R read(final Supplier<R> reader) {
        lock.readLock().lock();
        try {
            return reader.get();
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Applies a mutation atomically. The mutation is performed against fresh copies of the current state (so a
     * validation failure leaves the live view untouched), the copies are persisted to disk, and only once the write
     * succeeds are they swapped in as the live view. This guarantees the in-memory view never diverges from disk.
     */
    private void mutate(final BiConsumer<Map<String, T>, Map<String, List<String>>> mutation) {
        lock.writeLock().lock();
        try {
            final Map<String, T> newData = new LinkedHashMap<>(data);
            final Map<String, List<String>> newIndex = deepCopyIndex(index);
            mutation.accept(newData, newIndex);
            persist(newData, newIndex);
            this.data = newData;
            this.index = newIndex;
        } finally {
            lock.writeLock().unlock();
        }
    }

    private void initialize() {
        try {
            if (dataFile.getParent() != null) {
                Files.createDirectories(dataFile.getParent());
            }
            if (Files.exists(dataFile)) {
                final Persisted<T> loaded = loadFromDisk();
                this.data = loaded.data();
                this.index = loaded.index();
            } else {
                this.data = new LinkedHashMap<>();
                this.index = new LinkedHashMap<>();
                persist(this.data, this.index);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to initialize backing file:" + dataFile, e);
        }
    }

    private Persisted<T> loadFromDisk() {
        try {
            final String content = Files.readString(dataFile);
            if (content.isBlank()) {
                return new Persisted<>(new LinkedHashMap<>(), new LinkedHashMap<>());
            }
            final FileEnvelope envelope = mapper.readValue(content, FileEnvelope.class);
            final Map<String, T> loadedData = new LinkedHashMap<>();
            if (envelope.data() != null) {
                for (final Map.Entry<String, Object> entry : envelope.data().entrySet()) {
                    loadedData.put(entry.getKey(), mapper.convertValue(entry.getValue(), typeReference));
                }
            }
            final Map<String, List<String>> loadedIndex = envelope.index() == null
                                                           ? new LinkedHashMap<>()
                                                           : deepCopyIndex(envelope.index());
            return new Persisted<>(loadedData, loadedIndex);
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to read backing file:" + dataFile, e);
        }
    }

    private void persist(final Map<String, T> data, final Map<String, List<String>> index) {
        try {
            final FileEnvelope envelope = new FileEnvelope(new LinkedHashMap<>(data), new LinkedHashMap<>(index));
            final String content = mapper.writeValueAsString(envelope);
            final Path tmp = Files.createTempFile(
                    dataFile.toAbsolutePath().getParent(), dataFile.getFileName().toString(), ".tmp");
            try {
                Files.writeString(tmp, content);
                Files.move(tmp, dataFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException e) {
                Files.deleteIfExists(tmp);
                throw e;
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to write backing file:" + dataFile, e);
        }
    }

    private static Map<String, List<String>> deepCopyIndex(final Map<String, List<String>> index) {
        final Map<String, List<String>> copy = new LinkedHashMap<>();
        for (final Map.Entry<String, List<String>> entry : index.entrySet()) {
            copy.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }
        return copy;
    }
    private record Persisted<T extends Id>(Map<String, T> data, Map<String, List<String>> index) {}

    private record FileEnvelope(Map<String, Object> data, Map<String, List<String>> index) {}
}
