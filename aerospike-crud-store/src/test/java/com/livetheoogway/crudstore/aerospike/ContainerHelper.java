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

package com.livetheoogway.crudstore.aerospike;

import com.aerospike.client.AerospikeClient;
import com.aerospike.client.Host;
import com.aerospike.client.policy.ClientPolicy;
import com.aerospike.client.policy.CommitLevel;
import com.aerospike.client.policy.Policy;
import com.aerospike.client.policy.Replica;
import com.aerospike.client.policy.WritePolicy;
import io.appform.testcontainers.aerospike.AerospikeContainerConfiguration;
import lombok.experimental.UtilityClass;
import org.testcontainers.containers.GenericContainer;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@UtilityClass
public class ContainerHelper {

    public AerospikeClient provideAerospikeClient(final GenericContainer aerospikeContainer,
                                                  final AerospikeContainerConfiguration aerospikeContainerConfig) {
        Integer mappedPort = aerospikeContainer.getMappedPort(aerospikeContainerConfig.getPort());
        String host = aerospikeContainer.getContainerIpAddress();
        List<Host> aerospikeHosts = hosts(
                Collections.singletonList(new AHost(host, mappedPort)));
        ClientPolicy clientPolicy = clientPolicy();
        return new AerospikeClient(clientPolicy, aerospikeHosts.toArray(new Host[0]));
    }


    public List<Host> hosts(final List<AHost> connections) {
        return connections.stream()
                .map(connection -> new Host(connection.host(), connection.port()))
                .collect(Collectors.toList());
    }

    public ClientPolicy clientPolicy() {
        ClientPolicy clientPolicy = new ClientPolicy();
        clientPolicy.readPolicyDefault = readPolicy();
        clientPolicy.writePolicyDefault = writePolicy();
        clientPolicy.maxConnsPerNode = 5;
        clientPolicy.threadPool = Executors
                .newFixedThreadPool(Runtime.getRuntime().availableProcessors() * 4);
        clientPolicy.failIfNotConnected = true;
        return clientPolicy;
    }

    private Policy readPolicy() {
        Policy readPolicy = new Policy();
        readPolicy.maxRetries = 2;
        readPolicy.sleepBetweenRetries = 100;
        readPolicy.sendKey = true;
        readPolicy.replica = Replica.MASTER_PROLES;
        return readPolicy;
    }

    private WritePolicy writePolicy() {
        WritePolicy writePolicy = new WritePolicy();
        writePolicy.maxRetries = 2;
        writePolicy.sleepBetweenRetries = 100;
        writePolicy.commitLevel = CommitLevel.COMMIT_ALL;
        writePolicy.sendKey = true;
        writePolicy.replica = Replica.MASTER_PROLES;
        return writePolicy;
    }

    record AHost(String host, int port) {}
}
