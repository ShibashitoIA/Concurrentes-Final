package com.raft.client.cli;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.raft.client.network.ClientService;
import com.raft.client.protocol.ModelInfo;
import com.raft.client.protocol.Response;

/**
 * Cliente de línea de comandos para interactuar con el sistema RAFT.
 * 
 * Uso:
 *   java -jar raft-client-cli.jar train <archivo.csv> [nombre-modelo]
 *   java -jar raft-client-cli.jar predict <modelId> <input1,input2,...>
 *   java -jar raft-client-cli.jar list-models
 *   java -jar raft-client-cli.jar upload <archivo>
 *   java -jar raft-client-cli.jar status
 */
public class CLIClient {
    private static final Logger logger = LoggerFactory.getLogger(CLIClient.class);
    private static final String CONFIG_FILE = "config.properties";

    public static void main(String[] args) {
        if (args.length == 0) {
            printUsage();
            System.exit(1);
        }

        // Cargar configuración
        Properties config = loadConfig();
        String host = config.getProperty("server.host", "localhost");
        int port = Integer.parseInt(config.getProperty("server.port", "8080"));

        ClientService clientService = new ClientService(host, port);

        try {
            String command = args[0].toLowerCase();

            switch (command) {
                case "train":
                    handleTrain(clientService, args);
                    break;
                case "train-images":
                    handleTrainImages(clientService, args);
                    break;
                case "predict":
                    handlePredict(clientService, args);
                    break;
                case "predict-image":
                    handlePredictImage(clientService, args);
                    break;
                case "list-models":
                case "list":
                    handleListModels(clientService);
                    break;
                case "get-model":
                    handleGetModel(clientService, args);
                    break;
                case "delete-model":
                    handleDeleteModel(clientService, args);
                    break;
                case "upload":
                    handleUpload(clientService, args);
                    break;
                case "download":
                    handleDownload(clientService, args);
                    break;
                case "list-files":
                    handleListFiles(clientService);
                    break;
                case "delete-file":
                    handleDeleteFile(clientService, args);
                    break;
                case "status":
                    handleStatus(clientService);
                    break;
                case "help":
                    printUsage();
                    break;
                default:
                    System.err.println("Comando desconocido: " + command);
                    printUsage();
                    System.exit(1);
            }

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            logger.error("Error ejecutando comando", e);
            System.exit(1);
        }
    }

    private static void handleTrain(ClientService clientService, String[] args) throws IOException {
        if (args.length < 2) {
            System.err.println("Uso: train <archivo.csv> [nombre-modelo]");
            System.exit(1);
        }

        String filePath = args[1];
        File file = new File(filePath);

        if (!file.exists()) {
            System.err.println("Error: El archivo no existe: " + filePath);
            System.exit(1);
        }

        // Generar ID único automáticamente (el sistema asigna el identificador)
        String prefix = args.length > 2 ? args[2] : file.getName().replaceAll("\\.[^.]+$", "");
        String uuid = UUID.randomUUID().toString().substring(0, 8);
        String modelName = prefix + "-" + uuid;

        System.out.println("Entrenando modelo con ID asignado: '" + modelName + "'");
        System.out.println("Archivo: " + file.getName());
        System.out.println("Tamaño del archivo: " + (file.length() / 1024) + " KB");
        System.out.println("Conectando al servidor: " + clientService.getCurrentServer());
        System.out.println();

        Response response = clientService.trainModel(file, modelName);

        if (response.isSuccess()) {
            System.out.println("✓ Entrenamiento iniciado exitosamente");
            System.out.println("Mensaje: " + response.getMessage());
            if (response.getData().containsKey("modelId")) {
                System.out.println("ID del modelo: " + response.getData().get("modelId"));
            }
        } else {
            System.err.println("✗ Error al entrenar el modelo");
            System.err.println("Mensaje: " + response.getMessage());
            if (response.getErrorCode() != null) {
                System.err.println("Código de error: " + response.getErrorCode());
            }
        }
    }

