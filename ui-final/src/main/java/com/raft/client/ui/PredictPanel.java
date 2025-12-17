package com.raft.client.ui;

import com.raft.client.network.ClientService;
import com.raft.client.protocol.Response;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/**
 * Panel para realizar predicciones con modelos entrenados.
 */
public class PredictPanel extends VBox {
    private ClientService clientService;
    private TextField modelIdField;
    private TextField inputsField;
    private TextArea resultArea;
    private ProgressIndicator progressIndicator;
    private Button predictButton;

    public PredictPanel(ClientService clientService) {
        this.clientService = clientService;
        setupUI();
    }

    private void setupUI() {
        setPadding(new Insets(20));
        setSpacing(15);

        Label titleLabel = new Label("Realizar Predicción");
        titleLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        // Formulario
        GridPane formGrid = new GridPane();
        formGrid.setHgap(10);
        formGrid.setVgap(10);

        Label modelLabel = new Label("ID del Modelo:");
        modelIdField = new TextField();
        modelIdField.setPromptText("MODEL-123");
        modelIdField.setPrefWidth(300);

        Label inputsLabel = new Label("Valores de entrada:");
        inputsField = new TextField();
        inputsField.setPromptText("1.5, 2.3, 4.1, ...");
        inputsField.setPrefWidth(300);

        Label hintLabel = new Label("(Separados por comas)");
        hintLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: gray;");

        formGrid.add(modelLabel, 0, 0);
        formGrid.add(modelIdField, 1, 0);
        formGrid.add(inputsLabel, 0, 1);
        formGrid.add(inputsField, 1, 1);
        formGrid.add(hintLabel, 1, 2);

        // Botón de predicción
        predictButton = new Button("Predecir");
        predictButton.setStyle("-fx-background-color: #2ecc71; -fx-text-fill: white; -fx-font-size: 14px;");
        predictButton.setOnAction(e -> performPrediction());

        progressIndicator = new ProgressIndicator();
        progressIndicator.setVisible(false);
        progressIndicator.setPrefSize(30, 30);

        HBox buttonBox = new HBox(10, predictButton, progressIndicator);
        buttonBox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        // Área de resultados
        Label resultLabel = new Label("Resultado:");
        resultArea = new TextArea();
        resultArea.setEditable(false);
        resultArea.setPrefRowCount(12);
        resultArea.setWrapText(true);

        getChildren().addAll(titleLabel, formGrid, buttonBox, resultLabel, resultArea);
    }

    private void performPrediction() {
        String modelId = modelIdField.getText().trim();
        String inputsStr = inputsField.getText().trim();

        if (modelId.isEmpty()) {
            showAlert(Alert.AlertType.ERROR, "Error", "Debes especificar el ID del modelo");
            return;
        }

        if (inputsStr.isEmpty()) {
            showAlert(Alert.AlertType.ERROR, "Error", "Debes proporcionar los valores de entrada");
            return;
        }

        // Parsear inputs
        String[] inputParts = inputsStr.split(",");
        double[] inputs = new double[inputParts.length];

        try {
            for (int i = 0; i < inputParts.length; i++) {
                inputs[i] = Double.parseDouble(inputParts[i].trim());
            }
        } catch (NumberFormatException e) {
            showAlert(Alert.AlertType.ERROR, "Error", 
                "Los valores de entrada deben ser números válidos separados por comas");
            return;
        }

        predictButton.setDisable(true);
        progressIndicator.setVisible(true);
        resultArea.appendText("=== Realizando predicción ===\n");
        resultArea.appendText("Modelo: " + modelId + "\n");
        resultArea.appendText("Inputs: " + inputsStr + "\n");
        resultArea.appendText("Cantidad de valores: " + inputs.length + "\n\n");

        new Thread(() -> {
            try {
                Response response = clientService.predict(modelId, inputs);

                Platform.runLater(() -> {
                    if (response.isSuccess()) {
                        resultArea.appendText("✓ Predicción exitosa\n");
                        resultArea.appendText("Resultado: " + response.getData().get("prediction") + "\n");
                        
                        if (response.getData().containsKey("confidence")) {
                            resultArea.appendText("Confianza: " + response.getData().get("confidence") + "\n");
                        }
                        
                        if (response.getData().containsKey("output")) {
                            resultArea.appendText("Output completo: " + response.getData().get("output") + "\n");
                        }
                        
                        resultArea.appendText("\n");
                        
                    } else {
                        resultArea.appendText("✗ Error: " + response.getMessage() + "\n");
                        if (response.getErrorCode() != null) {
                            resultArea.appendText("Código: " + response.getErrorCode() + "\n");
                        }
                        resultArea.appendText("\n");
                        
                        showAlert(Alert.AlertType.ERROR, "Error", response.getMessage());
                    }

                    predictButton.setDisable(false);
                    progressIndicator.setVisible(false);
                });

            } catch (Exception e) {
                Platform.runLater(() -> {
                    resultArea.appendText("✗ Error de conexión: " + e.getMessage() + "\n\n");
                    showAlert(Alert.AlertType.ERROR, "Error de Conexión", e.getMessage());
                    predictButton.setDisable(false);
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
