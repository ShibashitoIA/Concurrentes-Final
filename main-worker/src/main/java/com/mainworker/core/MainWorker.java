package com.mainworker.core;

import com.rafthq.core.RaftNode;
import com.rafthq.core.NodeConfig;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * Worker Principal - Integración de RAFT + State Machine + HTTP Monitor
 *
 * Este es el proceso principal que:
 * 1. Arranca el módulo RAFT
 * 2. Registra el WorkerStateMachine como callback
 * 3. Inicia el servidor HTTP de monitoreo
 *
 * Uso:
 *   java -cp ... com.mainworker.core.MainWorker --config path/to/config.properties
 */
public class MainWorker {

    private static final Logger LOGGER = Logger.getLogger(MainWorker.class.getName());

    private final RaftNode raftNode;
    private final FileManager fileManager;
    private final ModelRegistry modelRegistry;
    private final WorkerStateMachine stateMachine;
    private final HTTPMonitorServer httpServer;
    private final NodeConfig config;

    public MainWorker(String configPath) throws IOException {
        LOGGER.info("Initializing MainWorker with config: " + configPath);

        // Cargar configuración
        this.config = NodeConfig.fromFile(configPath);

        // Determinar directorios de datos y modelos
        String storageDir = config.getStorageDir();
        String dataDir = Paths.get(storageDir, "data").toString();
        String modelsDir = Paths.get(storageDir, "models").toString();

        // Inicializar gestores
        this.fileManager = new FileManager(dataDir);
        this.modelRegistry = new ModelRegistry(modelsDir);

        // Inicializar State Machine
        this.stateMachine = new WorkerStateMachine(fileManager, modelRegistry, modelsDir);

        // Inicializar nodo RAFT con el state machine
        this.raftNode = new RaftNode(config, stateMachine);

        // Determinar puerto HTTP (puerto base + 1000)
        int httpPort = config.getPort() + 1000;

        // Inicializar servidor HTTP
        this.httpServer = new HTTPMonitorServer(httpPort, raftNode, stateMachine);

        LOGGER.info("MainWorker initialized successfully");
        LOGGER.info("  Node ID: " + config.getNodeId());
        LOGGER.info("  RAFT Port: " + config.getPort());
        LOGGER.info("  HTTP Monitor Port: " + httpPort);
        LOGGER.info("  Data Directory: " + dataDir);
        LOGGER.info("  Models Directory: " + modelsDir);
    }

    /**
     * Inicia el worker
     */
    public void start() {
        try {
            // Iniciar nodo RAFT
            raftNode.start();
            LOGGER.info("RAFT node started");

            // Iniciar servidor HTTP
            httpServer.start();
            LOGGER.info("HTTP monitor started");

            LOGGER.info("MainWorker is now running");
            LOGGER.info("Press Ctrl+C to shutdown");

            // Agregar shutdown hook
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                LOGGER.info("Shutdown signal received");
                stop();
            }));

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to start MainWorker", e);
            throw new RuntimeException(e);
        }
    }

    /**
     * Detiene el worker
     */
    public void stop() {
        LOGGER.info("Stopping MainWorker...");

        try {
            // Detener servidor HTTP
            if (httpServer != null) {
                httpServer.stop();
                LOGGER.info("HTTP monitor stopped");
            }

            // El nodo RAFT se detendrá cuando el proceso termine
            LOGGER.info("MainWorker stopped successfully");

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error during shutdown", e);
        }
    }

    /**
     * Punto de entrada principal
     */
    public static void main(String[] args) {
        try {
            // Parsear argumentos
            String configPath = parseArguments(args);

            if (configPath == null) {
                System.err.println("Usage: java com.mainworker.core.MainWorker --config <config-file>");
                System.exit(1);
            }

            // Crear y arrancar worker
            MainWorker worker = new MainWorker(configPath);
            worker.start();

            // Mantener el programa corriendo
            Thread.currentThread().join();

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Fatal error in MainWorker", e);
            System.exit(1);
        }
    }

    /**
     * Parsea argumentos de línea de comandos
     */
    private static String parseArguments(String[] args) {
        for (int i = 0; i < args.length; i++) {
            if ("--config".equals(args[i]) && i + 1 < args.length) {
                return args[i + 1];
            }
        }
        return null;
    }

    // Getters para testing

    public RaftNode getRaftNode() {
        return raftNode;
    }

    public FileManager getFileManager() {
        return fileManager;
    }

    public ModelRegistry getModelRegistry() {
        return modelRegistry;
    }

    public WorkerStateMachine getStateMachine() {
        return stateMachine;
    }
}
