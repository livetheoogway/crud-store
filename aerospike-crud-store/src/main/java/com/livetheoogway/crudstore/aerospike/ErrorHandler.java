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
import com.fasterxml.jackson.core.JsonProcessingException;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

@SuppressWarnings("java:S112")
public interface ErrorHandler<T> {
    void onDeleteUnsuccessful();

    Optional<T> onNoRecordFound(String id);

    Optional<T> onDeSerializationError(String id, final JsonProcessingException e);

    Optional<T> onAerospikeError(String id, AerospikeException e);

    default List<T> onAerospikeErrorForRefId(String id, AerospikeException e) {
        return List.of();
    }

    Optional<T> onSerializationError(final String id, JsonProcessingException e);

    Optional<T> onExecutionError(final String id, Exception e);

    List<T>  onRefIdLookupFailure(final String refId);

    @SuppressWarnings("unchecked")
    default <R> ErrorHandler<R> cloneOnType(Function<T, R> mapper) {
        return new ErrorHandler<>() {
            @Override
            public void onDeleteUnsuccessful() {
                ErrorHandler.this.onDeleteUnsuccessful();
            }

            @Override
            public Optional<R> onNoRecordFound(String id) {
                return ErrorHandler.this.onNoRecordFound(id).map(mapper);
            }

            @Override
            public Optional<R> onDeSerializationError(String id, JsonProcessingException e) {
                return ErrorHandler.this.onDeSerializationError(id, e).map(mapper);
            }

            @Override
            public Optional<R> onAerospikeError(String id, AerospikeException e) {
                return ErrorHandler.this.onAerospikeError(id, e).map(mapper);
            }

            @Override
            public List<R> onAerospikeErrorForRefId(String id, AerospikeException e) {
                return (List<R>) ErrorHandler.this.onAerospikeErrorForRefId(id, e);
            }

            @Override
            public Optional<R> onSerializationError(String id, JsonProcessingException e) {
                return ErrorHandler.this.onSerializationError(id, e).map(mapper);
            }

            @Override
            public Optional<R> onExecutionError(String id, Exception e) {
                return ErrorHandler.this.onExecutionError(id, e).map(mapper);
            }

            @Override
            public List<R> onRefIdLookupFailure(String refId) {
                return ErrorHandler.this.onRefIdLookupFailure(refId).stream().map(mapper).toList();
            }
        };
    }
}