    private static void handleTrainImages(ClientService clientService, String[] args) throws IOException {
        if (args.length < 2) {
            System.err.println("Uso: train-images <carpeta-dataset> [nombre-modelo] [ancho] [alto] [color]");
            System.err.println("  carpeta-dataset: Carpeta con subcarpetas por clase (ej: dataset/perros, dataset/gatos)");
            System.err.println("  ancho/alto: Tamaño de redimensión (default: 28x28)");
            System.err.println("  color: true/false para RGB o escala de grises (default: false)");
            System.exit(1);
        }

        String folderPath = args[1];
        File folder = new File(folderPath);

        if (!folder.exists() || !folder.isDirectory()) {
            System.err.println("Error: La carpeta no existe o no es un directorio: " + folderPath);
            System.exit(1);
        }

        // Generar ID único automáticamente (el sistema asigna el identificador)
        String prefix = args.length > 2 ? args[2] : folder.getName();
        String uuid = UUID.randomUUID().toString().substring(0, 8);
        String modelName = prefix + "-" + uuid;
        int width = args.length > 3 ? Integer.parseInt(args[3]) : 28;
        int height = args.length > 4 ? Integer.parseInt(args[4]) : 28;
        boolean isColor = args.length > 5 ? Boolean.parseBoolean(args[5]) : false;

        System.out.println("Entrenando modelo de imágenes con ID asignado: '" + modelName + "'");
        System.out.println("Dataset: " + folder.getName());
        System.out.println("Tamaño de imagen: " + width + "x" + height);
        System.out.println("Tipo: " + (isColor ? "RGB (3 canales)" : "Escala de grises"));
        System.out.println("Conectando al servidor: " + clientService.getCurrentServer());
        System.out.println();

        Response response = clientService.trainImageModel(folder, modelName, width, height, isColor);

        if (response.isSuccess()) {
            System.out.println("✓ Entrenamiento de imágenes iniciado exitosamente");
            System.out.println("Mensaje: " + response.getMessage());
            if (response.getData().containsKey("modelId")) {
                System.out.println("ID del modelo: " + response.getData().get("modelId"));
            }
        } else {
            System.err.println("✗ Error al entrenar el modelo de imágenes");
            System.err.println("Mensaje: " + response.getMessage());
            if (response.getErrorCode() != null) {
                System.err.println("Código de error: " + response.getErrorCode());
            }
        }
    }

    private static void handlePredict(ClientService clientService, String[] args) throws IOException {
        if (args.length < 3) {
            System.err.println("Uso: predict <modelId> <input1,input2,...>");
            System.exit(1);
        }

        String modelId = args[1];
        String inputsStr = args[2];

        // Parsear inputs
        String[] inputParts = inputsStr.split(",");
        double[] inputs = new double[inputParts.length];
        
        try {
            for (int i = 0; i < inputParts.length; i++) {
                inputs[i] = Double.parseDouble(inputParts[i].trim());
            }
        } catch (NumberFormatException e) {
            System.err.println("Error: Los inputs deben ser números separados por comas");
            System.exit(1);
        }

        System.out.println("Realizando predicción con modelo: " + modelId);
        System.out.println("Inputs: " + Arrays.toString(inputs));
        System.out.println();

        Response response = clientService.predict(modelId, inputs);

        if (response.isSuccess()) {
            System.out.println("✓ Predicción exitosa");
            System.out.println("Resultado: " + response.getData().get("prediction"));
            if (response.getData().containsKey("confidence")) {
                System.out.println("Confianza: " + response.getData().get("confidence"));
            }
        } else {
            System.err.println("✗ Error en la predicción");
            System.err.println("Mensaje: " + response.getMessage());
        }
    }

