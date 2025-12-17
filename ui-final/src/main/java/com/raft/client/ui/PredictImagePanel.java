package com.raft.client.ui;

import com.raft.client.network.ClientService;
import com.raft.client.protocol.Response;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.io.File;
import java.io.FileInputStream;

/**
 * Panel para realizar predicciones con imágenes.
 */
public class PredictImagePanel extends VBox {
    private ClientService clientService;
    private TextField modelIdField;
    private TextField imageField;
    private ImageView imagePreview;
    private TextArea resultArea;
    private ProgressIndicator progressIndicator;
    private Button predictButton;
    private File selectedImage;

    public PredictImagePanel(ClientService clientService) {
        this.clientService = clientService;
        setupUI();
    }

    private void setupUI() {
        setPadding(new Insets(20));
        setSpacing(15);

        Label titleLabel = new Label("Predicción con Imagen");
        titleLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        // Formulario
        GridPane formGrid = new GridPane();
        formGrid.setHgap(10);
        formGrid.setVgap(10);

        Label modelLabel = new Label("ID del Modelo:");
        modelIdField = new TextField();
        modelIdField.setPromptText("MODEL-IMG-123");
        modelIdField.setPrefWidth(300);

        Label imageLabel = new Label("Imagen:");
        imageField = new TextField();
        imageField.setPromptText("Selecciona una imagen...");
        imageField.setPrefWidth(250);
        imageField.setEditable(false);

        Button browseButton = new Button("Examinar...");
        browseButton.setOnAction(e -> selectImage());

        HBox imageBox = new HBox(10, imageField, browseButton);

        formGrid.add(modelLabel, 0, 0);
        formGrid.add(modelIdField, 1, 0);
        formGrid.add(imageLabel, 0, 1);
        formGrid.add(imageBox, 1, 1);

        // Vista previa de imagen
        Label previewLabel = new Label("Vista previa:");
        imagePreview = new ImageView();
        imagePreview.setFitWidth(200);
        imagePreview.setFitHeight(200);
        imagePreview.setPreserveRatio(true);
        imagePreview.setStyle("-fx-border-color: #ccc; -fx-border-width: 1;");

        VBox previewBox = new VBox(5, previewLabel, imagePreview);
        previewBox.setAlignment(Pos.CENTER);

        // Botón de predicción
        predictButton = new Button("Predecir Imagen");
        predictButton.setStyle("-fx-background-color: #e67e22; -fx-text-fill: white; -fx-font-size: 14px;");
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
        resultArea.setPrefRowCount(10);
        resultArea.setWrapText(true);

        HBox mainContent = new HBox(20);
        VBox leftPanel = new VBox(15, formGrid, buttonBox);
        mainContent.getChildren().addAll(leftPanel, previewBox);

        getChildren().addAll(titleLabel, mainContent, resultLabel, resultArea);
    }

    private void selectImage() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Seleccionar imagen");
        fileChooser.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("Imágenes", "*.png", "*.jpg", "*.jpeg", "*.bmp", "*.gif"),
            new FileChooser.ExtensionFilter("Todos los archivos", "*.*")
        );

        File file = fileChooser.showOpenDialog(getScene().getWindow());
        if (file != null) {
            selectedImage = file;
            imageField.setText(file.getName());
            
            // Mostrar vista previa
            try {
                Image image = new Image(new FileInputStream(file));
                imagePreview.setImage(image);
            } catch (Exception e) {
                showAlert(Alert.AlertType.ERROR, "Error", "No se pudo cargar la imagen: " + e.getMessage());
            }
        }
    }

    private void performPrediction() {
        String modelId = modelIdField.getText().trim();

        if (modelId.isEmpty()) {
            showAlert(Alert.AlertType.ERROR, "Error", "Debes especificar el ID del modelo");
            return;
        }

        if (selectedImage == null) {
            showAlert(Alert.AlertType.ERROR, "Error", "Debes seleccionar una imagen");
            return;
        }

        predictButton.setDisable(true);
        progressIndicator.setVisible(true);
        resultArea.appendText("=== Realizando predicción de imagen ===\n");
        resultArea.appendText("Modelo: " + modelId + "\n");
        resultArea.appendText("Imagen: " + selectedImage.getName() + "\n");
        resultArea.appendText("Tamaño: " + (selectedImage.length() / 1024) + " KB\n\n");

        new Thread(() -> {
            try {
                Response response = clientService.predictImage(modelId, selectedImage);

                Platform.runLater(() -> {
                    if (response.isSuccess()) {
                        resultArea.appendText("✓ Predicción exitosa\n");
                        resultArea.appendText("Clase predicha: " + response.getData().get("prediction") + "\n");
                        
                        if (response.getData().containsKey("confidence")) {
                            resultArea.appendText("Confianza: " + response.getData().get("confidence") + "\n");
                        }
                        
                        if (response.getData().containsKey("probabilities")) {
                            resultArea.appendText("Probabilidades por clase:\n");
                            resultArea.appendText(response.getData().get("probabilities").toString() + "\n");
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
