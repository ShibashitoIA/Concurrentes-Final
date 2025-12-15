package com.rafthq.core;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

/**
 * Entry point: load node configuration and start RAFT node.
 * Usage: java -cp out com.rafthq.core.Main --config path/to/config.properties
 */
public class Main {
    private static final Logger LOG = Logger.getLogger(Main.class.getName());

    public static void main(String[] args) {
        String configPath = null;

        // Parse arguments
        for (int i = 0; i < args.length; i++) {
            if ("--config".equals(args[i]) && i + 1 < args.length) {
                configPath = args[i + 1];
                i++;
            }
        }

        if (configPath == null) {
            System.err.println("Usage: java Main --config <path>");
            System.exit(1);
        }

        try {
            // Load configuration
            NodeConfig config = NodeConfig.fromFile(configPath);
            LOG.info(() -> "Loaded config: node=" + config.getNodeId() +
                    " host=" + config.getHost() + " port=" + config.getPort() +
                    " peers=" + config.getPeers());

            // Create simple state machine that logs commits
            StateMachine stateMachine = command -> {
                LOG.info(() -> "onCommit: received " + command.length + " bytes");
            };

            // Create and start node
            RaftNode node = new RaftNode(config, (cmd) -> {
                System.out.println("Applied: " + new String(cmd, StandardCharsets.UTF_8));
            });
            node.start();

            // Loop interactivo para enviar comandos al líder
            Scanner sc = new Scanner(System.in);
            System.out.println("Type a line to append (only works on leader):");
            while (sc.hasNextLine()) {
                String line = sc.nextLine();
                if (line.isEmpty()) continue;
                boolean ok = node.appendCommand(line.getBytes(StandardCharsets.UTF_8));
                if (!ok) {
                    System.out.println("Not leader. Try on the current leader terminal.");
                }
            }

            // Keep the process alive
            Thread.currentThread().join();
        } catch (IOException e) {
            LOG.log(Level.SEVERE, "Failed to load config: " + configPath, e);
            System.exit(1);
        } catch (InterruptedException e) {
            LOG.info("Node interrupted");
            System.exit(0);
        }
    }
}
