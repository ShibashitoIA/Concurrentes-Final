package examples;

import com.rafthq.core.*;
import com.sun.net.httpserver.*;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.logging.*;
import java.util.stream.Collectors;

/**
 * Ejemplo de integración: Storage Worker con replicación de archivos.
 * 
 * Este worker:
 * - Participa en consenso RAFT
 * - Replica archivos cuando recibe comandos STORE_FILE
 * - Expone HTTP monitor en puerto 8080+nodeOffset
 * - Verifica checksums para integridad
 * 
 * Persona 3: Adaptar esto con tu lógica de storage real.
 */
public class StorageWorkerExample {
    private static final Logger LOG = Logger.getLogger(StorageWorkerExample.class.getName());
    
    private final RaftNode raftNode;
    private final Path dataDir;
    private final Map<String, FileMetadata> fileIndex = new HashMap<>();
    private HttpServer httpServer;
    
    public StorageWorkerExample(String configPath, int httpPort) throws IOException {
        // Cargar configuración
        NodeConfig config = NodeConfig.fromFile(configPath);
        this.dataDir = Paths.get("data", config.getNodeId());
        Files.createDirectories(dataDir);
        
        // Crear state machine
        StateMachine stateMachine = this::onCommit;
        
        // Inicializar RAFT
        this.raftNode = new RaftNode(config, stateMachine);
        
        // Inicializar HTTP monitor
        startHttpServer(httpPort);
        
        LOG.info("StorageWorker initialized: " + config.getNodeId() + " HTTP on :" + httpPort);
    }
    
    /**
     * Callback de RAFT: aplicar comandos de storage.
     */
    private void onCommit(byte[] command) {
        String cmdStr = new String(command, StandardCharsets.UTF_8);
        String[] parts = cmdStr.split("\\|", -1);
        
        try {
            switch (parts[0]) {
                case "STORE_FILE":
                    handleStoreFile(parts[1], parts[2], Integer.parseInt(parts[3]), parts[4]);
                    break;
                case "DELETE_FILE":
                    handleDeleteFile(parts[1]);
                    break;
                default:
                    LOG.fine("Ignoring command: " + parts[0]);
            }
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Error applying command: " + cmdStr, e);
        }
    }
    
    /**
     * Replica un archivo en el nodo local.
     */
    private void handleStoreFile(String fileName, String checksumMD5, int sizeBytes, String contentB64) {
        LOG.info("Storing file: " + fileName + " size=" + sizeBytes);
        
        try {
            // Decodificar contenido
            byte[] content = Base64.getDecoder().decode(contentB64);
            
            // Verificar tamaño
            if (content.length != sizeBytes) {
                LOG.warning("Size mismatch for " + fileName + ": expected " + sizeBytes + ", got " + content.length);
                return;
            }
            
            // Verificar checksum
            String actualChecksum = calculateMD5(content);
            if (!actualChecksum.equalsIgnoreCase(checksumMD5)) {
                LOG.severe("Checksum mismatch for " + fileName + ": expected " + checksumMD5 + ", got " + actualChecksum);
                return;
            }
            
            // Guardar archivo
            Path filePath = dataDir.resolve(fileName);
            Files.write(filePath, content);
            
            // Actualizar índice
            synchronized (fileIndex) {
                fileIndex.put(fileName, new FileMetadata(fileName, sizeBytes, checksumMD5, System.currentTimeMillis()));
            }
            
            LOG.info("File stored successfully: " + fileName);
            
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Failed to store file: " + fileName, e);
        }
    }
    
    /**
     * Elimina un archivo replicado.
     */
    private void handleDeleteFile(String fileName) {
        LOG.info("Deleting file: " + fileName);
        
        try {
            Path filePath = dataDir.resolve(fileName);
            Files.deleteIfExists(filePath);
            
            synchronized (fileIndex) {
                fileIndex.remove(fileName);
            }
            
            LOG.info("File deleted: " + fileName);
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Failed to delete file: " + fileName, e);
        }
    }
    
    /**
     * API pública: Cliente solicita guardar archivo (solo líder acepta).
     */
    public boolean storeFile(String fileName, byte[] content) {
        if (raftNode.getState() != RaftState.LEADER) {
            return false; // Not leader
        }
        
        String checksum = calculateMD5(content);
        String contentB64 = Base64.getEncoder().encodeToString(content);
        
        String cmd = String.join("|", "STORE_FILE", fileName, checksum, String.valueOf(content.length), contentB64);
        return raftNode.appendCommand(cmd.getBytes(StandardCharsets.UTF_8));
    }
    
    /**
     * API pública: Listar archivos replicados.
     */
    public List<FileMetadata> listFiles() {
        synchronized (fileIndex) {
            return new ArrayList<>(fileIndex.values());
        }
    }
    
    public void start() {
        raftNode.start();
    }
    
    // === HTTP Monitor ===
    
    private void startHttpServer(int port) throws IOException {
        httpServer = HttpServer.create(new InetSocketAddress(port), 0);
        httpServer.setExecutor(Executors.newFixedThreadPool(2));
        
        // Endpoint: /status
        httpServer.createContext("/status", exchange -> {
            String response = String.format(
                "{\"nodeId\":\"%s\",\"role\":\"%s\",\"term\":%d,\"commitIndex\":%d}",
                raftNode.getClass().getSimpleName(), // nodeId no expuesto, usar placeholder
                raftNode.getState(),
                raftNode.getCurrentTerm(),
                raftNode.getCommitIndex()
            );
            sendResponse(exchange, 200, response);
        });
        
        // Endpoint: /files
        httpServer.createContext("/files", exchange -> {
            List<FileMetadata> files = listFiles();
            String json = files.stream()
                .map(f -> String.format("{\"name\":\"%s\",\"size\":%d,\"checksum\":\"%s\"}",
                    f.fileName, f.sizeBytes, f.checksumMD5))
                .collect(Collectors.joining(",", "[", "]"));
            sendResponse(exchange, 200, json);
        });
        
        httpServer.start();
        LOG.info("HTTP server started on port " + port);
    }
    
    private void sendResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
    
    // === Helpers ===
    
    private String calculateMD5(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(data);
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("MD5 calculation failed", e);
        }
    }
    
    static class FileMetadata {
        String fileName;
        int sizeBytes;
        String checksumMD5;
        long timestamp;
        
        FileMetadata(String fileName, int sizeBytes, String checksumMD5, long timestamp) {
            this.fileName = fileName;
            this.sizeBytes = sizeBytes;
            this.checksumMD5 = checksumMD5;
            this.timestamp = timestamp;
        }
    }
    
    // === Main ===
    
    public static void main(String[] args) {
        if (args.length < 4 || !"--config".equals(args[0]) || !"--http-port".equals(args[2])) {
            System.err.println("Usage: java StorageWorkerExample --config <path> --http-port <port>");
            System.exit(1);
        }
        
        try {
            String configPath = args[1];
            int httpPort = Integer.parseInt(args[3]);
            
            StorageWorkerExample worker = new StorageWorkerExample(configPath, httpPort);
            worker.start();
            
            LOG.info("StorageWorker running. HTTP monitor: http://localhost:" + httpPort + "/status");
            
            // Keep alive
            Thread.currentThread().join();
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Failed to start StorageWorker", e);
            System.exit(1);
        }
    }
}
