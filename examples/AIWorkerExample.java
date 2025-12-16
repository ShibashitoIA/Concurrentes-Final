package examples;

import com.rafthq.core.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.*;

/**
 * Ejemplo de integración: AI Worker con entrenamiento distribuido.
 * 
 * Este worker:
 * - Participa en consenso RAFT
 * - Entrena modelos de IA cuando recibe comandos TRAIN
 * - Responde a predicciones con PREDICT
 * - Acepta requests de clientes solo si es líder
 * 
 * Persona 2: Adaptar esto con tu código real de redes neuronales.
 */
public class AIWorkerExample {
    private static final Logger LOG = Logger.getLogger(AIWorkerExample.class.getName());
    
    private final RaftNode raftNode;
    private final Path modelsDir;
    private final Map<String, MockNeuralNetwork> loadedModels = new ConcurrentHashMap<>();
    private final ExecutorService trainingPool = Executors.newFixedThreadPool(4);
    
    public AIWorkerExample(String configPath) throws IOException {
        // Cargar configuración
        NodeConfig config = NodeConfig.fromFile(configPath);
        this.modelsDir = Paths.get("models", config.getNodeId());
        Files.createDirectories(modelsDir);
        
        // Crear state machine: aplica comandos de IA
        StateMachine stateMachine = this::onCommit;
        
        // Inicializar RAFT
        this.raftNode = new RaftNode(config, stateMachine);
        
        LOG.info("AIWorker initialized: " + config.getNodeId());
    }
    
    /**
     * Callback de RAFT: ejecutado cuando un comando es comprometido.
     */
    private void onCommit(byte[] command) {
        String cmdStr = new String(command, StandardCharsets.UTF_8);
        String[] parts = cmdStr.split("\\|", -1);
        
        try {
            switch (parts[0]) {
                case "TRAIN":
                    handleTrain(parts[1], parts[2], parts[3]);
                    break;
                case "PREDICT":
                    handlePredict(parts[1], parts[2], parts[3]);
                    break;
                case "REGISTER_MODEL":
                    handleRegisterModel(parts[1], parts[2], parts[3], parts[4]);
                    break;
                default:
                    LOG.warning("Unknown command: " + parts[0]);
            }
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Error applying command: " + cmdStr, e);
        }
    }
    
    /**
     * Entrena un modelo (paralelismo de datos).
     */
    private void handleTrain(String modelId, String dataPath, String hyperparamsB64) {
        LOG.info("Training model: " + modelId + " with data: " + dataPath);
        
        // Decodificar hiperparámetros
        String hyperparams = new String(Base64.getDecoder().decode(hyperparamsB64), StandardCharsets.UTF_8);
        
        // Entrenar en thread pool (no bloquear apply loop)
        trainingPool.submit(() -> {
            try {
                // TODO Persona 2: Reemplazar con tu red neuronal real
                MockNeuralNetwork nn = new MockNeuralNetwork();
                nn.train(dataPath, hyperparams);
                
                // Guardar modelo a disco
                Path modelPath = modelsDir.resolve(modelId + ".dat");
                nn.save(modelPath);
                
                // Cargar en memoria para predicciones rápidas
                loadedModels.put(modelId, nn);
                
                LOG.info("Model trained and saved: " + modelId);
                
                // Opcional: Registrar metadata del modelo
                String registerCmd = String.join("|", 
                    "REGISTER_MODEL", modelId, "MLP", "0.95", String.valueOf(System.currentTimeMillis()));
                raftNode.appendCommand(registerCmd.getBytes(StandardCharsets.UTF_8));
                
            } catch (Exception e) {
                LOG.log(Level.SEVERE, "Training failed for model: " + modelId, e);
            }
        });
    }
    
    /**
     * Ejecuta predicción con un modelo entrenado.
     */
    private void handlePredict(String modelId, String inputB64, String requestId) {
        LOG.fine("Predict with model: " + modelId + " for request: " + requestId);
        
        MockNeuralNetwork nn = loadedModels.get(modelId);
        if (nn == null) {
            // Intentar cargar desde disco
            try {
                Path modelPath = modelsDir.resolve(modelId + ".dat");
                if (Files.exists(modelPath)) {
                    nn = MockNeuralNetwork.load(modelPath);
                    loadedModels.put(modelId, nn);
                } else {
                    LOG.warning("Model not found: " + modelId);
                    return;
                }
            } catch (IOException e) {
                LOG.log(Level.WARNING, "Failed to load model: " + modelId, e);
                return;
            }
        }
        
        // Decodificar input
        byte[] inputBytes = Base64.getDecoder().decode(inputB64);
        double[] input = deserializeDoubleArray(inputBytes);
        
        // Predecir
        double[] output = nn.predict(input);
        
        // Solo el líder responde al cliente (implementar en capa de red)
        if (raftNode.getState() == RaftState.LEADER) {
            LOG.info("Prediction result for " + requestId + ": " + Arrays.toString(output));
            // TODO: Enviar respuesta al cliente por socket
        }
    }
    
