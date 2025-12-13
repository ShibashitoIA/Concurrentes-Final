package com.rafthq.core;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Core RAFT node orchestration. Methods are stubs to be completed in later steps.
 */
public class RaftNode {
    private static final Logger LOG = Logger.getLogger(RaftNode.class.getName());

    private final NodeConfig config;
    private final RaftLog log;
    private final StateMachine stateMachine;
    private final RpcServer rpcServer;
    private final RpcClient rpcClient;

    private volatile RaftState state = RaftState.FOLLOWER;
    private final AtomicInteger currentTerm = new AtomicInteger(0);
    private volatile String votedFor = null;
    private volatile int commitIndex = 0;
    private volatile int lastApplied = 0;

    private final Map<String, Integer> nextIndex = new ConcurrentHashMap<>();
    private final Map<String, Integer> matchIndex = new ConcurrentHashMap<>();

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private final ExecutorService senderPool = Executors.newCachedThreadPool();

    public RaftNode(NodeConfig config, StateMachine stateMachine) throws IOException {
        this.config = config;
        this.log = new RaftLog();
        this.stateMachine = stateMachine;
        this.rpcServer = new RpcServer(config.getHost(), config.getPort(), this::handleMessage);
        this.rpcClient = new RpcClient();
    }

    public void start() {
        rpcServer.start();
        scheduleElectionTimer();
        LOG.info(() -> "Node started as FOLLOWER on " + config.getHost() + ":" + config.getPort());
    }

    public synchronized boolean appendCommand(byte[] command) {
        if (state != RaftState.LEADER) {
            LOG.fine("Reject appendCommand: not leader");
            return false;
        }
        int index = log.lastIndex() + 1;
        int term = currentTerm.get();
        log.append(new RaftLogEntry(index, term, command));
        // TODO: trigger replication to peers
        return true;
    }

    private void scheduleElectionTimer() {
        int delay = randomElectionTimeout();
        scheduler.schedule(this::onElectionTimeout, delay, TimeUnit.MILLISECONDS);
    }

    private void onElectionTimeout() {
        if (state == RaftState.LEADER) {
            scheduleElectionTimer();
            return;
        }
        becomeCandidate();
        startElection();
        scheduleElectionTimer();
    }

    private void becomeCandidate() {
        state = RaftState.CANDIDATE;
        int term = currentTerm.incrementAndGet();
        votedFor = config.getNodeId();
        LOG.info(() -> "Became CANDIDATE term " + term);
    }

    private void startElection() {
        RequestVoteRequest req = new RequestVoteRequest(
                currentTerm.get(),
                config.getNodeId(),
                log.lastIndex(),
                log.lastTerm()
        );
        List<String> peers = config.getPeers();
        AtomicInteger votes = new AtomicInteger(1); // self vote
        for (String peer : peers) {
            senderPool.submit(() -> {
                try {
                    RequestVoteResponse resp = rpcClient.requestVote(peer, req);
                    if (resp == null) return;
                    if (resp.term > currentTerm.get()) {
                        stepDown(resp.term);
                        return;
                    }
                    if (resp.voteGranted) {
                        int granted = votes.incrementAndGet();
                        if (granted > peers.size() / 2 && state == RaftState.CANDIDATE) {
                            becomeLeader();
                        }
                    }
                } catch (Exception e) {
                    LOG.log(Level.FINE, "Vote request failed to peer " + peer, e);
                }
            });
        }
    }

    private void becomeLeader() {
        state = RaftState.LEADER;
        LOG.info(() -> "Became LEADER term " + currentTerm.get());
        for (String peer : config.getPeers()) {
            nextIndex.put(peer, log.lastIndex() + 1);
            matchIndex.put(peer, 0);
        }
        scheduler.scheduleAtFixedRate(this::sendHeartbeats,
                0,
                config.getHeartbeatIntervalMs(),
                TimeUnit.MILLISECONDS);
    }

    private void stepDown(int newTerm) {
        state = RaftState.FOLLOWER;
        currentTerm.set(newTerm);
        votedFor = null;
        LOG.info(() -> "Step down to FOLLOWER term " + newTerm);
    }

    private void sendHeartbeats() {
        if (state != RaftState.LEADER) return;
        AppendEntriesRequest req = new AppendEntriesRequest(
                currentTerm.get(),
                config.getNodeId(),
                log.lastIndex(),
                log.lastTerm(),
                commitIndex,
                List.of()
        );
        for (String peer : config.getPeers()) {
            senderPool.submit(() -> {
                try {
                    AppendEntriesResponse resp = rpcClient.appendEntries(peer, req);
                    if (resp == null) return;
                    if (resp.term > currentTerm.get()) {
                        stepDown(resp.term);
                    }
                } catch (Exception e) {
                    LOG.log(Level.FINE, "Heartbeat failed to peer " + peer, e);
                }
            });
        }
    }

    private String handleMessage(String message) {
        // TODO: parse and dispatch RequestVote/AppendEntries messages
        LOG.fine(() -> "Received raw message: " + message);
        return "OK"; // placeholder
    }
}
