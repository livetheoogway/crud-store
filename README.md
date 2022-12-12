<p align="center">
  <h1 align="center">CRUD Store</h1>
  <p align="center">A collection of CRUD components implementing a clean interface<p>
  <p align="center">
    <a href="https://github.com/livetheoogway/crud-store/actions">
    	<img src="https://github.com/livetheoogway/crud-store/actions/workflows/actions.yml/badge.svg"/>
    </a>
    <a href="https://s01.oss.sonatype.org/content/repositories/releases/com/livetheoogway/crudstore/">
    	<img src="https://img.shields.io/maven-central/v/com.livetheoogway.crudstore/crud-store"/>
    </a>
    <a href="https://github.com/livetheoogway/crud-store/blob/master/LICENSE">
    	<img src="https://img.shields.io/github/license/livetheoogway/crud-store" alt="license" />
    </a></p>
  <p align="center">
    <a href="https://sonarcloud.io/project/overview?id=livetheoogway_crud-store">
    	<img src="https://sonarcloud.io/api/project_badges/measure?project=livetheoogway_crud-store&metric=alert_status"/>
    </a>
    <a href="https://sonarcloud.io/project/overview?id=livetheoogway_crud-store">
    	<img src="https://sonarcloud.io/api/project_badges/measure?project=livetheoogway_crud-store&metric=coverage"/>
    </a>
    <a href="https://sonarcloud.io/project/overview?id=livetheoogway_crud-store">
    	<img src="https://sonarcloud.io/api/project_badges/measure?project=livetheoogway_crud-store&metric=bugs"/>
    </a>
    <a href="https://sonarcloud.io/project/overview?id=livetheoogway_crud-store">
    	<img src="https://sonarcloud.io/api/project_badges/measure?project=livetheoogway_crud-store&metric=vulnerabilities"/>
    </a>
  </p>
</p>

## What is it?

This is supposed to be a very simple codebase, with even simpler features.<br>
I used to write these crud stores several times across use-cases, decided to create a simple interface, and some default
abstractions of some popular databases that implements the interface.<br>
I've been able to add the abstraction for aerospike. Impls for other databases are welcome.

## Features

#### The interface
The following functions are supported in the interface
```java
    void create(final T item);
    void update(final T item);
    void delete(final String id);
    Optional<T> get(final String id);
    Map<String, T> get(final List<String> ids);
    List<T> list();
```

#### Cached Store

Wraps your store with a Caffine in-memory cache. Use this to quickly have a cache on top of your store<br>
Usage:

```java
Store<MyData> store=new CachingStore(
        myAerospikeStore,
        1000, // maxsize of cache
        10,   // expire after write in seconds
        5,    // refresh after write in seconds 
        );
```

#### Aerospike Store

This impl provides crud for your data by storing it in a default set (`data`), hides details of
serialization/de-serserialization, provides hook for proper error handling
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

## How to use it

### Maven Dependency

The following can be added in your dependencies, for the aerospike crud-store.

```xml

<dependency>
    <groupId>com.livetheoogway.crudstore</groupId>
    <artifactId>aerospike-crud-store</artifactId>
    <version>${crudstore.version}</version> <!--look for the latest version on top-->
</dependency>
```

## Tech Dependencies

- Java 17
- Aerospike (any provided version should do)

## Contributions

Please raise Issues, Bugs or any feature requests at [Github Issues](https://github.com/livetheoogway/crud-store/issues)
. <br>
If you plan on contributing to the code, fork the repository and raise a Pull Request back here.