package com.livetheoogway.crudstore.aerospike;

import lombok.experimental.UtilityClass;

import java.util.Random;

@UtilityClass
public class DataUtils {
    private static final Random RANDOM = new Random(System.currentTimeMillis());

    public TestData generateTestData() {
        return generateTestData("1", "me", RANDOM.nextInt(100));
    }

    public TestData generateTestData(String id) {
        return generateTestData(id, "me too", RANDOM.nextInt(100));
    }

    public TestData generateTestData(String id, String name, int age) {
        return new TestData(id, name, age);
    }
}
