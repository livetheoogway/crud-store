package com.livetheoogway.crudstore.aerospike;

import com.aerospike.client.AerospikeClient;
import com.aerospike.client.Host;
import com.aerospike.client.IAerospikeClient;
import com.aerospike.client.Language;
import com.aerospike.client.policy.ClientPolicy;
import com.aerospike.client.policy.CommitLevel;
import com.aerospike.client.policy.Policy;
import com.aerospike.client.policy.ReadModeAP;
import com.aerospike.client.policy.Replica;
import com.aerospike.client.policy.ScanPolicy;
import com.aerospike.client.policy.TlsPolicy;
import com.aerospike.client.policy.WritePolicy;
import com.aerospike.client.task.RegisterTask;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;
import java.util.concurrent.Executors;

@Slf4j
@UtilityClass
public class AerospikeClientHelpers {

    public IAerospikeClient aerospikeClient(AerospikeConfiguration config) {

        final var connectionString = config.hosts().trim();
        final var hosts = connectionString.split(",");

        final var readPolicy = new Policy();
        readPolicy.maxRetries = config.retries();
        readPolicy.replica = Replica.MASTER_PROLES;
        readPolicy.sleepBetweenRetries = config.sleepBetweenRetries();
        readPolicy.totalTimeout = config.timeout();
        readPolicy.sendKey = true;

        final var writePolicy = new WritePolicy();
        writePolicy.maxRetries = config.retries();
        writePolicy.replica = Replica.MASTER_PROLES;
        writePolicy.sleepBetweenRetries = config.sleepBetweenRetries();
        writePolicy.commitLevel = CommitLevel.COMMIT_ALL;
        writePolicy.totalTimeout = config.timeout();
        writePolicy.sendKey = true;
        writePolicy.expiration = -1;

        final var scanPolicy = new ScanPolicy();
        scanPolicy.maxRetries = 0;
        scanPolicy.includeBinData = true;
        scanPolicy.concurrentNodes = true;
        scanPolicy.maxConcurrentNodes = hosts.length;

        final var clientPolicy = new ClientPolicy();
        clientPolicy.maxConnsPerNode = config.maxConnectionsPerNode();
        clientPolicy.readPolicyDefault = readPolicy;
        clientPolicy.writePolicyDefault = writePolicy;
        clientPolicy.scanPolicyDefault = scanPolicy;
        clientPolicy.failIfNotConnected = true;
        clientPolicy.threadPool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() * 4);

        final var authEnabled = stringIsNotNullOrEmpty(config.user()) && stringIsNotNullOrEmpty(config.password());
        var defaultPort = authEnabled ? 4333 : 3000;
        if (config.port() > 10) {
            defaultPort = config.port();
        }

        var finalDefaultPort = defaultPort;
        final Host[] aerospikeHosts = Arrays
                .stream(hosts)
                .map(host -> {
                    String[] hostItems = host.trim().split(":");
                    if (hostItems.length == 2) {
                        return getHost(hostItems[0], Integer.parseInt(hostItems[1]), config);
                    } else {
                        return getHost(hostItems[0], finalDefaultPort, config);
                    }
                })
                .toArray(Host[]::new);

        if (authEnabled) {
            clientPolicy.user = config.user();
            clientPolicy.password = config.password();
            clientPolicy.tlsPolicy = new TlsPolicy();
        }
        return new AerospikeClient(clientPolicy, aerospikeHosts);
    }

    public void registerUDFs(final IAerospikeClient aerospikeClient,
                                    final AerospikeConfiguration aerospikeConfiguration,
                                    final String luaFilePath,
                                    final String serverPath) {
        Policy policy = new Policy();
        policy.maxRetries = aerospikeConfiguration.retries();
        policy.readModeAP = ReadModeAP.ALL;
        policy.sleepBetweenRetries = aerospikeConfiguration.sleepBetweenRetries();
        policy.setTimeout(aerospikeConfiguration.timeout());
        policy.sendKey = true;
        policy.replica = Replica.MASTER_PROLES;
        log.info("Registering UDF modules now..");
        RegisterTask task = aerospikeClient.register(policy, luaFilePath, serverPath, Language.LUA);
        task.waitTillComplete();
        log.info("Register client path {} and server path {}", luaFilePath, serverPath);
    }

    private static Host getHost(String hostname, int port, AerospikeConfiguration config) {
        if (stringIsNotNullOrEmpty(config.tlsName())) {
            return new Host(hostname, config.tlsName(), port);
        } else {
            return new Host(hostname, port);
        }
    }

    private static boolean stringIsNotNullOrEmpty(String string) {
        return string != null && !string.isEmpty();
    }
}
