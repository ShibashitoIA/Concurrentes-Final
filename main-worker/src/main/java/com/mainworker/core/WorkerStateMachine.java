package com.mainworker.core;

import com.rafthq.core.StateMachine;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * State Machine del Worker Principal
 * Aplica comandos comprometidos por RAFT de manera determinista
 *
 * Comandos soportados:
 * - STORE_FILE|fileName|checksumMD5|sizeBytes|chunkBase64
 * - REGISTER_MODEL|modelId|modelType|accuracy|timestampEpoch
 * - TRAIN_MODEL|modelId|inputType|datasetPath|hyperparamsBase64
 * - PREDICT|requestId|modelId|inputType|inputDataBase64
 * - DELETE_FILE|fileName
 * - LIST_FILES|pattern
 * - NOP (no operation)
 */
public class WorkerStateMachine implements StateMachine {

    private static final Logger LOGGER = Logger.getLogger(WorkerStateMachine.class.getName());

    private final FileManager fileManager;
    private final ModelRegistry modelRegistry;
    private final AIServiceAdapter aiServiceAdapter;
    private final ConcurrentHashMap<String, String> predictionResults;

    // Contador de comandos aplicados
    private long appliedCommandsCount = 0;

    public WorkerStateMachine(FileManager fileManager, ModelRegistry modelRegistry, String modelsDir) {
        this.fileManager = fileManager;
        this.modelRegistry = modelRegistry;
        this.aiServiceAdapter = new AIServiceAdapter(modelsDir);
        this.predictionResults = new ConcurrentHashMap<>();
        LOGGER.info("WorkerStateMachine initialized");
    }

    @Override
    public void onCommit(byte[] command) {
        try {
            String commandStr = new String(command, StandardCharsets.UTF_8);
            LOGGER.info("Applying committed command: " + commandStr.substring(0, Math.min(100, commandStr.length())));

            String[] parts = commandStr.split("\\|", -1);
            if (parts.length == 0) {
                LOGGER.warning("Empty command received");
                return;
            }

            String commandType = parts[0];

            switch (commandType) {
                case "STORE_FILE":
                    handleStoreFile(parts);
                    break;
                case "REGISTER_MODEL":
                    handleRegisterModel(parts);
                    break;
                case "TRAIN_MODEL":
                    handleTrainModel(parts);
                    break;
                case "PREDICT":
                    handlePredict(parts);
                    break;
                case "DELETE_FILE":
                    handleDeleteFile(parts);
                    break;
                case "LIST_FILES":
                    handleListFiles(parts);
                    break;
                case "NOP":
                    // No operation - used for testing
                    LOGGER.info("NOP command received");
                    break;
                default:
                    LOGGER.warning("Unknown command type: " + commandType);
            }

            appliedCommandsCount++;

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error applying command", e);
        }
    }

    /**
     * STORE_FILE|fileName|checksumMD5|sizeBytes|chunkBase64
     */
    private void handleStoreFile(String[] parts) {
        if (parts.length < 5) {
            LOGGER.warning("Invalid STORE_FILE command format");
            return;
        }

        String fileName = parts[1];
        String checksum = parts[2];
        long sizeBytes = Long.parseLong(parts[3]);
        String dataBase64 = parts[4];

        try {
            fileManager.storeFile(fileName, dataBase64, checksum, sizeBytes);
            LOGGER.info("File stored successfully: " + fileName + " (" + sizeBytes + " bytes)");
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to store file: " + fileName, e);
        }
    }

    /**
     * REGISTER_MODEL|modelId|modelType|accuracy|timestampEpoch
     */
    private void handleRegisterModel(String[] parts) {
        if (parts.length < 5) {
            LOGGER.warning("Invalid REGISTER_MODEL command format");
            return;
        }

        String modelId = parts[1];
        String modelType = parts[2];
        double accuracy = Double.parseDouble(parts[3]);
        long timestamp = Long.parseLong(parts[4]);

        try {
            modelRegistry.registerModel(modelId, modelType, accuracy, timestamp);
            LOGGER.info("Model registered successfully: " + modelId + " (" + modelType + ", accuracy=" + accuracy + ")");
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to register model: " + modelId, e);
        }
    }

