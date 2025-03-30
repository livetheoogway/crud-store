package com.livetheoogway.crudstore.aerospike;

import lombok.Builder;

@Builder
public record RefIdSetting(
        boolean disabled,
        String refIdBin,
        String refIdIndex
) {
}
