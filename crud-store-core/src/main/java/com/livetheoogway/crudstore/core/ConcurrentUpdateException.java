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

package com.livetheoogway.crudstore.core;

/**
 * Thrown by {@link ConcurrentStore#checkAndUpdate(Id, Object)} when a compare-and-set update fails because the
 * stored item's version no longer matches the expected version, i.e. it was concurrently modified (or is absent).
 * <p>
 * This is a fail-fast signal that a race condition occurred. Callers typically respond by re-reading the item via
 * {@link ConcurrentStore#getWithVersion(String)} and retrying the operation, which composes cleanly with declarative
 * retry frameworks (Resilience4j, Spring Retry, etc.).
 *
 * @since 1.3.0
 */
public class ConcurrentUpdateException extends RuntimeException {

    public ConcurrentUpdateException(final String message) {
        super(message);
    }

    public ConcurrentUpdateException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
