package com.livetheoogway.crudstore.aerospike;

public interface ESupplier <T> {
    T get() throws Exception;
}
