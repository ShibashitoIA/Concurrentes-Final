package com.raft.client.ui;

import com.raft.client.network.ClientService;
import com.raft.client.protocol.Response;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.io.File;

/**
 * Panel para gestionar archivos (subir/descargar).
 */
public class FilesPanel extends VBox {
    private ClientService clientService;
    private TextArea logArea;
    private ProgressIndicator progressIndicator;

    public FilesPanel(ClientService clientService) {
        this.clientService = clientService;
        setupUI();
    }

    private void setupUI() {
        setPadding(new Insets(20));
        setSpacing(15);

        Label titleLabel = new Label("Gestión de Archivos");
        titleLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        // Sección de subida
        Label uploadLabel = new Label("Subir Archivo:");
        uploadLabel.setStyle("-fx-font-weight: bold;");

        Button uploadButton = new Button("Seleccionar y Subir Archivo");
        uploadButton.setStyle("-fx-background-color: #3498db; -fx-text-fill: white;");
        uploadButton.setOnAction(e -> uploadFile());

        progressIndicator = new ProgressIndicator();
        progressIndicator.setVisible(false);
        progressIndicator.setPrefSize(30, 30);

        HBox uploadBox = new HBox(10, uploadButton, progressIndicator);
        uploadBox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        Separator separator = new Separator();

        // Sección de descarga
        Label downloadLabel = new Label("Descargar Archivo:");
        downloadLabel.setStyle("-fx-font-weight: bold;");

        TextField fileNameField = new TextField();
        fileNameField.setPromptText("Nombre del archivo a descargar");
        fileNameField.setPrefWidth(300);

        Button downloadButton = new Button("Descargar");
        downloadButton.setStyle("-fx-background-color: #27ae60; -fx-text-fill: white;");
        downloadButton.setOnAction(e -> downloadFile(fileNameField.getText()));

        Button listFilesButton = new Button("Ver Archivos Disponibles");
        listFilesButton.setStyle("-fx-background-color: #8e44ad; -fx-text-fill: white;");
        listFilesButton.setOnAction(e -> showAvailableFiles());

        HBox downloadBox = new HBox(10, fileNameField, downloadButton, listFilesButton);
        downloadBox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        Separator separator2 = new Separator();

        // Área de log
        Label logLabel = new Label("Registro de operaciones:");
        logArea = new TextArea();
        logArea.setEditable(false);
        logArea.setPrefRowCount(12);
        logArea.setWrapText(true);

        getChildren().addAll(
            titleLabel, 
            uploadLabel, uploadBox, 
            separator, 
            downloadLabel, downloadBox,
            separator2, 
            logLabel, logArea
        );
    }

