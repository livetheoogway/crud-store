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

package com.livetheoogway.crudstore.core;

import java.util.List;

/**
 * Extension of Store interface to support reference based queries
 * This may be used for data stores that support indexing
 *
 * @param <T> type of item being stored, must implement the Id interface
 * @since 1.2.5
 */
public interface ReferenceExtendedStore<T extends Id> extends Store<T> {
    /**
     * Create a new item
     *
     * @param item   item to be created
     * @param refIds reference ids to be indexed
     */
    void create(final T item, final List<String> refIds);

    /**
     * Get items by reference id
     *
     * @param refId reference id
     * @return list of items
     */
    List<T> getByRefId(final String refId);
}
