package com.rafthq.core;

public class RequestVoteRequest {
    public final int term;
    public final String candidateId;
    public final int lastLogIndex;
    public final int lastLogTerm;

    public RequestVoteRequest(int term, String candidateId, int lastLogIndex, int lastLogTerm) {
        this.term = term;
        this.candidateId = candidateId;
        this.lastLogIndex = lastLogIndex;
        this.lastLogTerm = lastLogTerm;
    }
}