    private void uploadFile() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Seleccionar archivo para subir");
        fileChooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("Todos los archivos", "*.*")
        );

        File file = fileChooser.showOpenDialog(getScene().getWindow());
        if (file == null) {
            return;
        }

        progressIndicator.setVisible(true);
        logArea.appendText("=== Subiendo archivo ===\n");
        logArea.appendText("Archivo: " + file.getName() + "\n");
        logArea.appendText("Tamaño: " + (file.length() / 1024) + " KB\n");
        logArea.appendText("Servidor: " + clientService.getCurrentServer() + "\n\n");

        new Thread(() -> {
            try {
                Response response = clientService.uploadFile(file);

                Platform.runLater(() -> {
                    if (response.isSuccess()) {
                        logArea.appendText("✓ Archivo subido exitosamente\n");
                        logArea.appendText("Mensaje: " + response.getMessage() + "\n");
                        logArea.appendText("\n");
                        
                        showAlert(Alert.AlertType.INFORMATION, "Éxito", 
                            "Archivo subido correctamente");
                    } else {
                        logArea.appendText("✗ Error: " + response.getMessage() + "\n");
                        if (response.getErrorCode() != null) {
                            logArea.appendText("Código: " + response.getErrorCode() + "\n");
                        }
                        logArea.appendText("\n");
                        
                        showAlert(Alert.AlertType.ERROR, "Error", response.getMessage());
                    }

                    progressIndicator.setVisible(false);
                });

            } catch (Exception e) {
                Platform.runLater(() -> {
                    logArea.appendText("✗ Error de conexión: " + e.getMessage() + "\n\n");
                    showAlert(Alert.AlertType.ERROR, "Error de Conexión", e.getMessage());
                    progressIndicator.setVisible(false);
                });
            }
        }).start();
    }

    private void downloadFile(String fileName) {
        if (fileName == null || fileName.trim().isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Advertencia", 
                "Por favor ingresa el nombre del archivo a descargar");
            return;
        }

        fileName = fileName.trim();

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Guardar archivo como");
        fileChooser.setInitialFileName(fileName);
        fileChooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("Todos los archivos", "*.*")
        );
        
        File destination = fileChooser.showSaveDialog(getScene().getWindow());
        if (destination == null) {
            return;
        }

        progressIndicator.setVisible(true);
        logArea.appendText("=== Descargando archivo ===\n");
        logArea.appendText("Archivo: " + fileName + "\n");
        logArea.appendText("Destino: " + destination.getAbsolutePath() + "\n");
        logArea.appendText("Servidor: " + clientService.getCurrentServer() + "\n\n");

        final String finalFileName = fileName;
        new Thread(() -> {
            try {
                Response response = clientService.downloadFile(finalFileName, destination);

                Platform.runLater(() -> {
                    if (response.isSuccess()) {
                        long size = destination.exists() ? destination.length() : 0;
                        logArea.appendText("✓ Archivo descargado exitosamente\n");
                        logArea.appendText("Ubicación: " + destination.getAbsolutePath() + "\n");
                        logArea.appendText("Tamaño: " + formatSize(size) + "\n\n");
                        
                        showAlert(Alert.AlertType.INFORMATION, "Éxito", 
                            "Archivo descargado correctamente\n\n" +
                            "Ubicación: " + destination.getAbsolutePath());
                    } else {
                        logArea.appendText("✗ Error: " + response.getMessage() + "\n");
                        if (response.getErrorCode() != null) {
                            logArea.appendText("Código: " + response.getErrorCode() + "\n");
                        }
                        logArea.appendText("\n");
                        
                        showAlert(Alert.AlertType.ERROR, "Error", response.getMessage());
                    }
                    progressIndicator.setVisible(false);
                });

            } catch (Exception e) {
                Platform.runLater(() -> {
                    logArea.appendText("✗ Error de conexión: " + e.getMessage() + "\n\n");
                    showAlert(Alert.AlertType.ERROR, "Error de Conexión", e.getMessage());
                    progressIndicator.setVisible(false);
                });
            }
        }).start();
    }

    private void showAvailableFiles() {
        progressIndicator.setVisible(true);
        logArea.appendText("=== Consultando archivos disponibles ===\n");

        new Thread(() -> {
            try {
                Response response = clientService.listFiles();

                Platform.runLater(() -> {
                    if (response.isSuccess()) {
                        @SuppressWarnings("unchecked")
                        java.util.List<java.util.Map<String, Object>> files = 
                            (java.util.List<java.util.Map<String, Object>>) 
                            response.getData().getOrDefault("files", new java.util.ArrayList<>());
                        
                        logArea.appendText("✓ Se encontraron " + files.size() + " archivo(s)\n\n");
                        
                        // Crear diálogo con lista de archivos
                        Alert dialog = new Alert(Alert.AlertType.INFORMATION);
                        dialog.setTitle("Archivos Disponibles");
                        dialog.setHeaderText("Archivos en el sistema distribuido");
                        
                        if (files.isEmpty()) {
                            dialog.setContentText("No hay archivos disponibles en el sistema.");
                        } else {
                            StringBuilder content = new StringBuilder();
                            content.append(String.format("Total: %d archivo(s)\n\n", files.size()));
                            content.append(String.format("%-40s %12s\n", "Nombre", "Tamaño"));
                            content.append("-".repeat(54)).append("\n");
                            
                            for (java.util.Map<String, Object> file : files) {
                                String name = (String) file.getOrDefault("fileName", "N/A");
                                Object sizeObj = file.get("size");
                                long size = sizeObj instanceof Number ? 
                                    ((Number) sizeObj).longValue() : 0;
                                
                                String truncName = name.length() > 40 ? 
                                    name.substring(0, 37) + "..." : name;
                                content.append(String.format("%-40s %12s\n", 
                                    truncName, formatSize(size)));
                            }
                            
                            if (response.getData().containsKey("totalSize")) {
                                long totalSize = ((Number) response.getData()
                                    .get("totalSize")).longValue();
                                content.append("-".repeat(54)).append("\n");
                                content.append(String.format("%-40s %12s\n", 
                                    "TOTAL", formatSize(totalSize)));
                            }
                            
                            dialog.setContentText(content.toString());
                        }
                        
                        // Hacer el diálogo más grande
                        dialog.getDialogPane().setPrefWidth(600);
                        dialog.showAndWait();
                        
                    } else {
                        logArea.appendText("✗ Error: " + response.getMessage() + "\n\n");
                        showAlert(Alert.AlertType.ERROR, "Error", 
                            "No se pudo obtener la lista de archivos:\n" + 
                            response.getMessage());
                    }
                    progressIndicator.setVisible(false);
                });

            } catch (Exception e) {
                Platform.runLater(() -> {
                    logArea.appendText("✗ Error: " + e.getMessage() + "\n\n");
                    showAlert(Alert.AlertType.ERROR, "Error de Conexión", e.getMessage());
                    progressIndicator.setVisible(false);
                });
            }
        }).start();
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", 
            bytes / (1024.0 * 1024.0));
        return String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0));
    }

    private void showAlert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
