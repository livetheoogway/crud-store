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

package com.livetheoogway.crudstore.aerospike.stores;

import com.aerospike.client.IAerospikeClient;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.livetheoogway.crudstore.aerospike.AerospikeStore;
import com.livetheoogway.crudstore.aerospike.ErrorHandler;
import com.livetheoogway.crudstore.aerospike.NamespaceSet;
import com.livetheoogway.crudstore.core.Id;

public class TestTypeRefAerospikeStore<T extends Id> extends AerospikeStore<T> {

    public TestTypeRefAerospikeStore(final IAerospikeClient client,
                                        final NamespaceSet namespaceSet,
                                        final ObjectMapper mapper,
                                        final TypeReference<T> typeReference,
                                        final ErrorHandler<T> errorHandler) {
        super(client, namespaceSet, mapper, typeReference, errorHandler);
    }
}
