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
The following are the CRUD ops in the Store interface (pretty self explanatory)
```java
    void create(final T item);
    void update(final T item);
    void delete(final String id);
    Optional<T> get(final String id);
    Map<String, T> get(final List<String> ids);
    List<T> list();
```
An extension to this has been added (since 1.2.5) ReferenceExtendedStore, to support fetch by references through indexes if a Store is able to support it.
```java
    void create(final T item, final List<String> refIds);
    List<T> getByRefId(final String refId);
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
serialization/de-serserialization, provides hook for proper error handling.<br>

It also supports indexing on references, as it implements a ReferenceExtendedStore. 
So if you have data that needs to be retrieved through some reference ids, you can do so in an optimal manner. Internally,
a LIST s-index is created, and an Aerospike query on the index is fired during retrieval

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
    protected boolean isValidDataItem(final TestData userData) {
        return true; // you may do additional checks here
    }
}
```
Your TestData class can look something like this
```java
@Builder
public record TestData(
        String id, 
        String name, 
        int age) implements Id {
}
```

That is it, you should be good to go to do the following
```java
public class Logic {
    private AerospikeStore<TestData> store
            = new MyAerospikeStore(
                        AerospikeClientHelpers.aerospikeClient(AerospikeConfiguration.builder()
                                                           .hosts("...") // set the rest
                                                           .build()), new NamespaceSet("test", "test-set"),
                        mapper, 
                        TestData.class, 
                        new DefaultErrorHandler<>());

    void myOperations() {
        store.create(new TestData("Id001", "Rick", 47));
        store.create(new TestData("Id001", "Rick", 47), List.of("animated"));
        store.update(new TestData("Id001", "Rick Sanchez", 48));
        Optional<TestData> result = store.get("Id001");
        List<TestData> result = store.getByRefId("animated");
        List<TestData> result = store.get(List.of("Id001", "Id002"));
        List<TestData> result = store.list();
        store.delete("Id001");
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