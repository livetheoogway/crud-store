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

public class DefaultErrorHandler<T> implements ErrorHandler<T> {
    @Override
    public void onDeleteUnsuccessful() {
        throw new RuntimeException("unsuccessful delete");
    }

    @Override
    public Optional<T> onNoRecordFound(final String id) {
        return Optional.empty();
    }

    @Override
    public Optional<T> onDeSerializationError(final String id, final JsonProcessingException e) {
        throw new RuntimeException(id + " not deserializable", e);
    }

    @Override
    public Optional<T> onAerospikeError(final String id, final AerospikeException e) {
        throw new RuntimeException(e);
    }

    @Override
    public Optional<T> onSerializationError(final String id, final JsonProcessingException e) {
        throw new RuntimeException(id + " not serializable", e);
    }

    @Override
    public Optional<T> onExecutionError(final String id, final Exception e) {
        throw new RuntimeException(e);
    }

    @Override
    public List<T> onRefIdLookupFailure(String refId) {
        throw new RuntimeException("refId lookup failed");
    }
}
