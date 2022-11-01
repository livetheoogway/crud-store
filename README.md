# CRUD Store
A collection of CRUD components implementing a clean interface

## Features

#### Cached Store
Wraps your store with a Caffine cache

#### Aerospike Store
Usage:
```java
public class MyAerospikeStore extends AerospikeStore<TestData> {

    protected MyAerospikeStore(final IAerospikeClient client,
                               final NamespaceSet namespaceSet,
                               final ObjectMapper mapper,
                               final Class<TestData> clazz,
                               final ErrorHandler<TestData> errorHandler) {
        super(client, namespaceSet, mapper, clazz, errorHandler);
    }

    @Override
    protected boolean isValidDataItem(final TestData testData) {
        return true;
    }
}
```