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

import com.aerospike.client.AerospikeException;
import com.aerospike.client.Bin;
import com.aerospike.client.IAerospikeClient;
import com.aerospike.client.Key;
import com.aerospike.client.Record;
import com.aerospike.client.policy.RecordExistsAction;
import com.aerospike.client.policy.WritePolicy;
import com.aerospike.client.query.Filter;
import com.aerospike.client.query.IndexCollectionType;
import com.aerospike.client.query.IndexType;
import com.aerospike.client.query.RecordSet;
import com.aerospike.client.query.Statement;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.livetheoogway.crudstore.core.Id;
import com.livetheoogway.crudstore.core.ReferenceExtendedStore;
import lombok.extern.slf4j.Slf4j;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Slf4j
public abstract class AerospikeStore<T extends Id> implements ReferenceExtendedStore<T> {

    private static final String DEFAULT_DATA_BIN = "data";
    private static final String DEFAULT_REF_ID_BIN = "refId";
    private static final String DEFAULT_REF_ID_INDEX_SUFFIX = "_idx";

    protected final ObjectMapper mapper;
    protected final IAerospikeClient client;
    protected final NamespaceSet namespaceSet;
    protected final WritePolicy createPolicy;
    protected final WritePolicy updateOnly;
    protected final ErrorHandler<T> errorHandler;
    protected final TypeReference<T> typeReference;
    protected final AerospikeStoreSetting storeSetting;

    protected AerospikeStore(final IAerospikeClient client,
                             final NamespaceSet namespaceSet,
                             final ObjectMapper mapper,
                             final Class<T> clazz,
                             final ErrorHandler<T> errorHandler) {
        this(client, namespaceSet, mapper, clazz, errorHandler, null);
    }

    protected AerospikeStore(final IAerospikeClient client,
                             final NamespaceSet namespaceSet,
                             final ObjectMapper mapper,
                             final Class<T> clazz,
                             final ErrorHandler<T> errorHandler,
                             final AerospikeStoreSetting storeSetting) {
        this(client, namespaceSet, mapper, new TypeReference<>() {
            @Override
            public Type getType() {
                return clazz;
            }
        }, errorHandler, storeSetting);
    }

    protected AerospikeStore(final IAerospikeClient client,
                             final NamespaceSet namespaceSet,
                             final ObjectMapper mapper,
                             final TypeReference<T> typeReference,
                             final ErrorHandler<T> errorHandler) {
        this(client, namespaceSet, mapper, typeReference, errorHandler, null);
    }

    protected AerospikeStore(final IAerospikeClient client,
                             final NamespaceSet namespaceSet,
                             final ObjectMapper mapper,
                             final TypeReference<T> typeReference,
                             final ErrorHandler<T> errorHandler,
                             final AerospikeStoreSetting storeSetting) {
        this.client = client;
        this.namespaceSet = namespaceSet;
        this.mapper = mapper;
        this.errorHandler = errorHandler;
        this.typeReference = typeReference;
        this.createPolicy = new WritePolicy(client.getWritePolicyDefault());
        this.storeSetting = defaultIfNull(storeSetting, namespaceSet.set());
        createPolicy.recordExistsAction = this.storeSetting.failOnCreateIfRecordExists()
                                          ? RecordExistsAction.CREATE_ONLY
                                          : RecordExistsAction.REPLACE;
        this.updateOnly = new WritePolicy(client.getWritePolicyDefault());
        updateOnly.recordExistsAction = RecordExistsAction.UPDATE_ONLY;
        setupIndexes();
    }

    /**
     * @param t item
     * @return true if the item t is valid you should not throw error here, just return false to filter.
     * Let the layer above this handle how to treat an invalid item. They can throw the error there
     */
    protected boolean isValidDataItem(T t) {
        return true;
    }

    /**
     * override this method to change the expiration time.
     * By default, the expiry is infinite
     *
     * @param item item being saved
     * @return expiry (-1 if you want no expiry on the items being saved into aerospike)
     */
    protected int expiration(T item) {
        return -1;
    }

    /**
     * this will throw an error if the item's ID  is already present in aerospike
     *
     * @param item to be written
     */
    @Override
    public void create(final T item) {
        write(item.id(), () -> recordDetails(item, null), createPolicy);
    }

