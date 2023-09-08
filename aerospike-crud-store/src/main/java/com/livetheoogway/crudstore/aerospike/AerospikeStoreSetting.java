package com.livetheoogway.crudstore.aerospike;

import lombok.Builder;

@Builder
public record AerospikeStoreSetting(
        boolean failOnCreateIfRecordExists,
        String dataBin,
        String refIdBin,
        String refIdIndex) {
}