    /**
     * Registra metadata de un modelo.
     */
    private void handleRegisterModel(String modelId, String modelType, String accuracy, String timestamp) {
        LOG.info("Registered model: " + modelId + " type=" + modelType + " accuracy=" + accuracy);
        // TODO: Guardar en índice/base de datos local
    }
    
    /**
     * API pública: Cliente solicita entrenar modelo (solo si es líder).
     */
    public String trainModel(String dataPath, Map<String, Object> hyperparams) {
        if (raftNode.getState() != RaftState.LEADER) {
            return "NOT_LEADER"; // Cliente debe redirigir
        }
        
        String modelId = UUID.randomUUID().toString();
        String hyperparamsJson = hyperparams.toString();
        String hyperparamsB64 = Base64.getEncoder().encodeToString(hyperparamsJson.getBytes(StandardCharsets.UTF_8));
        
        String cmd = String.join("|", "TRAIN", modelId, dataPath, hyperparamsB64);
        boolean accepted = raftNode.appendCommand(cmd.getBytes(StandardCharsets.UTF_8));
        
        return accepted ? modelId : "ERROR";
    }
    
    /**
     * API pública: Cliente solicita predicción.
     */
    public double[] predict(String modelId, double[] input) {
        if (raftNode.getState() != RaftState.LEADER) {
            throw new IllegalStateException("NOT_LEADER");
        }
        
        String requestId = UUID.randomUUID().toString();
        byte[] inputBytes = serializeDoubleArray(input);
        String inputB64 = Base64.getEncoder().encodeToString(inputBytes);
        
        String cmd = String.join("|", "PREDICT", modelId, inputB64, requestId);
        raftNode.appendCommand(cmd.getBytes(StandardCharsets.UTF_8));
        
        // TODO: Esperar resultado asíncrono (usando Future, CompletableFuture, etc.)
        return new double[]{0.0}; // Placeholder
    }
    
    public void start() {
        raftNode.start();
    }
    
    public boolean isLeader() {
        return raftNode.getState() == RaftState.LEADER;
    }
    
    // === Helpers ===
    
    private byte[] serializeDoubleArray(double[] arr) {
        // Serialización simple: 8 bytes por double
        java.nio.ByteBuffer buffer = java.nio.ByteBuffer.allocate(arr.length * 8);
        for (double d : arr) buffer.putDouble(d);
        return buffer.array();
    }
    
    private double[] deserializeDoubleArray(byte[] bytes) {
        java.nio.ByteBuffer buffer = java.nio.ByteBuffer.wrap(bytes);
        double[] arr = new double[bytes.length / 8];
        for (int i = 0; i < arr.length; i++) arr[i] = buffer.getDouble();
        return arr;
    }
    
    // === Mock Neural Network (reemplazar con tu implementación real) ===
    
    static class MockNeuralNetwork {
        private double[] weights = new double[10];
        
        public void train(String dataPath, String hyperparams) throws InterruptedException {
            // Simular entrenamiento
            LOG.info("Training on " + dataPath + " with " + hyperparams);
            Thread.sleep(1000); // Simular tiempo de entrenamiento
            for (int i = 0; i < weights.length; i++) {
                weights[i] = Math.random();
            }
        }
        
        public double[] predict(double[] input) {
            // Simular predicción
            double[] output = new double[2];
            output[0] = Math.random();
            output[1] = 1.0 - output[0];
            return output;
        }
        
        public void save(Path path) throws IOException {
            // Serializar pesos
            StringBuilder sb = new StringBuilder();
            for (double w : weights) sb.append(w).append(",");
            Files.writeString(path, sb.toString());
        }
        
        public static MockNeuralNetwork load(Path path) throws IOException {
            MockNeuralNetwork nn = new MockNeuralNetwork();
            String content = Files.readString(path);
            String[] parts = content.split(",");
            for (int i = 0; i < parts.length && i < nn.weights.length; i++) {
                nn.weights[i] = Double.parseDouble(parts[i]);
            }
            return nn;
        }
    }
    
    // === Main ===
    
    public static void main(String[] args) {
        if (args.length < 2 || !"--config".equals(args[0])) {
            System.err.println("Usage: java AIWorkerExample --config <path>");
            System.exit(1);
        }
        
        try {
            AIWorkerExample worker = new AIWorkerExample(args[1]);
            worker.start();
            
            // Keep alive
            Thread.currentThread().join();
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Failed to start AIWorker", e);
            System.exit(1);
        }
    }
}
