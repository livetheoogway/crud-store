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
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.livetheoogway.crudstore.core.Id;
import com.livetheoogway.crudstore.core.Store;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Slf4j
public abstract class AerospikeStore<T extends Id> implements Store<T> {

    protected static final String DATA = "data";
    protected final ObjectMapper mapper;
    protected final IAerospikeClient client;
    protected final NamespaceSet namespaceSet;
    protected final WritePolicy createPolicy;
    protected final WritePolicy updateOnly;
    protected final ErrorHandler<T> errorHandler;
    protected final TypeReference<T> typeReference;

    protected AerospikeStore(final IAerospikeClient client,
                             final NamespaceSet namespaceSet,
                             final ObjectMapper mapper,
                             final Class<T> clazz,
                             final ErrorHandler<T> errorHandler) {
        this(client, namespaceSet, mapper, clazz, errorHandler, true);
    }

    protected AerospikeStore(final IAerospikeClient client,
                             final NamespaceSet namespaceSet,
                             final ObjectMapper mapper,
                             final Class<T> clazz,
                             final ErrorHandler<T> errorHandler,
                             final boolean failOnCreateIfRecordExists) {
        this(client, namespaceSet, mapper, new TypeReference<>() {
            @Override
            public Type getType() {
                return clazz;
            }
        }, errorHandler, failOnCreateIfRecordExists);
    }

    protected AerospikeStore(final IAerospikeClient client,
                             final NamespaceSet namespaceSet,
                             final ObjectMapper mapper,
                             final TypeReference<T> typeReference,
                             final ErrorHandler<T> errorHandler) {
        this(client, namespaceSet, mapper, typeReference, errorHandler, true);
    }

    protected AerospikeStore(final IAerospikeClient client,
                             final NamespaceSet namespaceSet,
                             final ObjectMapper mapper,
                             final TypeReference<T> typeReference,
                             final ErrorHandler<T> errorHandler,
                             final boolean failOnCreateIfRecordExists) {
        this.client = client;
        this.namespaceSet = namespaceSet;
        this.mapper = mapper;
        this.errorHandler = errorHandler;
        this.typeReference = typeReference;
        this.createPolicy = new WritePolicy(client.getWritePolicyDefault());
        createPolicy.recordExistsAction = failOnCreateIfRecordExists ? RecordExistsAction.CREATE_ONLY
                                                                     : RecordExistsAction.REPLACE;
        this.updateOnly = new WritePolicy(client.getWritePolicyDefault());
        updateOnly.recordExistsAction = RecordExistsAction.UPDATE_ONLY;
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
        write(item, createPolicy);
    }

    /**
     * this will throw an error if the item does not exist
     *
     * @param item to be updated
     */
    @Override
    public void update(final T item) {
        write(item, updateOnly);
    }


    @Override
    public void delete(final String id) {
        exec("delete", id, () -> {
            final Key requestIdKey = new Key(namespaceSet.namespace(), namespaceSet.set(), id);
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
        final T data = exec("get", id, () -> {
            final Key requestIdKey = new Key(namespaceSet.namespace(), namespaceSet.set(), id);
            final Record asRecord = client.get(client.getReadPolicyDefault(), requestIdKey);
            return extractItem(id, asRecord);
        }, errorHandler);
        if (isValidDataItem(data)) {
            return Optional.ofNullable(data);
        }
        return Optional.empty();
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
        final Key[] batchReads = ids.stream()
                .map(id -> (new Key(namespaceSet.namespace(), namespaceSet.set(), id)))
                .toArray(Key[]::new);
        final Record[] records = client.get(client.getBatchPolicyDefault(), batchReads);
        return IntStream
                .range(0, ids.size())
                .mapToObj(index -> extractItemForBulkOperations(batchReads[index].userKey.toString(), records[index]))
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
                        extractItemForBulkOperations(key.userKey.toString(), asRecord)
                                .ifPresent(items::add));
        return items;
    }

    public T extractItem(String id, Record asRecord) {
        if (asRecord == null) {
            return errorHandler.onNoRecordFound(id);
        }
        val data = asRecord.getString(DATA);
        try {
            return mapper.readValue(data, typeReference);
        } catch (JsonProcessingException e) {
            log.error("[{}] Deserialization error id:{} record:{}", typeReference.getType().getTypeName(), id, asRecord, e);
            return errorHandler.onDeSerializationError(id, e);
        }
    }

    public Optional<T> extractItemForBulkOperations(String id, Record asRecord) {
        if (asRecord == null) {
            return errorHandler.onNoRecordFoundForBulkGet(id);
        }
        val data = asRecord.getString(DATA);
        try {
            return Optional.of(mapper.readValue(data, typeReference));
        } catch (JsonProcessingException e) {
            log.error("[{}] Deserialization error id:{} record:{}", typeReference.getType().getTypeName(), id, asRecord, e);
            return errorHandler.onDeSerializationErrorDuringBulkGet(id, e);
        }
    }

    protected RecordDetails recordDetails(T item) throws JsonProcessingException {
        final Bin dataBin = new Bin(DATA, mapper.writeValueAsString(item));
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
    protected <R> R exec(final String operation, final String id, ESupplier<R> response, ErrorHandler<R> errorHandler) {
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

    private void write(final T item, WritePolicy defaultWritePolicy) {
        val id = item.id();
        exec("operation:" + defaultWritePolicy.recordExistsAction, id, () -> {
            val recordDetails = recordDetails(item);
            var writePolicy = defaultWritePolicy;
            if (recordDetails.expiration() > 0) {
                writePolicy = new WritePolicy(writePolicy);
                writePolicy.expiration = recordDetails.expiration();
            }
            val requestIdKey = new Key(namespaceSet.namespace(), namespaceSet.set(), id);
            client.put(writePolicy, requestIdKey, recordDetails.bins());
            return null;
        }, errorHandler);
    }
}