    private static void handlePredictImage(ClientService clientService, String[] args) throws IOException {
        if (args.length < 3) {
            System.err.println("Uso: predict-image <modelId> <ruta-imagen>");
            System.exit(1);
        }

        String modelId = args[1];
        String imagePath = args[2];
        File imageFile = new File(imagePath);

        if (!imageFile.exists()) {
            System.err.println("Error: La imagen no existe: " + imagePath);
            System.exit(1);
        }

        System.out.println("Realizando predicción de imagen con modelo: " + modelId);
        System.out.println("Imagen: " + imageFile.getName());
        System.out.println("Tamaño: " + (imageFile.length() / 1024) + " KB");
        System.out.println();

        Response response = clientService.predictImage(modelId, imageFile);

        if (response.isSuccess()) {
            System.out.println("✓ Predicción de imagen exitosa");
            System.out.println("Clase predicha: " + response.getData().get("prediction"));
            if (response.getData().containsKey("confidence")) {
                System.out.println("Confianza: " + response.getData().get("confidence"));
            }
            if (response.getData().containsKey("probabilities")) {
                System.out.println("Probabilidades: " + response.getData().get("probabilities"));
            }
        } else {
            System.err.println("✗ Error en la predicción de imagen");
            System.err.println("Mensaje: " + response.getMessage());
        }
    }

    private static void handleListModels(ClientService clientService) throws IOException {
        System.out.println("Listando modelos disponibles...");
        System.out.println();

        List<ModelInfo> models = clientService.listModels();

        if (models.isEmpty()) {
            System.out.println("No hay modelos disponibles.");
        } else {
            System.out.println("Modelos encontrados: " + models.size());
            System.out.println();
            System.out.printf("%-20s %-30s %-15s %-20s%n", 
                "ID", "Nombre", "Estado", "Fecha Creación");
            System.out.println("-".repeat(85));

            for (ModelInfo model : models) {
                System.out.printf("%-20s %-30s %-15s %-20s%n",
                    model.getModelId(),
                    model.getName(),
                    model.getStatus(),
                    model.getCreatedAt() != null ? model.getCreatedAt() : "N/A");
            }
        }
    }

    private static void handleUpload(ClientService clientService, String[] args) throws IOException {
        if (args.length < 2) {
            System.err.println("Uso: upload <archivo>");
            System.exit(1);
        }

        String filePath = args[1];
        File file = new File(filePath);

        if (!file.exists()) {
            System.err.println("Error: El archivo no existe: " + filePath);
            System.exit(1);
        }

        System.out.println("Subiendo archivo: " + file.getName());
        System.out.println("Tamaño: " + (file.length() / 1024) + " KB");
        System.out.println();

        Response response = clientService.uploadFile(file);

        if (response.isSuccess()) {
            System.out.println("✓ Archivo subido exitosamente");
            System.out.println("Mensaje: " + response.getMessage());
        } else {
            System.err.println("✗ Error al subir el archivo");
            System.err.println("Mensaje: " + response.getMessage());
        }
    }

    private static void handleStatus(ClientService clientService) throws IOException {
        System.out.println("Consultando estado del servidor...");
        System.out.println();

        Response response = clientService.getStatus();

        if (response.isSuccess()) {
            System.out.println("✓ Estado del servidor:");
            System.out.println("Servidor: " + clientService.getCurrentServer());
            
            response.getData().forEach((key, value) -> {
                System.out.println("  " + key + ": " + value);
            });
        } else {
            System.err.println("✗ No se pudo obtener el estado");
            System.err.println("Mensaje: " + response.getMessage());
        }
    }

