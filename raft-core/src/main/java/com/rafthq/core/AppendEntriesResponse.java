package com.rafthq.core;

public class AppendEntriesResponse {
    public final int term;
    public final boolean success;
    public final int matchIndex;

    public AppendEntriesResponse(int term, boolean success, int matchIndex) {
        this.term = term;
        this.success = success;
        this.matchIndex = matchIndex;
    }
}