    @Override
    public void create(final T item, List<String> refIds) {
        write(item.id(), () -> recordDetails(item, refIds), createPolicy);
    }

    /**
     * this will throw an error if the item does not exist
     *
     * @param item to be updated
     */
    @Override
    public void update(final T item) {
        write(item.id(), () -> recordDetails(item, null), updateOnly);
    }


    @Override
    public void delete(final String id) {
        exec("delete", id, () -> {
            final var requestIdKey = new Key(namespaceSet.namespace(), namespaceSet.set(), id);
            if (!client.delete(updateOnly, requestIdKey)) {
                errorHandler.onDeleteUnsuccessful();
            }
            return null;
        }, errorHandler);
    }

    /**
     * will throw an error if the ID is absent or we were unable to deserialize the data to the right DTO
     *
     * @param id key
     * @return data optional
     */
    @Override
    public Optional<T> get(final String id) {
        return exec("get", id, () -> {
            final var requestIdKey = new Key(namespaceSet.namespace(), namespaceSet.set(), id);
            final var asRecord = client.get(client.getReadPolicyDefault(), requestIdKey);
            return extractItemFromRecord(id, asRecord);
        }, errorHandler);
    }

    /**
     * returns a map of data elements, by filtering out
     * error handling is not done correctly here, if any of the items in the list throw an error, it will abort the
     * whole operation and throw the error
     *
     * @param ids ids to be fetched
     * @return Map<Key, Item>
     */
    @Override
    public Map<String, T> get(final List<String> ids) {
        final var batchReads = ids.stream()
                .map(id -> (new Key(namespaceSet.namespace(), namespaceSet.set(), id)))
                .toArray(Key[]::new);
        final Record[] records = client.get(client.getBatchPolicyDefault(), batchReads);
        return IntStream
                .range(0, ids.size())
                .mapToObj(index -> extractItemFromRecord(batchReads[index].userKey.toString(), records[index]))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .filter(this::isValidDataItem)
                .collect(Collectors.toMap(Id::id, Function.identity()));
    }

    /**
     * get all items in the set
     * Use this cautiously, you could bring down your application if you use this on a large set, or even worse, you
     * could bring down aerospike
     *
     * @return all items saved in the set
     */
    @Override
    public List<T> list() {
        final List<T> items = Collections.synchronizedList(new ArrayList<>());
        client.scanAll(
                client.getScanPolicyDefault(),
                namespaceSet.namespace(), namespaceSet.set(),
                (key, asRecord) ->
                        extractItemFromRecord(key.userKey.toString(), asRecord)
                                .ifPresent(items::add));
        return items;
    }

    @Override
    public List<T> getByRefId(final String refId) {
        final var statement = new Statement();
        statement.setNamespace(namespaceSet.namespace());
        statement.setSetName(namespaceSet.set());
        statement.setIndexName(storeSetting.refIdIndex());
        statement.setBinNames(storeSetting.dataBin());
        statement.setFilter(Filter.contains(storeSetting.refIdBin(), IndexCollectionType.LIST, refId));
        final List<T> results = new ArrayList<>();
        try (final RecordSet recordSet = client.query(client.getQueryPolicyDefault(), statement)) {
            while (recordSet.next()) {
                extractItemFromRecord(recordSet.getKey().userKey.toString(), recordSet.getRecord())
                        .ifPresent(results::add);
            }
        } catch (AerospikeException e) {
            log.error("[{}] Aerospike Error {} item for id:{}", typeReference.getType().getTypeName(), "getByRefId", refId, e);
            return errorHandler.onAerospikeErrorForRefId(refId, e);
        }
        return results;
    }

    /**
     * given an Aerospike Record, extract the data out of it
     * @param id       key
     * @param asRecord how the record is extracted
     * @return optionally return data if available
     */
    protected Optional<T> extractItemFromRecord(final String id, final Record asRecord) {
        if (asRecord == null) {
            return errorHandler.onNoRecordFound(id);
        }
        final var data = asRecord.getString(storeSetting.dataBin());
        try {
            final T value = mapper.readValue(data, typeReference);
            if (isValidDataItem(value)) {
                return Optional.of(value);
            }
            log.warn("Invalid item found for id:{}", id);
            return Optional.empty();
        } catch (JsonProcessingException e) {
            log.error("[{}] Deserialization error id:{} record:{}", typeReference.getType().getTypeName(), id, asRecord, e);
            return errorHandler.onDeSerializationError(id, e);
        }
    }

