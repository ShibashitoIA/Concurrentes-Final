package com.rafthq.core;

import java.util.Base64;
import java.util.List;
import java.util.StringJoiner;
import java.util.stream.Collectors;

/**
 * Serialization helpers for text-based RPC messages. Implementation will be completed in next step.
 */
public class MessageCodec {
    private static final String SEP = "|";

    public static String encodeRequestVote(RequestVoteRequest req) {
        return StringJoinerBuilder.start("REQUEST_VOTE")
                .addInt(req.term)
                .add(req.candidateId)
                .addInt(req.lastLogIndex)
                .addInt(req.lastLogTerm)
                .build();
    }

    public static RequestVoteResponse decodeRequestVoteResponse(String line) {
        String[] parts = line.split("\\|");
        if (parts.length < 3 || !"REQUEST_VOTE_RESPONSE".equals(parts[0])) {
            throw new IllegalArgumentException("Invalid RequestVoteResponse: " + line);
        }
        int term = Integer.parseInt(parts[1]);
        boolean voteGranted = Boolean.parseBoolean(parts[2]);
        return new RequestVoteResponse(term, voteGranted);
    }

    public static String encodeRequestVoteResponse(RequestVoteResponse resp) {
        return StringJoinerBuilder.start("REQUEST_VOTE_RESPONSE")
                .addInt(resp.term)
                .add(Boolean.toString(resp.voteGranted))
                .build();
    }

    public static RequestVoteRequest decodeRequestVote(String line) {
        String[] parts = line.split("\\|");
        if (parts.length < 5 || !"REQUEST_VOTE".equals(parts[0])) {
            throw new IllegalArgumentException("Invalid RequestVote: " + line);
        }
        int term = Integer.parseInt(parts[1]);
        String candidateId = parts[2];
        int lastLogIndex = Integer.parseInt(parts[3]);
        int lastLogTerm = Integer.parseInt(parts[4]);
        return new RequestVoteRequest(term, candidateId, lastLogIndex, lastLogTerm);
    }

    public static String encodeAppendEntries(AppendEntriesRequest req) {
        StringJoinerBuilder builder = StringJoinerBuilder.start("APPEND_ENTRIES")
                .addInt(req.term)
                .add(req.leaderId)
                .addInt(req.prevLogIndex)
                .addInt(req.prevLogTerm)
                .addInt(req.leaderCommit)
                .addInt(req.entries.size());
        for (RaftLogEntry e : req.entries) {
            builder.add(encodeEntry(e));
        }
        return builder.build();
    }

    public static AppendEntriesRequest decodeAppendEntries(String line) {
        String[] parts = line.split("\\|");
        if (parts.length < 7 || !"APPEND_ENTRIES".equals(parts[0])) {
            throw new IllegalArgumentException("Invalid AppendEntries: " + line);
        }
        int term = Integer.parseInt(parts[1]);
        String leaderId = parts[2];
        int prevLogIndex = Integer.parseInt(parts[3]);
        int prevLogTerm = Integer.parseInt(parts[4]);
        int leaderCommit = Integer.parseInt(parts[5]);
        int entryCount = Integer.parseInt(parts[6]);
        List<RaftLogEntry> entries = List.of();
        if (entryCount > 0) {
            entries = parseEntries(parts, 7, entryCount);
        }
        return new AppendEntriesRequest(term, leaderId, prevLogIndex, prevLogTerm, leaderCommit, entries);
    }

    public static String encodeAppendEntriesResponse(AppendEntriesResponse resp) {
        return StringJoinerBuilder.start("APPEND_ENTRIES_RESPONSE")
                .addInt(resp.term)
                .add(Boolean.toString(resp.success))
                .addInt(resp.matchIndex)
                .build();
    }

    public static AppendEntriesResponse decodeAppendEntriesResponse(String line) {
        String[] parts = line.split("\\|");
        if (parts.length < 4 || !"APPEND_ENTRIES_RESPONSE".equals(parts[0])) {
            throw new IllegalArgumentException("Invalid AppendEntriesResponse: " + line);
        }
        int term = Integer.parseInt(parts[1]);
        boolean success = Boolean.parseBoolean(parts[2]);
        int matchIndex = Integer.parseInt(parts[3]);
        return new AppendEntriesResponse(term, success, matchIndex);
    }

    private static String encodeEntry(RaftLogEntry e) {
        String payload = Base64.getEncoder().encodeToString(e.getPayload());
        return e.getIndex() + "," + e.getTerm() + "," + e.getPayload().length + "," + payload;
    }

    private static List<RaftLogEntry> parseEntries(String[] parts, int start, int count) {
        return java.util.stream.IntStream.range(0, count)
                .mapToObj(i -> parts[start + i])
                .map(MessageCodec::decodeEntry)
                .collect(Collectors.toList());
    }

    private static RaftLogEntry decodeEntry(String raw) {
        String[] p = raw.split(",", 4);
        if (p.length < 4) {
            throw new IllegalArgumentException("Invalid entry: " + raw);
        }
        int index = Integer.parseInt(p[0]);
        int term = Integer.parseInt(p[1]);
        // length field p[2] is ignored; payload decoded from p[3]
        byte[] payload = Base64.getDecoder().decode(p[3]);
        return new RaftLogEntry(index, term, payload);
    }

    private static class StringJoinerBuilder {
        private final StringJoiner joiner;

        private StringJoinerBuilder(String head) {
            this.joiner = new StringJoiner(SEP);
            this.joiner.add(head);
        }

        static StringJoinerBuilder start(String head) {
            return new StringJoinerBuilder(head);
        }

        StringJoinerBuilder add(String s) {
            joiner.add(s);
            return this;
        }

        StringJoinerBuilder addInt(int i) {
            joiner.add(Integer.toString(i));
            return this;
        }

        String build() {
            return joiner.toString();
        }
    }
}
