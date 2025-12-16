package com.rafthq.core;

import java.util.List;

public class AppendEntriesRequest {
    public final int term;
    public final String leaderId;
    public final int prevLogIndex;
    public final int prevLogTerm;
    public final int leaderCommit;
    public final List<RaftLogEntry> entries;

    public AppendEntriesRequest(int term, String leaderId, int prevLogIndex, int prevLogTerm,
                                int leaderCommit, List<RaftLogEntry> entries) {
        this.term = term;
        this.leaderId = leaderId;
        this.prevLogIndex = prevLogIndex;
        this.prevLogTerm = prevLogTerm;
        this.leaderCommit = leaderCommit;
        this.entries = entries;
    }
}
