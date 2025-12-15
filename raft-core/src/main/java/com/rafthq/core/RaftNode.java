package com.rafthq.core;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Core RAFT node: orchestrates election, replication, and applies committed entries.
 * Thread-safe state management via locks and volatile variables.
 */
public class RaftNode {
    private static final Logger LOG = Logger.getLogger(RaftNode.class.getName());
    private static final Random RANDOM = new Random();

    private final NodeConfig config;
    private final RaftLog log;
    private final StateMachine stateMachine;
    private final RpcServer rpcServer;
    private final RpcClient rpcClient;
    private final PersistentState persistence;

    // Persistent state (thread-safe access)
    private volatile RaftState state = RaftState.FOLLOWER;
    private final AtomicInteger currentTerm = new AtomicInteger(0);
    private volatile String votedFor = null;

    // Volatile state
    private volatile int commitIndex = 0;
    private volatile int lastApplied = 0;

    // Leader state (only meaningful when state == LEADER)
    private final Map<String, Integer> nextIndex = new ConcurrentHashMap<>();
    private final Map<String, Integer> matchIndex = new ConcurrentHashMap<>();

    // Concurrency
    private final Object stateLock = new Object();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private final ExecutorService senderPool = Executors.newCachedThreadPool();

    private ScheduledFuture<?> electionTimerTask;
    private ScheduledFuture<?> heartbeatTask;

    public RaftNode(NodeConfig config, StateMachine stateMachine) throws IOException {
        this.config = config;
        this.log = new RaftLog();
        this.stateMachine = stateMachine;
        this.rpcServer = new RpcServer(config.getHost(), config.getPort(), this::handleMessage);
        this.rpcClient = new RpcClient();
        this.persistence = new PersistentState(config.getStorageDir());
        loadPersistentState();
    }

    public void start() {
        rpcServer.start();
        scheduleElectionTimer();
        startApplyLoop();
        LOG.info(() -> "Node " + config.getNodeId() + " started as FOLLOWER on " +
                config.getHost() + ":" + config.getPort() + " (term=" + currentTerm.get() + ")");
    }
    
