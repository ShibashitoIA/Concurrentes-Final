package com.rafthq.core;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Blocking client for sending one-line RPC messages.
 */
public class RpcClient {
    private static final Logger LOG = Logger.getLogger(RpcClient.class.getName());

    public RequestVoteResponse requestVote(String peer, RequestVoteRequest request) {
        String payload = MessageCodec.encodeRequestVote(request);
        String response = send(peer, payload);
        if (response == null) return null;
        return MessageCodec.decodeRequestVoteResponse(response);
    }

    public AppendEntriesResponse appendEntries(String peer, AppendEntriesRequest request) {
        String payload = MessageCodec.encodeAppendEntries(request);
        String response = send(peer, payload);
        if (response == null) return null;
        return MessageCodec.decodeAppendEntriesResponse(response);
    }

    private String send(String peer, String payload) {
        String[] parts = peer.split(":");
        if (parts.length != 2) {
            LOG.warning("Invalid peer format: " + peer);
            return null;
        }
        String host = parts[0];
        int port = Integer.parseInt(parts[1]);
        try (Socket socket = new Socket(host, port);
             PrintWriter writer = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
            writer.println(payload);
            return reader.readLine();
        } catch (IOException e) {
            LOG.log(Level.FINE, "RPC send failed to " + peer, e);
            return null;
        }
    }
}
