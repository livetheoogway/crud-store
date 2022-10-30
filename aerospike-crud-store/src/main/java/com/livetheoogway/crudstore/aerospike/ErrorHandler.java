package com.livetheoogway.crudstore.aerospike;

import com.aerospike.client.AerospikeException;
import com.fasterxml.jackson.core.JsonProcessingException;

import java.util.Optional;

public interface ErrorHandler<T> {
    void onDeleteError() throws Exception;

    T onNoRecordFound(String id) throws RuntimeException;

    T onDeSerializationError(String id, final JsonProcessingException e);

    T onAerospikeError(String id, AerospikeException e);

    T onSerializationError(JsonProcessingException e);

    Optional<T> onNoRecordFoundForBulkGet(String id);

    Optional<T> onDeSerializationErrorDuringBulkGet(String id, JsonProcessingException e);

    T onExecutionError(Exception e);
}
