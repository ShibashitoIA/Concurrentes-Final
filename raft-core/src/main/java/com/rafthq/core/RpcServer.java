package com.rafthq.core;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Minimal blocking socket server; each connection handled in a thread.
 */
public class RpcServer {
    private static final Logger LOG = Logger.getLogger(RpcServer.class.getName());
    
    // Maximum message size: 50MB (enough for large image datasets)
    private static final int MAX_MESSAGE_SIZE = 50 * 1024 * 1024;

    private final String host;
    private final int port;
    private final Function<String, String> handler;
    private final ExecutorService pool = Executors.newCachedThreadPool();

    public RpcServer(String host, int port, Function<String, String> handler) {
        this.host = host;
        this.port = port;
        this.handler = handler;
    }

    public void start() {
        pool.submit(() -> {
            try (ServerSocket server = new ServerSocket(port)) {
                LOG.info(() -> "RPC server listening on " + host + ":" + port);
                while (true) {
                    Socket socket = server.accept();
                    pool.submit(() -> handleSocket(socket));
                }
            } catch (IOException e) {
                LOG.log(Level.SEVERE, "RPC server error", e);
            }
        });
    }

    private void handleSocket(Socket socket) {
        try {
            socket.setSoTimeout(60000); // 60 second timeout for large payloads
            
            // Read length-prefixed message
            java.io.DataInputStream dis = new java.io.DataInputStream(socket.getInputStream());
            int messageLen = dis.readInt();
            
            // Validate message size to prevent OutOfMemoryError
            if (messageLen <= 0 || messageLen > MAX_MESSAGE_SIZE) {
                LOG.warning("Invalid message length: " + messageLen + ", rejecting connection");
                return;
            }
            
            byte[] messageBytes = new byte[messageLen];
            dis.readFully(messageBytes);
            String line = new String(messageBytes, java.nio.charset.StandardCharsets.UTF_8);
            
            if (line != null && !line.isEmpty()) {
                String response = handler.apply(line);
                
                // Send length-prefixed response
                java.io.DataOutputStream dos = new java.io.DataOutputStream(socket.getOutputStream());
                byte[] responseBytes = response.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                dos.writeInt(responseBytes.length);
                dos.write(responseBytes);
                dos.flush();
            }
        } catch (IOException e) {
            LOG.log(Level.FINE, "RPC connection error", e);
        } finally {
            try {
                socket.close();
            } catch (IOException ignore) {
            }
        }
    }
}
