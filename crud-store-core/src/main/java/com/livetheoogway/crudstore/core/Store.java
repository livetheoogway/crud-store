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
import java.util.Map;
import java.util.Optional;

/**
 * The main Store interface with basic CRUD operations
 *
 * @param <T> type of item being stored, must implement the Id interface
 */
public interface Store<T extends Id> {

    /**
     * Create a new item
     *
     * @param item item to be created
     */
    void create(final T item);

    /**
     * Update an existing item
     *
     * @param item item to be updated
     */
    void update(final T item);

    /**
     * Delete an existing item
     *
     * @param id id of the item to be deleted
     */
    void delete(final String id);

    /**
     * Get an existing item
     *
     * @param id id of the item to be fetched
     * @return item if found
     */
    Optional<T> get(final String id);

    /**
     * Get multiple items
     *
     * @param ids ids of the items to be fetched
     * @return map of id to item
     */
    Map<String, T> get(final List<String> ids);

    /**
     * List all items
     * Note: This is to be used carefully, as this might have memory implications based on the type of underlying
     * datastore being used
     *
     * @return list of all items
     */
    List<T> list();
}
