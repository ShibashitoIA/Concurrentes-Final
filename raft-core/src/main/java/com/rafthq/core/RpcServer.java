package com.rafthq.core;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
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
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter writer = new PrintWriter(socket.getOutputStream(), true)) {
            String line = reader.readLine();
            if (line != null) {
                String response = handler.apply(line);
                writer.println(response);
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