    /**
     * TRAIN_MODEL|modelId|inputType|datasetPath|hyperparamsBase64
     * Este comando se integra con el módulo de IA (Persona 2)
     *
     * hyperparamsBase64 contiene los parámetros en formato:
     * inputSize,outputSize,epochs,learningRate,numThreads,hasHeader,maxVocab,imageWidth,imageHeight,grayscale
     */
    private void handleTrainModel(String[] parts) {
        if (parts.length < 5) {
            LOGGER.warning("Invalid TRAIN_MODEL command format");
            return;
        }

        String modelId = parts[1];
        String inputType = parts[2];
        String datasetPath = parts[3];
        String hyperparamsBase64 = parts[4];

        try {
            // Decodificar hiperparámetros
            String hyperparamsStr = new String(Base64.getDecoder().decode(hyperparamsBase64), StandardCharsets.UTF_8);
            String[] hyperparams = hyperparamsStr.split(",");

            if (hyperparams.length < 10) {
                LOGGER.warning("Invalid hyperparameters format");
                return;
            }

            // Parsear hiperparámetros
            int inputSize = Integer.parseInt(hyperparams[0]);
            int outputSize = Integer.parseInt(hyperparams[1]);
            int epochs = Integer.parseInt(hyperparams[2]);
            double learningRate = Double.parseDouble(hyperparams[3]);
            int numThreads = Integer.parseInt(hyperparams[4]);
            boolean hasHeader = Boolean.parseBoolean(hyperparams[5]);
            int maxVocab = Integer.parseInt(hyperparams[6]);
            int imageWidth = Integer.parseInt(hyperparams[7]);
            int imageHeight = Integer.parseInt(hyperparams[8]);
            boolean grayscale = Boolean.parseBoolean(hyperparams[9]);

            LOGGER.info("Training model: " + modelId + " with input type: " + inputType);
            LOGGER.info("Dataset: " + datasetPath + ", epochs=" + epochs + ", lr=" + learningRate);

            // Obtener ruta absoluta del dataset
            String fullDatasetPath = fileManager.getFilePath(datasetPath);

            // Si es un ZIP, descomprimirlo y buscar el TSV
            if (fullDatasetPath.endsWith(".zip")) {
                LOGGER.info("Extracting ZIP file: " + fullDatasetPath);
                String extractedPath = extractZipAndFindTsv(fullDatasetPath);
                if (extractedPath != null) {
                    fullDatasetPath = extractedPath;
                    LOGGER.info("Using extracted TSV: " + fullDatasetPath);
                } else {
                    LOGGER.severe("No TSV file found in ZIP");
                    return;
                }
            }

            // Llamar al módulo de IA (passing modelId from command)
            String resultModelId = aiServiceAdapter.trainModel(
                modelId,  // Pass the modelId from the RAFT command
                inputType,
                fullDatasetPath,
                inputSize,
                outputSize,
                epochs,
                learningRate,
                numThreads,
                hasHeader,
                maxVocab,
                imageWidth,
                imageHeight,
                grayscale
            );

            // Registrar modelo como "entrenado"
            modelRegistry.registerModel(
                resultModelId,
                inputType.toLowerCase() + "-model",
                0.0, // Accuracy será actualizada por el módulo de IA
                System.currentTimeMillis()
            );

            LOGGER.info("Model training initiated: " + resultModelId);

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to train model: " + modelId, e);
        }
    }

