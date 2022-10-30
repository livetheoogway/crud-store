package com.livetheoogway.crudstore.aerospike;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

public record NamespaceSet(
        @NotNull @NotEmpty String namespace,
        @NotNull @NotEmpty String set) {
}
