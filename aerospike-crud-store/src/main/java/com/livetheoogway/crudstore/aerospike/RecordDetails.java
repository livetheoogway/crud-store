package com.livetheoogway.crudstore.aerospike;

import com.aerospike.client.Bin;

public record RecordDetails(int expiration, Bin... bins) {
}
