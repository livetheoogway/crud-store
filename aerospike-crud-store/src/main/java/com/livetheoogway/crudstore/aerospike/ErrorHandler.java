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
}