    private void loadPersistentState() {
        try {
            int loadedTerm = persistence.loadTerm();
            currentTerm.set(loadedTerm);
            String loadedVotedFor = persistence.loadVotedFor();
            votedFor = loadedVotedFor;
            List<RaftLogEntry> entries = persistence.loadLog();
            for (RaftLogEntry entry : entries) {
                log.append(entry);
            }
            if (loadedTerm > 0 || !entries.isEmpty()) {
                LOG.info(() -> "Loaded: term=" + loadedTerm + " votedFor=" + loadedVotedFor + " entries=" + entries.size());
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to load persistent state", e);
        }
    }
    
    private void savePersistentState() {
        try {
            persistence.saveTerm(currentTerm.get());
            persistence.saveVotedFor(votedFor);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to save persistent state", e);
        }
    }

    /**
     * API: append a command to the log (only accepted if leader).
     * @return true if command was appended, false if not leader.
     */
    public boolean appendCommand(byte[] command) {
        synchronized (stateLock) {
            if (state != RaftState.LEADER) {
                LOG.fine("Reject appendCommand: not leader (current state: " + state + ")");
                return false;
            }
            int index = log.lastIndex() + 1;
            int term = currentTerm.get();
            RaftLogEntry entry = new RaftLogEntry(index, term, command);
            log.append(entry);
            persistence.appendLogEntry(entry);
            LOG.fine(() -> "Appended command at index " + index + " term " + term);
            return true;
        }
    }

    public RaftState getState() {
        return state;
    }

    public int getCurrentTerm() {
        return currentTerm.get();
    }

    public String getVotedFor() {
        return votedFor;
    }

    public int getCommitIndex() {
        return commitIndex;
    }

    // ============ Election Timer ============
    private void scheduleElectionTimer() {
        if (electionTimerTask != null) {
            electionTimerTask.cancel(false);
        }
        int delay = randomElectionTimeout();
        electionTimerTask = scheduler.schedule(this::onElectionTimeout, delay, TimeUnit.MILLISECONDS);
    }

    private int randomElectionTimeout() {
        int min = config.getElectionTimeoutMinMs();
        int max = config.getElectionTimeoutMaxMs();
        return min + RANDOM.nextInt(max - min + 1);
    }

    private void onElectionTimeout() {
        synchronized (stateLock) {
            if (state == RaftState.LEADER) {
                // Leader doesn't participate in elections
                scheduleElectionTimer();
                return;
            }
            LOG.info(() -> "Election timeout in state " + state);
            becomeCandidate();
        }
        startElection();
        scheduleElectionTimer();
    }

    // ============ State Transitions ============
    private void becomeCandidate() {
        state = RaftState.CANDIDATE;
        int term = currentTerm.incrementAndGet();
        votedFor = config.getNodeId();
        savePersistentState();
        LOG.info(() -> "Node " + config.getNodeId() + " became CANDIDATE term " + term);
    }

    private void becomeLeader() {
        synchronized (stateLock) {
            if (state != RaftState.CANDIDATE) {
                // Race condition: already stepped down
                return;
            }
            state = RaftState.LEADER;
            LOG.info(() -> "Node " + config.getNodeId() + " became LEADER term " + currentTerm.get());

            // Initialize leader state
            int lastIdx = log.lastIndex();
            for (String peer : config.getPeers()) {
                nextIndex.put(peer, lastIdx + 1);
                matchIndex.put(peer, 0);
            }

            // Cancel election timer, start heartbeat
            if (electionTimerTask != null) {
                electionTimerTask.cancel(false);
            }
            startHeartbeats();
        }
    }

    private void stepDown(int newTerm) {
        synchronized (stateLock) {
            if (newTerm > currentTerm.get()) {
                state = RaftState.FOLLOWER;
                currentTerm.set(newTerm);
                votedFor = null;
                savePersistentState();
                LOG.info(() -> "Node " + config.getNodeId() + " stepped down to FOLLOWER term " + newTerm);
                scheduleElectionTimer();
            }
        }
    }

    // ============ Election (RequestVote) ============
    private void startElection() {
        int term = currentTerm.get();
        RequestVoteRequest req = new RequestVoteRequest(
                term,
                config.getNodeId(),
                log.lastIndex(),
                log.lastTerm()
        );
        List<String> peers = config.getPeers();
        AtomicInteger votes = new AtomicInteger(1); // self vote
        int required = peers.size() / 2 + 1;

        for (String peer : peers) {
            senderPool.submit(() -> {
                try {
                    RequestVoteResponse resp = rpcClient.requestVote(peer, req);
                    if (resp == null) {
                        LOG.fine(() -> "No response from peer " + peer);
                        return;
                    }
                    if (resp.term > currentTerm.get()) {
                        stepDown(resp.term);
                        return;
                    }
                    if (resp.voteGranted) {
                        int granted = votes.incrementAndGet();
                        LOG.fine(() -> "Vote granted from " + peer + " (" + granted + "/" + required + ")");
                        if (granted >= required && state == RaftState.CANDIDATE) {
                            becomeLeader();
                        }
                    } else {
                        LOG.fine(() -> "Vote denied from " + peer);
                    }
                } catch (Exception e) {
                    LOG.log(Level.FINE, "RequestVote RPC failed to " + peer, e);
                }
            });
        }
    }

    // ============ RPC Handlers ============
    /**
     * Handle incoming RequestVote RPC and return response.
     */
    private RequestVoteResponse handleRequestVote(RequestVoteRequest req) {
        synchronized (stateLock) {
            if (req.term > currentTerm.get()) {
                stepDown(req.term);
            }
            int myTerm = currentTerm.get();
            final boolean grant;

            if (req.term < myTerm) {
                // Stale request
                grant = false;
            } else if (req.term == myTerm) {
                // Grant vote if we haven't voted yet or voted for this candidate
                if (votedFor == null || votedFor.equals(req.candidateId)) {
                    // Check log is up-to-date
                    if (req.lastLogTerm > log.lastTerm() ||
                            (req.lastLogTerm == log.lastTerm() && req.lastLogIndex >= log.lastIndex())) {
                        votedFor = req.candidateId;
                        grant = true;
                    } else {
                        grant = false;
                    }
                } else {
                    grant = false;
                }
            } else {
                grant = false;
            }

            final boolean granted = grant;
            LOG.fine(() -> "RequestVote from " + req.candidateId + " term " + req.term +
                    " -> " + (granted ? "GRANTED" : "DENIED"));
            return new RequestVoteResponse(myTerm, grant);
        }
    }

    /**
     * Handle incoming AppendEntries RPC and return response.
     */
    private AppendEntriesResponse handleAppendEntries(AppendEntriesRequest req) {
        synchronized (stateLock) {
            if (req.term > currentTerm.get()) {
                stepDown(req.term);
            } else if (req.term < currentTerm.get()) {
                return new AppendEntriesResponse(currentTerm.get(), false, 0);
            }

            // Heartbeat: reset election timer even if no entries
            scheduleElectionTimer();

            // Log match check
            if (req.prevLogIndex > 0) {
                var entry = log.get(req.prevLogIndex);
                if (entry.isEmpty() || entry.get().getTerm() != req.prevLogTerm) {
                    LOG.fine(() -> "Log mismatch at index " + req.prevLogIndex);
                    return new AppendEntriesResponse(currentTerm.get(), false, log.lastIndex());
                }
            }

            // Truncate and append entries
            int lastNewIndex = req.prevLogIndex;
            if (!req.entries.isEmpty()) {
                // Conflict detection: truncate from first conflicting entry
                for (RaftLogEntry e : req.entries) {
                    var existing = log.get(e.getIndex());
                    if (existing.isPresent() && existing.get().getTerm() != e.getTerm()) {
                        log.truncateFrom(e.getIndex());
                        persistence.truncateLog(e.getIndex());
                    }
                    if (!existing.isPresent()) {
                        log.append(e);
                        persistence.appendLogEntry(e);
                        lastNewIndex = e.getIndex();
                    }
                }
            }

            // Update commitIndex
            if (req.leaderCommit > commitIndex) {
                commitIndex = Math.min(req.leaderCommit, log.lastIndex());
            }

            return new AppendEntriesResponse(currentTerm.get(), true, lastNewIndex);
        }
    }

    private String handleMessage(String message) {
        try {
            if (message.startsWith("REQUEST_VOTE")) {
                RequestVoteRequest req = MessageCodec.decodeRequestVote(message);
                RequestVoteResponse resp = handleRequestVote(req);
                return MessageCodec.encodeRequestVoteResponse(resp);
            } else if (message.startsWith("APPEND_ENTRIES")) {
                AppendEntriesRequest req = MessageCodec.decodeAppendEntries(message);
                AppendEntriesResponse resp = handleAppendEntries(req);
                return MessageCodec.encodeAppendEntriesResponse(resp);
            } else {
                LOG.warning("Unknown message type: " + message);
                return "ERROR";
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Error handling message: " + message, e);
            return "ERROR";
        }
    }

    // ============ Heartbeats (Leader) ============
    private void startHeartbeats() {
        if (heartbeatTask != null) {
            heartbeatTask.cancel(false);
        }
        heartbeatTask = scheduler.scheduleAtFixedRate(this::sendHeartbeats,
                0,
                config.getHeartbeatIntervalMs(),
                TimeUnit.MILLISECONDS);
    }

    private void sendHeartbeats() {
        if (state != RaftState.LEADER) {
            return;
        }
        for (String peer : config.getPeers()) {
            senderPool.submit(() -> sendHeartbeatToPeer(peer));
        }
    }

    private void sendHeartbeatToPeer(String peer) {
        int nextIdx = nextIndex.getOrDefault(peer, log.lastIndex() + 1);
        int prevLogIdx = nextIdx - 1;
        int prevLogTerm = 0;

        var prevEntry = log.get(prevLogIdx);
        if (prevEntry.isPresent()) {
            prevLogTerm = prevEntry.get().getTerm();
        }

        List<RaftLogEntry> entries = log.sliceFrom(nextIdx);
        AppendEntriesRequest req = new AppendEntriesRequest(
                currentTerm.get(),
                config.getNodeId(),
                prevLogIdx,
                prevLogTerm,
                commitIndex,
                entries
        );

        try {
            AppendEntriesResponse resp = rpcClient.appendEntries(peer, req);
            if (resp == null) {
                return;
            }

            if (resp.term > currentTerm.get()) {
                stepDown(resp.term);
                return;
            }

            if (resp.success) {
                nextIndex.put(peer, Math.max(nextIndex.getOrDefault(peer, 0), resp.matchIndex + 1));
                matchIndex.put(peer, resp.matchIndex);
                advanceCommitIndex();
            } else {
                int idx = nextIndex.getOrDefault(peer, log.lastIndex() + 1);
                if (idx > 0) {
                    nextIndex.put(peer, idx - 1);
                }
            }
        } catch (Exception e) {
            LOG.log(Level.FINE, "Heartbeat RPC failed to " + peer, e);
        }
    }

    // ============ Commit and Apply ============
    /**
     * Start background thread that applies committed entries to state machine.
     */
    private void startApplyLoop() {
        scheduler.scheduleAtFixedRate(() -> {
            while (commitIndex > lastApplied) {
                lastApplied++;
                var entry = log.get(lastApplied);
                if (entry.isPresent()) {
                    byte[] command = entry.get().getPayload();
                    try {
                        stateMachine.onCommit(command);
                        LOG.fine(() -> "Applied entry " + lastApplied + " to state machine");
                    } catch (Exception e) {
                        LOG.log(Level.WARNING, "State machine error at index " + lastApplied, e);
                    }
                }
            }
        }, 100, 50, TimeUnit.MILLISECONDS);
    }

    /**
     * Leader: advance commitIndex if majority of peers have replicated up to N.
     * Called after updating matchIndex.
     */
    private void advanceCommitIndex() {
        if (state != RaftState.LEADER) {
            return;
        }

        int lastIdx = log.lastIndex();
        for (int n = commitIndex + 1; n <= lastIdx; n++) {
            final int idx = n;
            var entry = log.get(idx);
            if (entry.isEmpty() || entry.get().getTerm() != currentTerm.get()) {
                continue;
            }

            // Count replicas (self + peers that have matchIndex >= n)
            int count = 1; // self
            for (String peer : config.getPeers()) {
                if (matchIndex.getOrDefault(peer, 0) >= idx) {
                    count++;
                }
            }

            int majority = (config.getPeers().size() + 1) / 2 + 1;
            if (count >= majority) {
                commitIndex = idx;
                LOG.fine(() -> "Advanced commitIndex to " + idx);
            }
        }
    }
}
