<p align="center">
  <h1 align="center">CRUD Store</h1>
  <p align="center">A collection of CRUD components implementing a clean interface<p>
  <p align="center">
    <a href="https://github.com/livetheoogway/crud-store/actions">
    	<img src="https://github.com/livetheoogway/crud-store/actions/workflows/build.yml/badge.svg"/>
    </a>
    <a href="https://s01.oss.sonatype.org/content/repositories/releases/com/livetheoogway/crudstore/">
    	<img src="https://img.shields.io/maven-central/v/com.livetheoogway.crudstore/crud-store"/>
    </a>
    <a href="https://github.com/livetheoogway/crud-store/blob/master/LICENSE">
    	<img src="https://img.shields.io/github/license/livetheoogway/crud-store" alt="license" />
    </a>
  </p>

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

I used to write these crud stores several times across use-cases, decided to create a simple interface, and some default
abstractions of popular databases that implements the interface.<br>
I've been able to add abstractions for Aerospike and for a simple file backed store. Impls for other databases as contributions are welcome.

## Features

#### The interface
The following are the CRUD ops in the Store interface (pretty self-explanatory)
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

#### Optimistic concurrency (compare-and-set)

Since 1.3.0, stores that can honour an atomic check-and-set implement `ConcurrentStore<T, V>` (a sub-interface of
`Store`). This is kept separate from the base `Store` (interface segregation), so callers handed a plain `Store` never
risk a runtime `UnsupportedOperationException`. Read an item together with an opaque version stamp, then update it only
if it has not changed in the meantime:
```java
    Optional<Versioned<T, V>> getWithVersion(final String id);
    void checkAndUpdate(final T item, final V expectedVersion);
```
The version type `V` is left to the store, so each backend can use its natural concurrency token: a numeric generation
(`Long`) for Aerospike/HBase, or an opaque `String` ETag for object stores such as S3/CosmosDB. `checkAndUpdate` throws
`ConcurrentUpdateException` if the item was absent or was concurrently modified (version mismatch). Throwing (rather
than returning a boolean) is the idiomatic optimistic-locking contract used by JPA/Hibernate and composes cleanly with
declarative retry frameworks (Resilience4j, Spring Retry). The Aerospike and File stores implement
`ConcurrentStore<T, Long>`; the Aerospike store backs the version with the native record generation, so the
check-and-set is enforced atomically server-side (`GenerationPolicy.EXPECT_GEN_EQUAL`).
```java
ConcurrentStore<TestData, Long> store = myAerospikeStore; // AerospikeStore implements ConcurrentStore<T, Long>
Optional<Versioned<TestData, Long>> current = store.getWithVersion("Id001");
if (current.isPresent()) {
    TestData mutated = current.get().value().withName("Rick Sanchez"); // your own copy-with-change
    try {
        store.checkAndUpdate(mutated, current.get().version());
    } catch (ConcurrentUpdateException e) {
        // someone else changed it first; re-read via getWithVersion and retry
    }
}
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

#### File Store

A simple file backed `ReferenceExtendedStore` (since 1.3.0). All items are serialized to JSON and persisted to a single
backing file on disk. It also implements `ReferenceExtendedStore`, so reference based lookups (`getByRefId`) are
supported through an in-file index.

The full dataset is loaded into memory once at construction, so reads are served entirely from memory. Each mutation is
applied to a copy, persisted to disk, and only then swapped in as the live view — so the in-memory view never diverges
from the file, even if a write fails. It is thread-safe (a `ReadWriteLock` lets reads run concurrently while mutations
are exclusive) and writes are atomic (data is written to a temporary file and then moved into place), so the on-disk
file never ends up half-written.

> [!NOTE]
> The entire dataset is held in memory and the whole file is rewritten on every mutation. This makes the File Store
> best suited for **small datasets, local development, tests and tooling**, rather than high throughput or large scale
> production workloads. It is also **single-process only** — it does no cross-process/cross-instance coordination, so
> use a single instance per file. For anything beyond this, prefer the Aerospike Store.

Usage:

```java
ObjectMapper mapper = new ObjectMapper();
FileStore<TestData> store = new FileStore<>(mapper, Path.of("data/store.json"), TestData.class);

// generic types are supported via a TypeReference
FileStore<Profile<TestData>> genericStore =
        new FileStore<>(mapper, Path.of("data/profiles.json"), new TypeReference<>() {});

store.create(new TestData("Id001", "Rick", 47));
store.create(new TestData("Id002", "Morty", 14), List.of("animated"));
store.update(new TestData("Id001", "Rick Sanchez", 48));
Optional<TestData> result = store.get("Id001");
List<TestData> byRef = store.getByRefId("animated");
Map<String, TestData> bulk = store.get(List.of("Id001", "Id002"));
List<TestData> all = store.list();
store.delete("Id001");
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

Or, for the file backed crud-store.

```xml

<dependency>
    <groupId>com.livetheoogway.crudstore</groupId>
    <artifactId>file-crud-store</artifactId>
    <version>${crudstore.version}</version> <!--look for the latest version on top-->
</dependency>
```

> [!NOTE]
> `file-crud-store` marks `jackson-databind` as `provided`, so make sure a Jackson version is available on your
> classpath (most applications already bring one in).

## Tech Dependencies

- Java 17
- Aerospike (optional, any provided version should do)
- Jackson (for the file-crud-store, `provided` scope)

## Contributions

Please raise Issues, Bugs or any feature requests at [Github Issues](https://github.com/livetheoogway/crud-store/issues). <br>
If you plan on contributing to the code, fork the repository and raise a Pull Request back here.
