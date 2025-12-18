package com.rafthq.core;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Simple persistence layer for Raft state.
 * Stores: currentTerm, votedFor, log entries
 * Format: JSON-like text files (one per field/log)
 */
public class PersistentState {
    private static final Logger LOG = Logger.getLogger(PersistentState.class.getName());
    
    private final Path storageDir;
    private final Path termFile;
    private final Path votedForFile;
    private final Path logFile;
    private final Path lastAppliedFile;

    public PersistentState(String storageDirPath) throws IOException {
        this.storageDir = Paths.get(storageDirPath);
        Files.createDirectories(storageDir);
        
        this.termFile = storageDir.resolve("term.txt");
        this.votedForFile = storageDir.resolve("votedFor.txt");
        this.logFile = storageDir.resolve("log.txt");
        this.lastAppliedFile = storageDir.resolve("lastApplied.txt");
    }

    public int loadTerm() {
        try {
            if (Files.exists(termFile)) {
                String content = Files.readString(termFile, StandardCharsets.UTF_8).trim();
                return Integer.parseInt(content);
            }
        } catch (IOException | NumberFormatException e) {
            LOG.warning("Failed to load term: " + e.getMessage());
        }
        return 0;
    }

    public void saveTerm(int term) {
        try {
            Files.writeString(termFile, String.valueOf(term), StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOG.severe("Failed to save term: " + e.getMessage());
        }
    }

    public String loadVotedFor() {
        try {
            if (Files.exists(votedForFile)) {
                return Files.readString(votedForFile, StandardCharsets.UTF_8).trim();
            }
        } catch (IOException e) {
            LOG.warning("Failed to load votedFor: " + e.getMessage());
        }
        return null;
    }

    public void saveVotedFor(String candidateId) {
        try {
            if (candidateId == null) {
                Files.deleteIfExists(votedForFile);
            } else {
                Files.writeString(votedForFile, candidateId, StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            LOG.severe("Failed to save votedFor: " + e.getMessage());
        }
    }

    public int loadLastApplied() {
        try {
            if (Files.exists(lastAppliedFile)) {
                String content = Files.readString(lastAppliedFile, StandardCharsets.UTF_8).trim();
                return Integer.parseInt(content);
            }
        } catch (IOException | NumberFormatException e) {
            LOG.warning("Failed to load lastApplied: " + e.getMessage());
        }
        return 0;
    }

    public void saveLastApplied(int lastApplied) {
        try {
            Files.writeString(lastAppliedFile, String.valueOf(lastApplied), StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOG.severe("Failed to save lastApplied: " + e.getMessage());
        }
    }

    public List<RaftLogEntry> loadLog() {
        List<RaftLogEntry> entries = new ArrayList<>();
        try {
            if (Files.exists(logFile)) {
                List<String> lines = Files.readAllLines(logFile, StandardCharsets.UTF_8);
                for (String line : lines) {
                    if (line.trim().isEmpty()) continue;
                    // Format: index,term,base64_payload
                    String[] parts = line.split(",", 3);
                    int index = Integer.parseInt(parts[0]);
                    int term = Integer.parseInt(parts[1]);
                    byte[] payload = java.util.Base64.getDecoder().decode(parts[2]);
                    entries.add(new RaftLogEntry(index, term, payload));
                }
            }
        } catch (IOException e) {
            LOG.severe("Failed to load log: " + e.getMessage());
        }
        return entries;
    }

    public void appendLogEntry(RaftLogEntry entry) {
        try (BufferedWriter writer = Files.newBufferedWriter(logFile, 
                StandardCharsets.UTF_8,
                java.nio.file.StandardOpenOption.CREATE,
                java.nio.file.StandardOpenOption.APPEND)) {
            String payload64 = java.util.Base64.getEncoder().encodeToString(entry.getPayload());
            writer.write(entry.getIndex() + "," + entry.getTerm() + "," + payload64);
            writer.newLine();
        } catch (IOException e) {
            LOG.severe("Failed to append log entry: " + e.getMessage());
        }
    }

    public void truncateLog(int fromIndex) {
        try {
            List<RaftLogEntry> entries = loadLog();
            try (BufferedWriter writer = Files.newBufferedWriter(logFile, StandardCharsets.UTF_8)) {
                for (RaftLogEntry entry : entries) {
                    if (entry.getIndex() < fromIndex) {
                        String payload64 = java.util.Base64.getEncoder().encodeToString(entry.getPayload());
                        writer.write(entry.getIndex() + "," + entry.getTerm() + "," + payload64);
                        writer.newLine();
                    }
                }
            }
        } catch (IOException e) {
            LOG.severe("Failed to truncate log: " + e.getMessage());
        }
    }
}
