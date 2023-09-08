package com.livetheoogway.crudstore.aerospike;

import lombok.Builder;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

@Builder
public record AerospikeConfiguration(
        @NotNull String hosts,
        @Max(65535) int port,
        @NotNull @Min(0) Integer retries,
        @NotNull @Min(0) Integer sleepBetweenRetries,
        @NotNull @Min(0) Integer timeout,
        @NotNull @Min(1) Integer maxConnectionsPerNode,
        String user,
        String password,
        String tlsName) {}
