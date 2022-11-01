<p align="center">
  <h1 align="center">CRUD Store</h1>
  <p align="center">A collection of CRUD components implementing a clean interface<p>
  <p align="center">
    <a href="https://github.com/livetheoogway/crud-store/actions">
    	<img src="https://github.com/livetheoogway/crud-store/actions/workflows/actions.yml/badge.svg"/>
    </a>
    <a href="https://s01.oss.sonatype.org/content/repositories/releases/com/livetheoogway/crud-store/">
    	<img src="https://img.shields.io/maven-central/v/com.livetheoogway.crud-store/crud-store"/>
    </a>
    <a href="https://github.com/livetheoogway/crud-store/blob/master/LICENSE">
    	<img src="https://img.shields.io/github/license/livetheoogway/crud-store" alt="license" />
    </a>
    <a href="https://sonarcloud.io/project/overview?id=livetheoogway_crud-store">
    	<img src="https://sonarcloud.io/api/project_badges/measure?project=livetheoogway_crud-store&metric=alert_status"/>
    </a>
  </p>
</p>


## Features

#### Cached Store

Wraps your store with a Caffine cache

- you may wrap the
  Usage:

```java
Store<MyData> store = new CachingStore(
        myAerospikeStore,
        10, // expire after write in seconds
        5, // refresh after write in seconds 
        );
```

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
        return true; // you may do additional checks here
    }
}
```