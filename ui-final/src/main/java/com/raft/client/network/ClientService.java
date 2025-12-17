package com.raft.client.network;

import com.raft.client.protocol.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
     * Entrena un modelo con un archivo de datos CSV (numéricos).
     */
    public Response trainModel(File trainingFile, String modelName) throws IOException {
        logger.info("Entrenando modelo '{}' con archivo: {}", modelName, trainingFile.getName());

        Command command = new Command(CommandType.TRAIN);
        command.addParameter("modelName", modelName);
        command.addParameter("fileName", trainingFile.getName());
        command.addParameter("modelType", "NUMERIC");

        // Leer el archivo
        byte[] fileData = Files.readAllBytes(trainingFile.toPath());
        command.setData(fileData);

        return networkClient.sendCommand(command);
    }

    /**
     * Entrena un modelo con un dataset de imágenes (carpeta con subcarpetas por clase).
     */
    public Response trainImageModel(File imageFolder, String modelName, int width, int height, boolean isColor) throws IOException {
        logger.info("Entrenando modelo de imágenes '{}' desde: {}", modelName, imageFolder.getName());

        Command command = new Command(CommandType.TRAIN_IMAGE);
        command.addParameter("modelName", modelName);
        command.addParameter("folderName", imageFolder.getName());
        command.addParameter("imageWidth", String.valueOf(width));
        command.addParameter("imageHeight", String.valueOf(height));
        command.addParameter("channels", isColor ? "3" : "1");
        command.addParameter("modelType", "IMAGE_CNN");

        // En una implementación real, aquí se comprimirían las imágenes en un ZIP
        // Por ahora, indicamos la ruta de la carpeta
        String folderPath = imageFolder.getAbsolutePath();
        command.setData(folderPath.getBytes());

        return networkClient.sendCommand(command);
    }

    /**
     * Realiza una predicción con un modelo usando datos numéricos.
     */
    public Response predict(String modelId, double[] inputs) throws IOException {
        logger.info("Predicción con modelo: {}", modelId);

        Command command = new Command(CommandType.PREDICT);
        command.addParameter("modelId", modelId);

        // Convertir inputs a string
        StringBuilder inputStr = new StringBuilder();
        for (int i = 0; i < inputs.length; i++) {
            if (i > 0) inputStr.append(",");
            inputStr.append(inputs[i]);
        }
        command.addParameter("inputs", inputStr.toString());

        return networkClient.sendCommand(command);
    }

    /**
     * Realiza una predicción con una imagen.
     */
    public Response predictImage(String modelId, File imageFile) throws IOException {
        logger.info("Predicción de imagen con modelo: {}", modelId);

        Command command = new Command(CommandType.PREDICT_IMAGE);
        command.addParameter("modelId", modelId);
        command.addParameter("fileName", imageFile.getName());

        // Leer la imagen
        byte[] imageData = Files.readAllBytes(imageFile.toPath());
        command.setData(imageData);

        return networkClient.sendCommand(command);
    }

    /**
     * Lista los modelos disponibles.
     */
    public List<ModelInfo> listModels() throws IOException {
        logger.info("Listando modelos disponibles");

        Command command = new Command(CommandType.LIST_MODELS);
        Response response = networkClient.sendCommand(command);

        List<ModelInfo> models = new ArrayList<>();
        if (response.isSuccess() && response.getData().containsKey("models")) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> modelsData = 
                (List<Map<String, Object>>) response.getData().get("models");
            
            for (Map<String, Object> modelData : modelsData) {
                ModelInfo model = new ModelInfo();
                model.setModelId((String) modelData.get("modelId"));
                model.setName((String) modelData.get("name"));
                model.setStatus((String) modelData.get("status"));
                model.setCreatedAt((String) modelData.get("createdAt"));
                models.add(model);
            }
        }

        return models;
    }

    /**
     * Obtiene información de un modelo específico.
     */
    public Response getModelInfo(String modelId) throws IOException {
        logger.info("Obteniendo información del modelo: {}", modelId);

        Command command = new Command(CommandType.GET_MODEL);
        command.addParameter("modelId", modelId);

        return networkClient.sendCommand(command);
    }

    /**
     * Sube un archivo al sistema distribuido.
     */
    public Response uploadFile(File file) throws IOException {
        logger.info("Subiendo archivo: {}", file.getName());

        Command command = new Command(CommandType.STORE_FILE);
        command.addParameter("fileName", file.getName());

        byte[] fileData = Files.readAllBytes(file.toPath());
        command.setData(fileData);

        return networkClient.sendCommand(command);
    }

    /**
     * Descarga un archivo del sistema distribuido.
     */
    public Response downloadFile(String fileName, File destination) throws IOException {
        logger.info("Descargando archivo: {} a {}", fileName, destination.getPath());

        Command command = new Command(CommandType.DOWNLOAD_FILE);
        command.addParameter("fileName", fileName);

        Response response = networkClient.sendCommand(command);

        // Guardar datos binarios si se recibieron
        if (response.isSuccess() && response.getBinaryData() != null) {
            byte[] fileData = response.getBinaryData();
            Files.write(destination.toPath(), fileData);
            logger.info("Archivo guardado: {} ({} bytes)", 
                destination.getAbsolutePath(), fileData.length);
            
            // Agregar información al response
            response.addData("localPath", destination.getAbsolutePath());
            response.addData("size", fileData.length);
        } else if (response.isSuccess()) {
            logger.warn("No se recibieron datos binarios para el archivo: {}", fileName);
        }
        
        return response;
    }

    /**
     * Lista todos los archivos almacenados en el sistema distribuido.
     */
    public Response listFiles() throws IOException {
        logger.info("Listando archivos del sistema");

        Command command = new Command(CommandType.LIST_FILES);
        return networkClient.sendCommand(command);
    }

    /**
     * Elimina un modelo del sistema.
     */
    public Response deleteModel(String modelId) throws IOException {
        logger.info("Eliminando modelo: {}", modelId);

        Command command = new Command(CommandType.DELETE_MODEL);
        command.addParameter("modelId", modelId);
        
        return networkClient.sendCommand(command);
    }

    /**
     * Elimina un archivo del sistema.
     */
    public Response deleteFile(String fileName) throws IOException {
        logger.info("Eliminando archivo: {}", fileName);

        Command command = new Command(CommandType.DELETE_FILE);
        command.addParameter("fileName", fileName);
        
        return networkClient.sendCommand(command);
    }

    /**
     * Obtiene el estado del nodo actual.
     */
    public Response getStatus() throws IOException {
        logger.info("Obteniendo estado del servidor");

        Command command = new Command(CommandType.STATUS);
        return networkClient.sendCommand(command);
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
}
