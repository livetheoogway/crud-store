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

import com.aerospike.client.Bin;

import java.util.Arrays;
import java.util.Objects;

public record RecordDetails(int expiration, Bin... bins) {
    @Override
    public String toString() {
        return "RecordDetails{" + "expiration=" + expiration
                + ", bins=" + Arrays.toString(bins)
                + '}';
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final RecordDetails that = (RecordDetails) o;
        return expiration == that.expiration && Arrays.equals(bins, that.bins);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(expiration);
        result = 31 * result + Arrays.hashCode(bins);
        return result;
    }
}
