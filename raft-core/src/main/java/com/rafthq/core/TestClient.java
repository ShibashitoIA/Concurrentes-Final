package com.rafthq.core;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Simple test client to send commands to a node.
 * Usage: java -cp out com.rafthq.core.TestClient <host:port> <command>
 */
public class TestClient {
    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Usage: TestClient <host:port> <command>");
            System.exit(1);
        }

        String[] parts = args[0].split(":");
        String host = parts[0];
        int port = Integer.parseInt(parts[1]);
        String command = args[1];

        // For now, just connect and send a line (simulating user input)
        // This is a HACK - real implementation would use a proper RPC
        try (Socket socket = new Socket(host, port);
             PrintWriter writer = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
            
            System.out.println("Connected to " + host + ":" + port);
            System.out.println("Sending command: " + command);
            writer.println(command);
            
            // Read response (if any)
            String response = reader.readLine();
            System.out.println("Response: " + response);
        }
    }
}
