package com.rafthq.core;

import java.io.IOException;
import java.net.Socket;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Blocking client for sending one-line RPC messages.
 */
public class RpcClient {
    private static final Logger LOG = Logger.getLogger(RpcClient.class.getName());
    
    // Maximum message size: 50MB (must match server)
    private static final int MAX_MESSAGE_SIZE = 50 * 1024 * 1024;

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
        try (Socket socket = new Socket(host, port)) {
            socket.setSoTimeout(60000); // 60 second timeout for large payloads
            
            // Send length-prefixed message
            java.io.DataOutputStream dos = new java.io.DataOutputStream(socket.getOutputStream());
            byte[] payloadBytes = payload.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            dos.writeInt(payloadBytes.length);
            dos.write(payloadBytes);
            dos.flush();
            
            // Read length-prefixed response
            java.io.DataInputStream dis = new java.io.DataInputStream(socket.getInputStream());
            int responseLen = dis.readInt();
            
            // Validate response size
            if (responseLen <= 0 || responseLen > MAX_MESSAGE_SIZE) {
                LOG.warning("Invalid response length from " + peer + ": " + responseLen);
                return null;
            }
            
            byte[] responseBytes = new byte[responseLen];
            dis.readFully(responseBytes);
            return new String(responseBytes, java.nio.charset.StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOG.log(Level.FINE, "RPC send failed to " + peer, e);
            return null;
        }
    }
}
