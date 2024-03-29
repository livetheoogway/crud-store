/*
 * Copyright 2022. Live the Oogway, Tushar Naik
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is
 * distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and limitations
 * under the License.
 */

package com.livetheoogway.crudstore.aerospike.util;

import com.livetheoogway.crudstore.aerospike.data.IdWithRefs;
import com.livetheoogway.crudstore.aerospike.data.ProfileData;
import com.livetheoogway.crudstore.aerospike.data.UserData;
import lombok.experimental.UtilityClass;

import java.util.Random;

@UtilityClass
public class DataUtils {
    private static final Random RANDOM = new Random(System.currentTimeMillis());

    public IdWithRefs<UserData> generateTestData() {
        return generateTestData("1", "me", RANDOM.nextInt(100));
    }

    public IdWithRefs<UserData> generateTestData(String id) {
        return generateTestData(id, "me too", RANDOM.nextInt(100));
    }

    public IdWithRefs<ProfileData<UserData>> generateProfileData() {
        return IdWithRefs.of(new ProfileData<>("1", generateTestData("1", "me", RANDOM.nextInt(100)).item()));
    }

    public IdWithRefs<ProfileData<UserData>> generateProfileData(String id) {
        return IdWithRefs.of(new ProfileData<>(id, generateTestData(id, "me too", RANDOM.nextInt(100)).item()));
    }

    public IdWithRefs<UserData> generateTestData(String id, String name, int age) {
        return IdWithRefs.of(new UserData(id, name, age));
    }
}
