package com.raft.client.ui;

import com.raft.client.network.ClientService;
import com.raft.client.protocol.Response;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.io.File;

/**
 * Panel para entrenar modelos de IA.
 */
public class TrainPanel extends VBox {
    private ClientService clientService;
    private TextField fileField;
    private TextField modelNameField;
    private TextArea logArea;
    private ProgressIndicator progressIndicator;
    private Button trainButton;

    public TrainPanel(ClientService clientService) {
        this.clientService = clientService;
        setupUI();
    }

    private void setupUI() {
        setPadding(new Insets(20));
        setSpacing(15);

        Label titleLabel = new Label("Entrenar Modelo de IA");
        titleLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        // Selector de archivo
        GridPane formGrid = new GridPane();
        formGrid.setHgap(10);
        formGrid.setVgap(10);

        Label fileLabel = new Label("Archivo de entrenamiento:");
        fileField = new TextField();
        fileField.setPromptText("Selecciona un archivo CSV...");
        fileField.setPrefWidth(300);
        fileField.setEditable(false);

        Button browseButton = new Button("Examinar...");
        browseButton.setOnAction(e -> selectFile());

        HBox fileBox = new HBox(10, fileField, browseButton);

        Label nameLabel = new Label("Nombre del modelo:");
        modelNameField = new TextField();
        modelNameField.setPromptText("mi-modelo");
        modelNameField.setPrefWidth(300);

        formGrid.add(fileLabel, 0, 0);
        formGrid.add(fileBox, 1, 0);
        formGrid.add(nameLabel, 0, 1);
        formGrid.add(modelNameField, 1, 1);

        // Botón de entrenamiento
        trainButton = new Button("Iniciar Entrenamiento");
        trainButton.setStyle("-fx-background-color: #3498db; -fx-text-fill: white; -fx-font-size: 14px;");
        trainButton.setOnAction(e -> startTraining());

        progressIndicator = new ProgressIndicator();
        progressIndicator.setVisible(false);
        progressIndicator.setPrefSize(30, 30);

        HBox buttonBox = new HBox(10, trainButton, progressIndicator);
        buttonBox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        // Área de log
        Label logLabel = new Label("Registro:");
        logArea = new TextArea();
        logArea.setEditable(false);
        logArea.setPrefRowCount(12);
        logArea.setWrapText(true);

        getChildren().addAll(titleLabel, formGrid, buttonBox, logLabel, logArea);
    }

    private void selectFile() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Seleccionar archivo de entrenamiento");
        fileChooser.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("Archivos CSV", "*.csv"),
            new FileChooser.ExtensionFilter("Todos los archivos", "*.*")
        );

        File file = fileChooser.showOpenDialog(getScene().getWindow());
        if (file != null) {
            fileField.setText(file.getAbsolutePath());
            
            // Sugerir nombre del modelo basado en el archivo
            if (modelNameField.getText().isEmpty()) {
                String suggestedName = file.getName().replaceAll("\\.[^.]+$", "");
                modelNameField.setText(suggestedName);
            }
        }
    }

    private void startTraining() {
        String filePath = fileField.getText();
        String modelName = modelNameField.getText().trim();

        if (filePath.isEmpty()) {
            showAlert(Alert.AlertType.ERROR, "Error", "Debes seleccionar un archivo de entrenamiento");
            return;
        }

        if (modelName.isEmpty()) {
            showAlert(Alert.AlertType.ERROR, "Error", "Debes especificar un nombre para el modelo");
            return;
        }

        File file = new File(filePath);
        if (!file.exists()) {
            showAlert(Alert.AlertType.ERROR, "Error", "El archivo no existe");
            return;
        }

        trainButton.setDisable(true);
        progressIndicator.setVisible(true);
        logArea.appendText("=== Iniciando entrenamiento ===\n");
        logArea.appendText("Archivo: " + file.getName() + "\n");
        logArea.appendText("Tamaño: " + (file.length() / 1024) + " KB\n");
        logArea.appendText("Modelo: " + modelName + "\n");
        logArea.appendText("Servidor: " + clientService.getCurrentServer() + "\n\n");

        new Thread(() -> {
            try {
                Response response = clientService.trainModel(file, modelName);

                Platform.runLater(() -> {
                    if (response.isSuccess()) {
                        logArea.appendText("✓ Entrenamiento iniciado exitosamente\n");
                        logArea.appendText("Mensaje: " + response.getMessage() + "\n");
                        logArea.appendText("(Se envió STORE_FILE con MD5 y TRAIN_MODEL TABULAR con 10 hiperparámetros)\n");
                        if (response.getData().containsKey("modelId")) {
                            logArea.appendText("ID del modelo: " + response.getData().get("modelId") + "\n");
                        }
                        logArea.appendText("\n");
                        
                        showAlert(Alert.AlertType.INFORMATION, "Éxito", 
                            "El entrenamiento ha sido iniciado correctamente");
                    } else {
                        logArea.appendText("✗ Error: " + response.getMessage() + "\n");
                        if (response.getErrorCode() != null) {
                            logArea.appendText("Código: " + response.getErrorCode() + "\n");
                        }
                        logArea.appendText("\n");
                        
                        showAlert(Alert.AlertType.ERROR, "Error", response.getMessage());
                    }

                    trainButton.setDisable(false);
                    progressIndicator.setVisible(false);
                });

            } catch (Exception e) {
                Platform.runLater(() -> {
                    logArea.appendText("✗ Error de conexión: " + e.getMessage() + "\n\n");
                    showAlert(Alert.AlertType.ERROR, "Error de Conexión", e.getMessage());
                    trainButton.setDisable(false);
                    progressIndicator.setVisible(false);
                });
            }
        }).start();
    }

    private void showAlert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
