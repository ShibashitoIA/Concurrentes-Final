package com.rafthq.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Thread-safe in-memory log with hooks for persistence.
 */
public class RaftLog {
    private final List<RaftLogEntry> entries = new ArrayList<>();
    private final Lock lock = new ReentrantLock();

    // The log is 1-indexed; index 0 is treated as a dummy for simplicity.
    public RaftLog() {
        entries.add(new RaftLogEntry(0, 0, new byte[0]));
    }

    public int lastIndex() {
        lock.lock();
        try {
            return entries.get(entries.size() - 1).getIndex();
        } finally {
            lock.unlock();
        }
    }

    public int lastTerm() {
        lock.lock();
        try {
            return entries.get(entries.size() - 1).getTerm();
        } finally {
            lock.unlock();
        }
    }

    public Optional<RaftLogEntry> get(int index) {
        lock.lock();
        try {
            if (index < 0 || index >= entries.size()) {
                return Optional.empty();
            }
            return Optional.of(entries.get(index));
        } finally {
            lock.unlock();
        }
    }

    public List<RaftLogEntry> sliceFrom(int startIndex) {
        lock.lock();
        try {
            if (startIndex >= entries.size()) {
                return List.of();
            }
            return new ArrayList<>(entries.subList(startIndex, entries.size()));
        } finally {
            lock.unlock();
        }
    }

    public void append(RaftLogEntry entry) {
        lock.lock();
        try {
            entries.add(entry);
        } finally {
            lock.unlock();
        }
    }

    public void truncateFrom(int startIndex) {
        lock.lock();
        try {
            if (startIndex < entries.size()) {
                entries.subList(startIndex, entries.size()).clear();
            }
        } finally {
            lock.unlock();
        }
    }
}
