package com.raft.client.network;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.raft.client.protocol.ModelInfo;
import com.raft.client.protocol.Response;

/**
 * Servicio de alto nivel que encapsula las operaciones del cliente.
 */
public class ClientService {
    private static final Logger logger = LoggerFactory.getLogger(ClientService.class);
    private final NetworkClient networkClient;

    public ClientService(String host, int port) {
        this.networkClient = new NetworkClient(host, port);
    }

    /**
     * Entrena un modelo TABULAR: sube CSV con STORE_FILE y luego envía TRAIN_MODEL.
     */
    public Response trainModel(File trainingFile, String modelName) throws IOException {
        logger.info("Entrenando modelo '{}' con archivo: {}", modelName, trainingFile.getName());

        byte[] fileData = Files.readAllBytes(trainingFile.toPath());
        String md5 = computeMD5Hex(fileData);
        String b64 = Base64.getEncoder().encodeToString(fileData);
        long size = fileData.length;

        logger.info("STORE_FILE -> name={}, sizeBytes={}, md5={}", trainingFile.getName(), size, md5);

        // 1) STORE_FILE|fileName|checksumMD5|sizeBytes|chunkBase64
        String storeCmd = String.join("|",
                "STORE_FILE",
                trainingFile.getName(),
                md5,
                String.valueOf(size),
                b64
        );
        Response storeResp = networkClient.sendCommandText(storeCmd);
        if (!storeResp.isSuccess()) {
            logger.warn("STORE_FILE falló: {}", storeResp.getMessage());
            return storeResp;
        }

        // 2) TRAIN_MODEL|modelId|inputType|datasetPath|hyperparamsBase64
        String hyperparams = buildDefaultTabularHyperparams(trainingFile);
        String[] hpArr = hyperparams.split(",", -1);
        if (hpArr.length != 10) {
            logger.warn("Hyperparams TABULAR no tienen 10 campos: {} -> {} campos", hyperparams, hpArr.length);
        } else {
            logger.info("Hyperparams TABULAR (10 ok): {}", hyperparams);
        }
        String hpB64 = Base64.getEncoder().encodeToString(hyperparams.getBytes(StandardCharsets.UTF_8));
        String trainCmd = String.join("|",
                "TRAIN_MODEL",
                modelName,
                "TABULAR",
                trainingFile.getName(),
                hpB64
        );
        logger.info("TRAIN_MODEL TABULAR -> modelId={}, dataset={}, hpB64Len={}", modelName, trainingFile.getName(), hpB64.length());
        return networkClient.sendCommandText(trainCmd);
    }

    /**
     * Entrena un modelo de IMAGEN: comprime carpeta a ZIP, STORE_FILE y luego TRAIN_MODEL.
     */
    public Response trainImageModel(File imageFolder, String modelName, int width, int height, boolean isColor) throws IOException {
        logger.info("Entrenando modelo de imágenes '{}' desde: {}", modelName, imageFolder.getAbsolutePath());

        // Detectar número de clases (subcarpetas)
        int numClasses = countSubdirectories(imageFolder);
        if (numClasses <= 0) {
            logger.warn("No se encontraron subcarpetas (clases) en: {}", imageFolder.getAbsolutePath());
            return new Response(false, "No se encontraron subcarpetas de clases en la carpeta seleccionada");
        }
        logger.info("Detectadas {} clases (subcarpetas)", numClasses);

        // Empaquetar carpeta a ZIP en memoria
        byte[] zipBytes = ZipUtils.zipDirectoryToBytes(imageFolder);
        String zipName = imageFolder.getName() + ".zip";
        String md5 = computeMD5Hex(zipBytes);
        String b64 = Base64.getEncoder().encodeToString(zipBytes);
        logger.info("STORE_FILE (ZIP) -> name={}, sizeBytes={}, md5={}", zipName, zipBytes.length, md5);

        // 1) STORE_FILE del ZIP
        String storeCmd = String.join("|",
                "STORE_FILE",
                zipName,
                md5,
                String.valueOf(zipBytes.length),
                b64
        );
        Response storeResp = networkClient.sendCommandText(storeCmd);
        if (!storeResp.isSuccess()) {
            logger.warn("STORE_FILE ZIP falló: {}", storeResp.getMessage());
            return storeResp;
        }

        // 2) TRAIN_MODEL (IMAGE) con hiperparámetros por defecto
        String hyperparams = buildDefaultImageHyperparams(width, height, !isColor, numClasses);
        String[] hpArr = hyperparams.split(",", -1);
        if (hpArr.length != 10) {
            logger.warn("Hyperparams IMAGE no tienen 10 campos: {} -> {} campos", hyperparams, hpArr.length);
        } else {
            logger.info("Hyperparams IMAGE (10 ok): {}", hyperparams);
        }
        String hpB64 = Base64.getEncoder().encodeToString(hyperparams.getBytes(StandardCharsets.UTF_8));
        String trainCmd = String.join("|",
                "TRAIN_MODEL",
                modelName,
                "IMAGE",
                zipName,
                hpB64
        );
        logger.info("TRAIN_MODEL IMAGE -> modelId={}, datasetZip={}, hpB64Len={}", modelName, zipName, hpB64.length());
        return networkClient.sendCommandText(trainCmd);
    }

