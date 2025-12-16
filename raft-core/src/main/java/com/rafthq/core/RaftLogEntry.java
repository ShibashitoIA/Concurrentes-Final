package com.rafthq.core;

import java.util.Arrays;

public class RaftLogEntry {
    private final int index;
    private final int term;
    private final byte[] payload;

    public RaftLogEntry(int index, int term, byte[] payload) {
        this.index = index;
        this.term = term;
        this.payload = payload;
    }

    public int getIndex() {
        return index;
    }

    public int getTerm() {
        return term;
    }

    public byte[] getPayload() {
        return payload;
    }

    @Override
    public String toString() {
        return "RaftLogEntry{" +
                "index=" + index +
                ", term=" + term +
                ", payload.length=" + (payload != null ? payload.length : 0) +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RaftLogEntry)) return false;
        RaftLogEntry that = (RaftLogEntry) o;
        return index == that.index && term == that.term && Arrays.equals(payload, that.payload);
    }

    @Override
    public int hashCode() {
        int result = Integer.hashCode(index);
        result = 31 * result + Integer.hashCode(term);
        result = 31 * result + Arrays.hashCode(payload);
        return result;
    }
}