    private static void handleGetModel(ClientService clientService, String[] args) throws IOException {
        if (args.length < 2) {
            System.err.println("Uso: get-model <modelId>");
            System.exit(1);
        }

        String modelId = args[1];
        System.out.println("Consultando información del modelo: " + modelId);
        System.out.println();

        Response response = clientService.getModelInfo(modelId);

        if (response.isSuccess()) {
            Map<String, Object> data = response.getData();
            
            System.out.println("=".repeat(60));
            System.out.println("  DETALLES DEL MODELO " + modelId);
            System.out.println("=".repeat(60));
            System.out.println();
            System.out.println("Nombre:              " + data.getOrDefault("name", "N/A"));
            System.out.println("Estado:              " + data.getOrDefault("status", "N/A"));
            System.out.println("Tipo:                " + data.getOrDefault("modelType", "N/A"));
            System.out.println("Fecha Creación:      " + data.getOrDefault("createdAt", "N/A"));
            System.out.println();
            
            System.out.println("Arquitectura:");
            System.out.println("  • Tamaño Entrada:  " + data.getOrDefault("inputSize", "N/A") + " características");
            System.out.println("  • Tamaño Salida:   " + data.getOrDefault("outputSize", "N/A") + " clases");
            
            if (data.containsKey("imageWidth") && data.containsKey("imageHeight")) {
                System.out.println("  • Dimensiones:     " + data.get("imageWidth") + "x" + data.get("imageHeight"));
                System.out.println("  • Canales:         " + data.getOrDefault("channels", "N/A"));
            }
            System.out.println();
            
            if (data.containsKey("trainingFile")) {
                System.out.println("Entrenamiento:");
                System.out.println("  • Archivo:         " + data.get("trainingFile"));
            }
            
            if (data.containsKey("predictionCount")) {
                System.out.println();
                System.out.println("Uso:");
                System.out.println("  • Predicciones:    " + data.get("predictionCount") + " realizadas");
            }
            
            if (data.containsKey("lastUsed")) {
                System.out.println("  • Último Uso:      " + data.get("lastUsed"));
            }
            System.out.println();
            System.out.println("=".repeat(60));
        } else {
            System.err.println("✗ No se pudo obtener información del modelo");
            System.err.println("Mensaje: " + response.getMessage());
            if (response.getErrorCode() != null) {
                System.err.println("Código: " + response.getErrorCode());
            }
        }
    }

    private static void handleDeleteModel(ClientService clientService, String[] args) throws IOException {
        if (args.length < 2) {
            System.err.println("Uso: delete-model <modelId>");
            System.exit(1);
        }

        String modelId = args[1];
        
        // Confirmación de seguridad
        System.out.print("¿Estás seguro de eliminar el modelo '" + modelId + "'? (s/n): ");
        try (java.util.Scanner scanner = new java.util.Scanner(System.in)) {
            String confirm = scanner.nextLine().toLowerCase().trim();
            
            if (!confirm.equals("s") && !confirm.equals("y") && !confirm.equals("si") && !confirm.equals("yes")) {
                System.out.println("Operación cancelada.");
                return;
            }
        }

        System.out.println("Eliminando modelo: " + modelId);
        System.out.println();

        Response response = clientService.deleteModel(modelId);

        if (response.isSuccess()) {
            System.out.println("✓ Modelo eliminado exitosamente");
            System.out.println("Mensaje: " + response.getMessage());
            if (response.getData().containsKey("deletedAt")) {
                System.out.println("Fecha: " + response.getData().get("deletedAt"));
            }
        } else {
            System.err.println("✗ Error al eliminar el modelo");
            System.err.println("Mensaje: " + response.getMessage());
            if (response.getErrorCode() != null) {
                System.err.println("Código: " + response.getErrorCode());
            }
        }
    }

    private static void handleDownload(ClientService clientService, String[] args) throws IOException {
        if (args.length < 2) {
            System.err.println("Uso: download <nombre-archivo> [ruta-destino]");
            System.exit(1);
        }

        String fileName = args[1];
        String destPath = args.length > 2 ? args[2] : "./";
        
        File destDir = new File(destPath);
        if (!destDir.exists()) {
            destDir.mkdirs();
        }
        
        File destination = destDir.isDirectory() ? 
            new File(destDir, fileName) : new File(destPath);

        System.out.println("Descargando archivo: " + fileName);
        System.out.println("Destino: " + destination.getAbsolutePath());
        System.out.println();

        Response response = clientService.downloadFile(fileName, destination);

        if (response.isSuccess()) {
            System.out.println("✓ Archivo descargado exitosamente");
            System.out.println("Ubicación: " + destination.getAbsolutePath());
            
            if (destination.exists()) {
                long size = destination.length();
                System.out.println("Tamaño: " + formatSize(size));
            }
        } else {
            System.err.println("✗ Error al descargar el archivo");
            System.err.println("Mensaje: " + response.getMessage());
            if (response.getErrorCode() != null) {
                System.err.println("Código: " + response.getErrorCode());
            }
        }
    }