    /**
     * Realiza una predicción TABULAR -> PREDICT|requestId|modelId|TABULAR|base64(inputsCSV)
     */
    public Response predict(String modelId, double[] inputs) throws IOException {
        logger.info("Predicción con modelo: {}", modelId);

        StringBuilder inputStr = new StringBuilder();
        for (int i = 0; i < inputs.length; i++) {
            if (i > 0) inputStr.append(",");
            inputStr.append(inputs[i]);
        }
        String requestId = UUID.randomUUID().toString();
        String b64 = Base64.getEncoder().encodeToString(inputStr.toString().getBytes(StandardCharsets.UTF_8));

        String predictCmd = String.join("|",
                "PREDICT",
                requestId,
                modelId,
                "TABULAR",
                b64
        );
        return networkClient.sendCommandText(predictCmd);
    }

    /**
     * Predicción de imagen -> PREDICT|requestId|modelId|IMAGE|base64(imageBytes)
     */
    public Response predictImage(String modelId, File imageFile) throws IOException {
        logger.info("Predicción de imagen con modelo: {}", modelId);
        byte[] imageData = Files.readAllBytes(imageFile.toPath());
        String requestId = UUID.randomUUID().toString();
        String b64 = Base64.getEncoder().encodeToString(imageData);
        String predictCmd = String.join("|",
                "PREDICT",
                requestId,
                modelId,
                "IMAGE",
                b64
        );
        return networkClient.sendCommandText(predictCmd);
    }

    /**
     * Lista los modelos disponibles.
     */
    public List<ModelInfo> listModels() throws IOException {
        logger.info("Listando modelos disponibles (HTTP /models)");
        String body = networkClient.getString("/models");
        // Intentar soportar múltiples formatos: arreglo directo o objeto con campo "models"
        List<ModelInfo> models = new ArrayList<>();
        com.google.gson.Gson gson = new com.google.gson.Gson();
        try {
            // Primero: intentar como lista
            java.lang.reflect.Type listType = new com.google.gson.reflect.TypeToken<List<Map<String, Object>>>() {}.getType();
            List<Map<String, Object>> modelsData = gson.fromJson(body, listType);
            if (modelsData != null) {
                for (Map<String, Object> modelData : modelsData) {
                    models.add(mapToModelInfo(modelData));
                }
                return models;
            }
        } catch (Exception ignore) { }

        try {
            // Segundo: intentar como objeto con campo "models"
            @SuppressWarnings("unchecked") Map<String, Object> obj = gson.fromJson(body, Map.class);
            if (obj != null) {
                Object m = obj.get("models");
                if (m instanceof List) {
                    @SuppressWarnings("unchecked") List<Map<String, Object>> modelsData = (List<Map<String, Object>>) m;
                    for (Map<String, Object> modelData : modelsData) {
                        models.add(mapToModelInfo(modelData));
                    }
                } else if (obj.containsKey("modelId")) {
                    models.add(mapToModelInfo(obj));
                }
            }
        } catch (Exception e) {
            logger.warn("No se pudo parsear /models: {}", e.getMessage());
        }
        return models;
    }

