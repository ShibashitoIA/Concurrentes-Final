package com.rafthq.core;

public class RequestVoteResponse {
    public final int term;
    public final boolean voteGranted;

    public RequestVoteResponse(int term, boolean voteGranted) {
        this.term = term;
        this.voteGranted = voteGranted;
    }
}