    private static void handleListFiles(ClientService clientService) throws IOException {
        System.out.println("Listando archivos disponibles en el sistema...");
        System.out.println();

        Response response = clientService.listFiles();

        if (response.isSuccess()) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> files = (List<Map<String, Object>>) 
                response.getData().getOrDefault("files", new ArrayList<>());
            
            if (files.isEmpty()) {
                System.out.println("No hay archivos disponibles.");
            } else {
                System.out.println("Archivos encontrados: " + files.size());
                System.out.println();
                System.out.printf("%-40s %-15s %-20s%n", "Nombre", "Tamaño", "Fecha Subida");
                System.out.println("-".repeat(75));

                for (Map<String, Object> file : files) {
                    String name = (String) file.getOrDefault("fileName", "N/A");
                    Object sizeObj = file.get("size");
                    long size = sizeObj instanceof Number ? ((Number) sizeObj).longValue() : 0;
                    String date = (String) file.getOrDefault("uploadedAt", "N/A");
                    
                    System.out.printf("%-40s %-15s %-20s%n",
                        truncate(name, 40),
                        formatSize(size),
                        date);
                }
                
                System.out.println("-".repeat(75));
                
                // Mostrar totales si están disponibles
                if (response.getData().containsKey("totalSize")) {
                    long totalSize = ((Number) response.getData().get("totalSize")).longValue();
                    System.out.println("Tamaño total: " + formatSize(totalSize));
                }
            }
        } else {
            System.err.println("✗ No se pudo listar archivos");
            System.err.println("Mensaje: " + response.getMessage());
            if (response.getErrorCode() != null) {
                System.err.println("Código: " + response.getErrorCode());
            }
        }
    }

    private static void handleDeleteFile(ClientService clientService, String[] args) throws IOException {
        if (args.length < 2) {
            System.err.println("Uso: delete-file <nombre-archivo>");
            System.exit(1);
        }

        String fileName = args[1];
        
        // Confirmación de seguridad
        System.out.print("¿Estás seguro de eliminar el archivo '" + fileName + "'? (s/n): ");
        try (java.util.Scanner scanner = new java.util.Scanner(System.in)) {
            String confirm = scanner.nextLine().toLowerCase().trim();
            
            if (!confirm.equals("s") && !confirm.equals("y") && !confirm.equals("si") && !confirm.equals("yes")) {
                System.out.println("Operación cancelada.");
                return;
            }
        }

        System.out.println("Eliminando archivo: " + fileName);
        System.out.println();

        Response response = clientService.deleteFile(fileName);

        if (response.isSuccess()) {
            System.out.println("✓ Archivo eliminado exitosamente");
            System.out.println("Mensaje: " + response.getMessage());
        } else {
            System.err.println("✗ Error al eliminar el archivo");
            System.err.println("Mensaje: " + response.getMessage());
            if (response.getErrorCode() != null) {
                System.err.println("Código: " + response.getErrorCode());
            }
        }
    }

    private static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        return String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0));
    }

    private static String truncate(String str, int maxLength) {
        if (str == null) return "N/A";
        if (str.length() <= maxLength) return str;
        return str.substring(0, maxLength - 3) + "...";
    }

    private static Properties loadConfig() {
        Properties config = new Properties();
        
        try (FileInputStream fis = new FileInputStream(CONFIG_FILE)) {
            config.load(fis);
            logger.info("Configuración cargada desde: {}", CONFIG_FILE);
        } catch (IOException e) {
            logger.warn("No se pudo cargar {}, usando valores por defecto", CONFIG_FILE);
        }

        return config;
    }

    private static void printUsage() {
        System.out.println("╔═══════════════════════════════════════════════════════════════════╗");
        System.out.println("║        Cliente CLI para Sistema RAFT Distribuido con IA          ║");
        System.out.println("╚═══════════════════════════════════════════════════════════════════╝");
        System.out.println();
        System.out.println("COMANDOS DE ENTRENAMIENTO:");
        System.out.println("  train <archivo.csv> [nombre-modelo]              Entrena modelo con CSV");
        System.out.println("  train-images <carpeta> [nombre] [W] [H] [color]  Entrena CNN con imágenes");
        System.out.println();
        System.out.println("COMANDOS DE PREDICCIÓN:");
        System.out.println("  predict <modelId> <input1,input2,...>            Predicción numérica");
        System.out.println("  predict-image <modelId> <imagen.jpg>             Predicción con imagen");
        System.out.println();
        System.out.println("GESTIÓN DE MODELOS:");
        System.out.println("  list-models                                      Lista todos los modelos");
        System.out.println("  get-model <modelId>                              Ver detalles de un modelo");
        System.out.println("  delete-model <modelId>                           Eliminar modelo");
        System.out.println();
        System.out.println("GESTIÓN DE ARCHIVOS:");
        System.out.println("  upload <archivo>                                 Subir archivo al sistema");
        System.out.println("  download <archivo> [destino]                     Descargar archivo");
        System.out.println("  list-files                                       Listar archivos disponibles");
        System.out.println("  delete-file <archivo>                            Eliminar archivo");
        System.out.println();
        System.out.println("SISTEMA:");
        System.out.println("  status                                           Estado del servidor RAFT");
        System.out.println("  help                                             Muestra esta ayuda");
        System.out.println();
        System.out.println("═══════════════════════════════════════════════════════════════════");
        System.out.println("EJEMPLOS DE USO:");
        System.out.println("═══════════════════════════════════════════════════════════════════");
        System.out.println();
        System.out.println("  # Entrenar con datos numéricos");
        System.out.println("  ./run-cli.sh train datos.csv mi-modelo");
        System.out.println("  ./run-cli.sh predict MODEL-123 \"1.5,2.3,4.1,3.2\"");
        System.out.println();
        System.out.println("  # Entrenar con imágenes (MNIST 28x28 escala de grises)");
        System.out.println("  ./run-cli.sh train-images ./dataset/mnist mnist 28 28 false");
        System.out.println("  ./run-cli.sh predict-image MODEL-IMG-456 test-digit.png");
        System.out.println();
        System.out.println("  # Gestión de modelos");
        System.out.println("  ./run-cli.sh list-models");
        System.out.println("  ./run-cli.sh get-model MODEL-123");
        System.out.println("  ./run-cli.sh delete-model MODEL-OLD-456");
        System.out.println();
        System.out.println("  # Gestión de archivos");
        System.out.println("  ./run-cli.sh upload mi-dataset.csv");
        System.out.println("  ./run-cli.sh list-files");
        System.out.println("  ./run-cli.sh download resultado.csv ./descargas/");
        System.out.println("  ./run-cli.sh delete-file archivo-viejo.txt");
        System.out.println();
        System.out.println("  # Estado del sistema");
        System.out.println("  ./run-cli.sh status");
        System.out.println();
        System.out.println("═══════════════════════════════════════════════════════════════════");
        System.out.println("CONFIGURACIÓN:");
        System.out.println("═══════════════════════════════════════════════════════════════════");
        System.out.println();
        System.out.println("  Edita 'config.properties' para cambiar el servidor:");
        System.out.println();
        System.out.println("    server.host=localhost");
        System.out.println("    server.port=8080");
        System.out.println();
    }
}