    /**
     * Obtiene información de un modelo específico.
     */
    public Response getModelInfo(String modelId) throws IOException {
        logger.info("Obteniendo información del modelo: {} (HTTP /models/{id})", modelId);
        String body = networkClient.getString("/models/" + modelId);
        Response r = new Response();
        r.setSuccess(true);
        r.setMessage("OK");
        try {
            com.google.gson.Gson gson = new com.google.gson.Gson();
            @SuppressWarnings("unchecked") Map<String, Object> data = gson.fromJson(body, Map.class);
            r.setData(data);
        } catch (Exception e) {
            r.setSuccess(false);
            r.setMessage("No se pudo parsear respuesta de modelo: " + e.getMessage());
        }
        return r;
    }

    /**
     * Sube un archivo al sistema distribuido.
     */
    public Response uploadFile(File file) throws IOException {
        logger.info("Subiendo archivo: {}", file.getName());
        byte[] fileData = Files.readAllBytes(file.toPath());
        String md5 = computeMD5Hex(fileData);
        String b64 = Base64.getEncoder().encodeToString(fileData);
        long size = fileData.length;
        String storeCmd = String.join("|",
                "STORE_FILE",
                file.getName(),
                md5,
                String.valueOf(size),
                b64
        );
        return networkClient.sendCommandText(storeCmd);
    }

    /**
     * Descarga un archivo del sistema distribuido.
     */
    public Response downloadFile(String fileName, File destination) throws IOException {
        logger.info("Descargando archivo: {} a {}", fileName, destination.getPath());
        byte[] data = networkClient.getBytes("/files/" + fileName);
        Response r = new Response();
        if (data != null && data.length > 0) {
            Files.write(destination.toPath(), data);
            logger.info("Archivo guardado: {} ({} bytes)", destination.getAbsolutePath(), data.length);
            r.setSuccess(true);
            r.setMessage("OK");
            r.addData("localPath", destination.getAbsolutePath());
            r.addData("size", data.length);
        } else {
            r.setSuccess(false);
            r.setMessage("No se recibieron datos del servidor");
        }
        return r;
    }

    /**
     * Lista todos los archivos almacenados en el sistema distribuido.
     */
    public Response listFiles() throws IOException {
        logger.info("Listando archivos del sistema (HTTP /files)");
        String body = networkClient.getString("/files");
        Response r = new Response();
        r.setSuccess(true);
        r.setMessage("OK");
        try {
            com.google.gson.Gson gson = new com.google.gson.Gson();
            @SuppressWarnings("unchecked") Map<String, Object> data = gson.fromJson(body, Map.class);
            r.setData(data);
        } catch (Exception e) {
            r.setSuccess(false);
            r.setMessage("No se pudo parsear /files: " + e.getMessage());
        }
        return r;
    }

    /**
     * Elimina un modelo del sistema.
     */
    public Response deleteModel(String modelId) throws IOException {
        logger.info("Eliminando modelo: {} (si endpoint existe)", modelId);
        // Si el monitor no soporta eliminación, retornar error claro
        Response r = new Response();
        r.setSuccess(false);
        r.setMessage("DELETE_MODEL no soportado por el monitor (use limpieza manual si aplica)");
        return r;
    }

    /**
     * Elimina un archivo del sistema.
     */
    public Response deleteFile(String fileName) throws IOException {
        logger.info("Eliminando archivo: {} (no estándar)", fileName);
        // No hay comando DELETE_FILE expuesto por monitor HTTP en docs; usar POST /command si existe:
        String cmd = String.join("|", "DELETE_FILE", fileName);
        return networkClient.sendCommandText(cmd);
    }

    /**
     * Obtiene el estado del nodo actual.
     */
    public Response getStatus() throws IOException {
        logger.info("Obteniendo estado del servidor (HTTP /status)");
        String body = networkClient.getString("/status");
        Response r = new Response();
        r.setSuccess(true);
        r.setMessage("OK");
        try {
            com.google.gson.Gson gson = new com.google.gson.Gson();
            @SuppressWarnings("unchecked") Map<String, Object> data = gson.fromJson(body, Map.class);
            r.setData(data);
        } catch (Exception e) {
            r.setSuccess(false);
            r.setMessage("No se pudo parsear /status: " + e.getMessage());
        }
        return r;
    }

