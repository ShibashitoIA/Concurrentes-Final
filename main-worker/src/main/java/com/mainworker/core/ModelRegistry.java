package com.mainworker.core;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * Registro de modelos de IA entrenados
 * Mantiene un índice de modelos con su metadata
 */
public class ModelRegistry {

    private static final Logger LOGGER = Logger.getLogger(ModelRegistry.class.getName());
    private static final String REGISTRY_FILE = "model_registry.txt";

    private final Path modelsDirectory;
    private final ConcurrentHashMap<String, ModelMetadata> modelIndex;
    private final Path registryFile;

    public ModelRegistry(String modelsDirectoryPath) throws IOException {
        this.modelsDirectory = Paths.get(modelsDirectoryPath);
        this.modelIndex = new ConcurrentHashMap<>();
        this.registryFile = modelsDirectory.resolve(REGISTRY_FILE);

        // Crear directorio si no existe
        if (!Files.exists(modelsDirectory)) {
            Files.createDirectories(modelsDirectory);
            LOGGER.info("Created models directory: " + modelsDirectory.toAbsolutePath());
        }

        // Cargar registro existente
        loadRegistry();
    }

    /**
     * Registra un nuevo modelo
     */
    public void registerModel(String modelId, String modelType, double accuracy, long timestamp) {
        ModelMetadata metadata = new ModelMetadata(
            modelId,
            modelType,
            accuracy,
            timestamp,
            "TRAINED"
        );

        modelIndex.put(modelId, metadata);
        persistRegistry();

        LOGGER.info("Model registered: " + modelId + " (" + modelType + ", accuracy=" + accuracy + ")");
    }

    /**
     * Actualiza el estado de un modelo
     */
    public void updateModelStatus(String modelId, String status) {
        ModelMetadata existing = modelIndex.get(modelId);
        if (existing != null) {
            ModelMetadata updated = new ModelMetadata(
                existing.modelId,
                existing.modelType,
                existing.accuracy,
                existing.timestamp,
                status
            );
            modelIndex.put(modelId, updated);
            persistRegistry();
            LOGGER.info("Model status updated: " + modelId + " -> " + status);
        }
    }

    /**
     * Obtiene metadata de un modelo
     */
    public ModelMetadata getModel(String modelId) {
        return modelIndex.get(modelId);
    }

    /**
     * Lista todos los modelos
     */
    public List<ModelMetadata> listAllModels() {
        return new ArrayList<>(modelIndex.values());
    }

    /**
     * Lista modelos filtrados por tipo
     */
    public List<ModelMetadata> listModelsByType(String modelType) {
        List<ModelMetadata> filtered = new ArrayList<>();
        for (ModelMetadata metadata : modelIndex.values()) {
            if (metadata.modelType.equals(modelType)) {
                filtered.add(metadata);
            }
        }
        return filtered;
    }

    /**
     * Verifica si un modelo existe
     */
    public boolean modelExists(String modelId) {
        return modelIndex.containsKey(modelId);
    }

    /**
     * Elimina un modelo del registro
     */
    public void unregisterModel(String modelId) {
        if (modelIndex.remove(modelId) != null) {
            persistRegistry();
            LOGGER.info("Model unregistered: " + modelId);
        }
    }

    /**
     * Obtiene la ruta donde debería guardarse un modelo
     */
    public String getModelPath(String modelId) {
        return modelsDirectory.resolve(modelId).toAbsolutePath().toString();
    }

    /**
     * Obtiene el total de modelos registrados
     */
    public int getTotalModels() {
        return modelIndex.size();
    }

    /**
     * Persiste el registro en disco
     */
    private void persistRegistry() {
        try (BufferedWriter writer = Files.newBufferedWriter(registryFile,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING)) {

            for (ModelMetadata metadata : modelIndex.values()) {
                writer.write(metadata.toRegistryLine());
                writer.newLine();
            }

            LOGGER.fine("Registry persisted with " + modelIndex.size() + " models");

        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to persist registry", e);
        }
    }

    /**
     * Carga el registro desde disco
     */
    private void loadRegistry() {
        if (!Files.exists(registryFile)) {
            LOGGER.info("No existing registry file found");
            return;
        }

        try (BufferedReader reader = Files.newBufferedReader(registryFile)) {
            String line;
            int count = 0;

            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }

                try {
                    ModelMetadata metadata = ModelMetadata.fromRegistryLine(line);
                    modelIndex.put(metadata.modelId, metadata);
                    count++;
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "Failed to parse registry line: " + line, e);
                }
            }

            LOGGER.info("Loaded " + count + " models from registry");

        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to load registry", e);
        }
    }

    /**
     * Clase interna para metadata de modelos
     */
    public static class ModelMetadata {
        private final String modelId;
        private final String modelType;
        private final double accuracy;
        private final long timestamp;
        private final String status;

        public ModelMetadata(String modelId, String modelType, double accuracy, long timestamp, String status) {
            this.modelId = modelId;
            this.modelType = modelType;
            this.accuracy = accuracy;
            this.timestamp = timestamp;
            this.status = status;
        }

        public String getModelId() {
            return modelId;
        }

        public String getModelType() {
            return modelType;
        }

        public double getAccuracy() {
            return accuracy;
        }

        public long getTimestamp() {
            return timestamp;
        }

        public String getStatus() {
            return status;
        }

        public String toJson() {
            return String.format(
                "{\"modelId\":\"%s\",\"modelType\":\"%s\",\"accuracy\":%.4f,\"timestamp\":%d,\"status\":\"%s\"}",
                modelId, modelType, accuracy, timestamp, status
            );
        }

        public String toRegistryLine() {
            return String.format("%s|%s|%.4f|%d|%s", modelId, modelType, accuracy, timestamp, status);
        }

        public static ModelMetadata fromRegistryLine(String line) {
            String[] parts = line.split("\\|");
            if (parts.length != 5) {
                throw new IllegalArgumentException("Invalid registry line format");
            }

            return new ModelMetadata(
                parts[0],
                parts[1],
                Double.parseDouble(parts[2]),
                Long.parseLong(parts[3]),
                parts[4]
            );
        }
    }
}