    /**
     * Extrae un archivo ZIP y busca el archivo TSV dentro
     */
    private String extractZipAndFindTsv(String zipPath) {
        Path zipFile = Paths.get(zipPath).toAbsolutePath().normalize();
        Path extractDir = zipFile.getParent().resolve(zipFile.getFileName().toString().replace(".zip", "_extracted")).normalize();
        
        try {
            Files.createDirectories(extractDir);
            LOGGER.info("Extracting to: " + extractDir);
            
            // Extraer ZIP
            try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile.toFile()))) {
                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    String entryName = entry.getName();
                    Path outputPath = extractDir.resolve(entryName).normalize();
                    
                    // Prevenir zip slip - ambas rutas deben estar normalizadas
                    if (!outputPath.startsWith(extractDir)) {
                        LOGGER.warning("Skipping potentially unsafe ZIP entry: " + entryName);
                        zis.closeEntry();
                        continue;
                    }
                    
                    if (entry.isDirectory()) {
                        Files.createDirectories(outputPath);
                        LOGGER.fine("Created directory: " + outputPath);
                    } else {
                        Files.createDirectories(outputPath.getParent());
                        try (FileOutputStream fos = new FileOutputStream(outputPath.toFile())) {
                            byte[] buffer = new byte[8192];
                            int len;
                            while ((len = zis.read(buffer)) > 0) {
                                fos.write(buffer, 0, len);
                            }
                        }
                    }
                    zis.closeEntry();
                }
            }
            
            // Buscar archivo TSV en el directorio extraído
            return findTsvFile(extractDir);
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to extract ZIP: " + zipPath, e);
            return null;
        }
    }
    
    /**
     * Busca recursivamente un archivo TSV en un directorio
     */
    private String findTsvFile(Path dir) {
        try {
            // Primero buscar en el directorio raíz
            File[] files = dir.toFile().listFiles();
            if (files != null) {
                for (File f : files) {
                    if (f.isFile() && f.getName().endsWith(".tsv")) {
                        return f.getAbsolutePath();
                    }
                }
                // Si no hay TSV en raíz, buscar en subdirectorios
                for (File f : files) {
                    if (f.isDirectory()) {
                        String found = findTsvFile(f.toPath());
                        if (found != null) {
                            return found;
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error searching for TSV in: " + dir, e);
        }
        return null;
    }

    /**
     * PREDICT|requestId|modelId|inputType|inputDataBase64
     */
    private void handlePredict(String[] parts) {
        if (parts.length < 5) {
            LOGGER.warning("Invalid PREDICT command format");
            return;
        }

        String requestId = parts[1];
        String modelId = parts[2];
        String inputType = parts[3];
        String inputDataBase64 = parts[4];

        try {
            // Verificar que el modelo existe
            if (!modelRegistry.modelExists(modelId)) {
                LOGGER.warning("Model not found: " + modelId);
                predictionResults.put(requestId, "ERROR:MODEL_NOT_FOUND");
                return;
            }

            String inputData;
            if ("IMAGE".equals(inputType)) {
                // Para imágenes, pasar el base64 directamente (NO convertir a UTF-8 string)
                inputData = inputDataBase64;
            } else {
                // Para texto/tabular, decodificar el base64 a string
                inputData = new String(Base64.getDecoder().decode(inputDataBase64), StandardCharsets.UTF_8);
            }

            LOGGER.info("Predicting with model: " + modelId + ", input type: " + inputType);

            // Llamar al módulo de IA para predicción
            double[] prediction = aiServiceAdapter.predict(modelId, inputType, inputData);

            // Guardar resultado
            String resultStr = arrayToString(prediction);
            predictionResults.put(requestId, resultStr);

            LOGGER.info("Prediction completed for request: " + requestId + " -> " + resultStr);

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to predict: " + requestId, e);
            predictionResults.put(requestId, "ERROR:" + e.getMessage());
        }
    }

    /**
     * DELETE_FILE|fileName
     */
    private void handleDeleteFile(String[] parts) {
        if (parts.length < 2) {
            LOGGER.warning("Invalid DELETE_FILE command format");
            return;
        }

        String fileName = parts[1];

        try {
            fileManager.deleteFile(fileName);
            LOGGER.info("File deleted successfully: " + fileName);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to delete file: " + fileName, e);
        }
    }

    /**
     * LIST_FILES|pattern
     */
    private void handleListFiles(String[] parts) {
        String pattern = parts.length > 1 ? parts[1] : "*";

        try {
            var files = fileManager.listFiles(pattern);
            LOGGER.info("Listed " + files.size() + " files matching pattern: " + pattern);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to list files with pattern: " + pattern, e);
        }
    }

    private String arrayToString(double[] array) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < array.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(String.format("%.4f", array[i]));
        }
        sb.append("]");
        return sb.toString();
    }

    public long getAppliedCommandsCount() {
        return appliedCommandsCount;
    }

    public FileManager getFileManager() {
        return fileManager;
    }

    public ModelRegistry getModelRegistry() {
        return modelRegistry;
    }

    public String getPredictionResult(String requestId) {
        return predictionResults.get(requestId);
    }

    /**
     * Adapter para el módulo de IA
     * Encapsula las llamadas al AIService de Persona 2
     */
    private static class AIServiceAdapter {
        private final String modelsDir;
        private Object aiService = null;
        private boolean aiServiceAvailable = false;

        public AIServiceAdapter(String modelsDir) {
            this.modelsDir = modelsDir;
            tryInitializeAIService();
        }

        private void tryInitializeAIService() {
            try {
                Class<?> aiServiceClass = Class.forName("com.mycompany.moduloia.api.AIService");
                Class<?> pathClass = Class.forName("java.nio.file.Path");
                Class<?> pathsClass = Class.forName("java.nio.file.Paths");

                Object modelsPath = pathsClass.getMethod("get", String.class, String[].class)
                    .invoke(null, modelsDir, new String[0]);

                aiService = aiServiceClass.getConstructor(pathClass).newInstance(modelsPath);
                aiServiceAvailable = true;
                LOGGER.info("AIService initialized successfully");
            } catch (ClassNotFoundException e) {
                LOGGER.warning("AIService not found in classpath. Using stub mode. Add ModuloIA JAR to classpath.");
                aiServiceAvailable = false;
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Failed to initialize AIService. Using stub mode.", e);
                aiServiceAvailable = false;
            }
        }

        public String trainModel(String providedModelId, String inputType, String datasetPath, int inputSize, int outputSize,
                                 int epochs, double learningRate, int numThreads, boolean hasHeader,
                                 int maxVocab, int imageWidth, int imageHeight, boolean grayscale) {

            if (!aiServiceAvailable) {
                String modelId = providedModelId != null ? providedModelId : "stub-model-" + System.currentTimeMillis();
                LOGGER.info("AIService stub: would train " + inputType + " model with dataset: " + datasetPath);
                return modelId;
            }

            try {
                Class<?> inputTypeClass = Class.forName("com.mycompany.moduloia.api.InputType");
                Class<?> trainingRequestClass = Class.forName("com.mycompany.moduloia.api.TrainingRequest");

                Object inputTypeEnum = Enum.valueOf((Class<Enum>) inputTypeClass, inputType);

                // Use constructor with modelId (new constructor)
                Object trainingRequest = trainingRequestClass.getConstructor(
                    String.class, inputTypeClass, String.class, int.class, int.class, int.class, double.class,
                    int.class, boolean.class, int.class, int.class, int.class, boolean.class
                ).newInstance(
                    providedModelId, inputTypeEnum, datasetPath, inputSize, outputSize, epochs, learningRate,
                    numThreads, hasHeader, maxVocab, imageWidth, imageHeight, grayscale
                );

                String modelId = (String) aiService.getClass()
                    .getMethod("trainModel", trainingRequestClass)
                    .invoke(aiService, trainingRequest);

                LOGGER.info("Model trained successfully: " + modelId);
                return modelId;

            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Failed to train model", e);
                return "error-model-" + System.currentTimeMillis();
            }
        }

        public double[] predict(String modelId, String inputType, String inputData) {
            if (!aiServiceAvailable) {
                LOGGER.info("AIService stub: would predict with model: " + modelId);
                return new double[]{0.5, 0.5};
            }

            try {
                Class<?> inputTypeClass = Class.forName("com.mycompany.moduloia.api.InputType");
                Class<?> predictRequestClass = Class.forName("com.mycompany.moduloia.api.PredictRequest");

                Object inputTypeEnum = Enum.valueOf((Class<Enum>) inputTypeClass, inputType);

                Object predictRequest;

                if ("TABULAR".equals(inputType)) {
                    String[] values = inputData.split(",");
                    double[] tabularInput = new double[values.length];
                    for (int i = 0; i < values.length; i++) {
                        tabularInput[i] = Double.parseDouble(values[i].trim());
                    }

                    predictRequest = predictRequestClass.getConstructor(
                        inputTypeClass, String.class, double[].class, String.class, String.class
                    ).newInstance(inputTypeEnum, modelId, tabularInput, null, null);

                } else if ("TFIDF".equals(inputType)) {
                    predictRequest = predictRequestClass.getConstructor(
                        inputTypeClass, String.class, double[].class, String.class, String.class
                    ).newInstance(inputTypeEnum, modelId, null, inputData, null);

                } else if ("IMAGE".equals(inputType)) {
                    // inputData contiene los bytes de la imagen en base64
                    // Decodificar y pasar como imageBytes
                    byte[] imageBytes = Base64.getDecoder().decode(inputData);
                    
                    predictRequest = predictRequestClass.getConstructor(
                        inputTypeClass, String.class, double[].class, String.class, String.class, byte[].class
                    ).newInstance(inputTypeEnum, modelId, null, null, null, imageBytes);

                } else {
                    throw new IllegalArgumentException("Unsupported input type: " + inputType);
                }

                double[] prediction = (double[]) aiService.getClass()
                    .getMethod("predict", predictRequestClass)
                    .invoke(aiService, predictRequest);

                LOGGER.info("Prediction completed for model: " + modelId);
                return prediction;

            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Failed to predict", e);
                return new double[]{-1.0};
            }
        }
    }
}