    /**
     * Verifica la conexión con el servidor.
     */
    public boolean testConnection() {
        return networkClient.testConnection();
    }

    /**
     * Cambia el servidor al que se conecta.
     */
    public void setServer(String host, int port) {
        networkClient.setServer(host, port);
    }

    public String getCurrentServer() {
        return networkClient.getCurrentServer();
    }

    public NetworkClient getNetworkClient() {
        return networkClient;
    }

    // ===== Helpers =====

    private static String computeMD5Hex(byte[] bytes) throws IOException {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(bytes);
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IOException("No se pudo calcular MD5", e);
        }
    }

    private static String buildDefaultTabularHyperparams(File csv) throws IOException {
        // inputSize,outputSize,epochs,learningRate,numThreads,hasHeader,maxVocab,imageWidth,imageHeight,grayscale
        int inputSize = 0;
        boolean hasHeader = false;
        try {
            List<String> lines = Files.readAllLines(csv.toPath());
            if (!lines.isEmpty()) {
                String first = lines.get(0);
                String[] parts = first.split(",");
                // Intentar detectar si es header (no numérico)
                boolean numeric = true;
                for (String p : parts) {
                    try { Double.parseDouble(p.trim()); } catch (NumberFormatException ex) { numeric = false; break; }
                }
                if (numeric) {
                    inputSize = parts.length;
                    hasHeader = false;
                } else if (lines.size() > 1) {
                    String second = lines.get(1);
                    inputSize = second.split(",").length;
                    hasHeader = true;
                }
            }
        } catch (Exception e) {
            logger.warn("No se pudo inferir inputSize: {}", e.getMessage());
        }
        int outputSize = 2; // por defecto binario
        int epochs = 10;
        double learningRate = 0.01;
        int numThreads = 1;
        int maxVocab = 0;
        int imageWidth = 0;
        int imageHeight = 0;
        boolean grayscale = false;
        return String.join(",",
                String.valueOf(inputSize),
                String.valueOf(outputSize),
                String.valueOf(epochs),
                String.valueOf(learningRate),
                String.valueOf(numThreads),
                String.valueOf(hasHeader),
                String.valueOf(maxVocab),
                String.valueOf(imageWidth),
                String.valueOf(imageHeight),
                String.valueOf(grayscale)
        );
    }

    private static String buildDefaultImageHyperparams(int width, int height, boolean grayscale, int numClasses) {
        // inputSize se calcula automáticamente por el extractor de imágenes
        int inputSize = 0;
        int outputSize = numClasses;  // Número de clases detectadas
        int epochs = 10;
        double learningRate = 0.01;
        int numThreads = Runtime.getRuntime().availableProcessors();  // Usar todos los cores
        boolean hasHeader = false;
        int maxVocab = 0;
        return String.join(",",
                String.valueOf(inputSize),
                String.valueOf(outputSize),
                String.valueOf(epochs),
                String.valueOf(learningRate),
                String.valueOf(numThreads),
                String.valueOf(hasHeader),
                String.valueOf(maxVocab),
                String.valueOf(width),
                String.valueOf(height),
                String.valueOf(grayscale)
        );
    }

    /**
     * Cuenta el número de subdirectorios (clases) en una carpeta.
     */
    private static int countSubdirectories(File folder) {
        if (folder == null || !folder.isDirectory()) {
            return 0;
        }
        File[] files = folder.listFiles();
        if (files == null) {
            return 0;
        }
        int count = 0;
        for (File f : files) {
            if (f.isDirectory()) {
                count++;
            }
        }
        return count;
    }

    private static String safeString(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static ModelInfo mapToModelInfo(Map<String, Object> modelData) {
        ModelInfo model = new ModelInfo();
        model.setModelId(safeString(modelData.get("modelId")));
        model.setName(safeString(modelData.get("name")));
        model.setStatus(safeString(modelData.get("status")));
        model.setCreatedAt(safeString(modelData.get("createdAt")));
        if (modelData.containsKey("modelType")) model.setModelType(safeString(modelData.get("modelType")));
        if (modelData.containsKey("trainingFile")) model.setTrainingFile(safeString(modelData.get("trainingFile")));
        return model;
    }
}
