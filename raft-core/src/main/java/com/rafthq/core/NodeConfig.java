package com.rafthq.core;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

/**
 * Loads node configuration from a .properties file.
 */
public class NodeConfig {
    private final String nodeId;
    private final String host;
    private final int port;
    private final List<String> peers; // host:port entries
    private final int electionTimeoutMinMs;
    private final int electionTimeoutMaxMs;
    private final int heartbeatIntervalMs;
    private final String storageDir;
    private final int logFlushBatch;
    private final String logLevel;

    public NodeConfig(String nodeId, String host, int port, List<String> peers,
                      int electionTimeoutMinMs, int electionTimeoutMaxMs,
                      int heartbeatIntervalMs, String storageDir,
                      int logFlushBatch, String logLevel) {
        this.nodeId = nodeId;
        this.host = host;
        this.port = port;
        this.peers = peers;
        this.electionTimeoutMinMs = electionTimeoutMinMs;
        this.electionTimeoutMaxMs = electionTimeoutMaxMs;
        this.heartbeatIntervalMs = heartbeatIntervalMs;
        this.storageDir = storageDir;
        this.logFlushBatch = logFlushBatch;
        this.logLevel = logLevel;
    }

    public static NodeConfig fromFile(String path) throws IOException {
        Properties props = new Properties();
        try (FileInputStream fis = new FileInputStream(path)) {
            props.load(fis);
        }
        String nodeId = required(props, "node.id");
        String host = required(props, "node.host");
        int port = Integer.parseInt(required(props, "node.port"));
        String peersRaw = props.getProperty("peers", "");
        List<String> peers = peersRaw.isBlank()
                ? List.of()
                : Arrays.asList(peersRaw.split(","));
        int electionMin = Integer.parseInt(props.getProperty("election.timeout.min.ms", "250"));
        int electionMax = Integer.parseInt(props.getProperty("election.timeout.max.ms", "500"));
        int heartbeat = Integer.parseInt(props.getProperty("heartbeat.interval.ms", "100"));
        String storageDir = props.getProperty("storage.dir", "./data/" + nodeId);
        int flushBatch = Integer.parseInt(props.getProperty("log.flush.batch", "16"));
        String logLevel = props.getProperty("log.level", "INFO");

        return new NodeConfig(nodeId, host, port, peers,
                electionMin, electionMax, heartbeat, storageDir,
                flushBatch, logLevel);
    }

    private static String required(Properties props, String key) {
        String value = props.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing required property: " + key);
        }
        return value.trim();
    }

    public String getNodeId() {
        return nodeId;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public List<String> getPeers() {
        return peers;
    }

    public int getElectionTimeoutMinMs() {
        return electionTimeoutMinMs;
    }

    public int getElectionTimeoutMaxMs() {
        return electionTimeoutMaxMs;
    }

    public int getHeartbeatIntervalMs() {
        return heartbeatIntervalMs;
    }

    public String getStorageDir() {
        return storageDir;
    }

    public int getLogFlushBatch() {
        return logFlushBatch;
    }

    public String getLogLevel() {
        return logLevel;
    }
}
