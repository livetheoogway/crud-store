package com.livetheoogway.crudstore.aerospike.util;

import com.livetheoogway.crudstore.aerospike.data.ProfileData;
import com.livetheoogway.crudstore.aerospike.data.UserData;
import lombok.experimental.UtilityClass;

import java.util.Random;

@UtilityClass
public class DataUtils {
    private static final Random RANDOM = new Random(System.currentTimeMillis());

    public UserData generateTestData() {
        return generateTestData("1", "me", RANDOM.nextInt(100));
    }

    public UserData generateTestData(String id) {
        return generateTestData(id, "me too", RANDOM.nextInt(100));
    }

    public ProfileData<UserData> generateProfileData() {
        return new ProfileData<>("1", generateTestData("1", "me", RANDOM.nextInt(100)));
    }

    public ProfileData<UserData> generateProfileData(String id) {
        return new ProfileData<>(id, generateTestData(id, "me too", RANDOM.nextInt(100)));
    }

    public UserData generateTestData(String id, String name, int age) {
        return new UserData(id, name, age);
    }
}