    protected RecordDetails recordDetails(final T item, final List<String> refIds) throws JsonProcessingException {
        final var dataBin = new Bin(storeSetting.dataBin(), mapper.writeValueAsString(item));
        if (refIds != null && !refIds.isEmpty()) {
            final var refIdBin = new Bin(storeSetting.refIdBin(), refIds);
            return new RecordDetails(expiration(item), dataBin, refIdBin);
        }
        return new RecordDetails(expiration(item), dataBin);
    }

    /**
     * try to do a supplier get, and handle all possible error scenarios
     *
     * @param operation (used for logging) name of the operation
     * @param id        (used for logging) identifier being worked upon, by the supplier function
     * @param response  supplier that will provide a result
     * @param <R>       result type
     * @return result
     */
    protected <R> Optional<R> exec(final String operation,
                                   final String id,
                                   final ESupplier<Optional<R>> response,
                                   final ErrorHandler<R> errorHandler) {
        try {
            log.info("[{}] {} item for id:{}", typeReference.getType().getTypeName(), operation, id);
            return response.get();
        } catch (AerospikeException e) {
            log.error("[{}] Aerospike Error {} item for id:{}", typeReference.getType().getTypeName(), operation, id, e);
            return errorHandler.onAerospikeError(id, e);
        } catch (JsonProcessingException e) {
            log.error("Error while converting to string for id:{}", id, e);
            return errorHandler.onSerializationError(id, e);
        } catch (Exception e) {
            log.error("Error while {} item for id:{}", operation, id, e);
            return errorHandler.onExecutionError(id, e);
        }
    }

    protected void write(final String id,
                         final ESupplier<RecordDetails> recordDetailsSupplier,
                         final WritePolicy defaultWritePolicy) {
        exec("operation:" + defaultWritePolicy.recordExistsAction, id, () -> {
            final var recordDetails = recordDetailsSupplier.get();
            var writePolicy = defaultWritePolicy;
            if (recordDetails.expiration() > 0) {
                writePolicy = new WritePolicy(writePolicy);
                writePolicy.expiration = recordDetails.expiration();
            }
            final var requestIdKey = new Key(namespaceSet.namespace(), namespaceSet.set(), id);
            client.put(writePolicy, requestIdKey, recordDetails.bins());
            return null;
        }, errorHandler);
    }

    private void setupIndexes() {
        try {
            final var refIdIndexTask = client
                    .createIndex(null, namespaceSet.namespace(), namespaceSet.set(),
                                 storeSetting.refIdIndex(), storeSetting.refIdBin(),
                                 IndexType.STRING,
                                 IndexCollectionType.LIST);
            if (refIdIndexTask != null) {
                refIdIndexTask.waitTillComplete();
                log.info("Created index: {}", storeSetting.refIdIndex());
            }
        } catch (AerospikeException e) {
            if (e.getResultCode() == 100) {
                log.info("Index already exists:{}", storeSetting.refIdIndex());
                return;
            }
            log.error("Error while creating index:{}", storeSetting.refIdIndex(), e);
            throw e;
        }
    }

    private AerospikeStoreSetting defaultIfNull(final AerospikeStoreSetting storeSetting,
                                                final @NotNull @NotEmpty String set) {
        final var builder = AerospikeStoreSetting.builder();
        if (storeSetting == null) {
            return builder
                    .dataBin(DEFAULT_DATA_BIN)
                    .refIdBin(DEFAULT_REF_ID_BIN)
                    .failOnCreateIfRecordExists(true)
                    .refIdIndex(set + DEFAULT_REF_ID_INDEX_SUFFIX).build();
        }
        builder.dataBin(Objects.requireNonNullElse(storeSetting.dataBin(), DEFAULT_DATA_BIN));
        final var refIdBin = Objects.requireNonNullElse(storeSetting.refIdBin(), DEFAULT_REF_ID_BIN);
        builder.refIdBin(refIdBin);
        builder.refIdIndex(Objects.requireNonNullElse(storeSetting.refIdIndex(),
                                                      set + "_" + refIdBin + DEFAULT_REF_ID_INDEX_SUFFIX));
        return builder.build();
    }
}
