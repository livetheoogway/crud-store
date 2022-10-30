package com.livetheoogway.crudstore.core;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface Store <T extends Id> {
    void create(final T item);

    void update(final T item);

    void delete(final String id);

    Optional<T> get(final String id);

    Map<String, T> get(final List<String> ids);

    List<T> list();
}
