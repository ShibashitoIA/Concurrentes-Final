package com.raft.client.ui;

import com.raft.client.network.ClientService;
import com.raft.client.protocol.Response;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;

import java.io.File;

/**
 * Panel para entrenar modelos con imágenes.
 */
public class TrainImagePanel extends VBox {
    private ClientService clientService;
    private TextField folderField;
    private TextField modelNameField;
    private TextField widthField;
    private TextField heightField;
    private CheckBox colorCheckBox;
    private TextArea logArea;
    private ProgressIndicator progressIndicator;
    private Button trainButton;

    public TrainImagePanel(ClientService clientService) {
        this.clientService = clientService;
        setupUI();
    }

    private void setupUI() {
        setPadding(new Insets(20));
        setSpacing(15);

        Label titleLabel = new Label("Entrenar Modelo con Imágenes (CNN)");
        titleLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        Label infoLabel = new Label("Dataset: Carpeta con subcarpetas por clase (ej: dataset/perros, dataset/gatos)");
        infoLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: gray;");

        // Formulario
        GridPane formGrid = new GridPane();
        formGrid.setHgap(10);
        formGrid.setVgap(10);

        Label folderLabel = new Label("Carpeta del dataset:");
        folderField = new TextField();
        folderField.setPromptText("Selecciona carpeta con imágenes...");
        folderField.setPrefWidth(300);
        folderField.setEditable(false);

        Button browseButton = new Button("Examinar...");
        browseButton.setOnAction(e -> selectFolder());

        HBox folderBox = new HBox(10, folderField, browseButton);

        Label nameLabel = new Label("Nombre del modelo:");
        modelNameField = new TextField();
        modelNameField.setPromptText("mi-modelo-imagenes");
        modelNameField.setPrefWidth(300);

        Label sizeLabel = new Label("Tamaño de imagen:");
        widthField = new TextField("28");
        widthField.setPrefWidth(60);
        heightField = new TextField("28");
        heightField.setPrefWidth(60);
        HBox sizeBox = new HBox(5, widthField, new Label("x"), heightField, new Label("píxeles"));

        Label colorLabel = new Label("Tipo de imagen:");
        colorCheckBox = new CheckBox("RGB a color (si no, escala de grises)");
        colorCheckBox.setSelected(false);

        formGrid.add(folderLabel, 0, 0);
        formGrid.add(folderBox, 1, 0);
        formGrid.add(nameLabel, 0, 1);
        formGrid.add(modelNameField, 1, 1);
        formGrid.add(sizeLabel, 0, 2);
        formGrid.add(sizeBox, 1, 2);
        formGrid.add(colorLabel, 0, 3);
        formGrid.add(colorCheckBox, 1, 3);

        // Botón de entrenamiento
        trainButton = new Button("Iniciar Entrenamiento con Imágenes");
        trainButton.setStyle("-fx-background-color: #9b59b6; -fx-text-fill: white; -fx-font-size: 14px;");
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
        logArea.setPrefRowCount(10);
        logArea.setWrapText(true);

        getChildren().addAll(titleLabel, infoLabel, formGrid, buttonBox, logLabel, logArea);
    }

    private void selectFolder() {
        DirectoryChooser dirChooser = new DirectoryChooser();
        dirChooser.setTitle("Seleccionar carpeta de dataset");

        File folder = dirChooser.showDialog(getScene().getWindow());
        if (folder != null) {
            folderField.setText(folder.getAbsolutePath());
            
            // Sugerir nombre del modelo basado en la carpeta
            if (modelNameField.getText().isEmpty()) {
                String suggestedName = folder.getName() + "-model";
                modelNameField.setText(suggestedName);
            }
        }
    }

    private void startTraining() {
        String folderPath = folderField.getText();
        String modelName = modelNameField.getText().trim();
        String widthStr = widthField.getText().trim();
        String heightStr = heightField.getText().trim();

        if (folderPath.isEmpty()) {
            showAlert(Alert.AlertType.ERROR, "Error", "Debes seleccionar una carpeta de dataset");
            return;
        }

        if (modelName.isEmpty()) {
            showAlert(Alert.AlertType.ERROR, "Error", "Debes especificar un nombre para el modelo");
            return;
        }

        int width, height;
        try {
            width = Integer.parseInt(widthStr);
            height = Integer.parseInt(heightStr);
            if (width <= 0 || height <= 0 || width > 1024 || height > 1024) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException e) {
            showAlert(Alert.AlertType.ERROR, "Error", "El tamaño debe ser un número entre 1 y 1024");
            return;
        }

        File folder = new File(folderPath);
        if (!folder.exists() || !folder.isDirectory()) {
            showAlert(Alert.AlertType.ERROR, "Error", "La carpeta no existe");
            return;
        }

        boolean isColor = colorCheckBox.isSelected();

        trainButton.setDisable(true);
        progressIndicator.setVisible(true);
        logArea.appendText("=== Iniciando entrenamiento con imágenes ===\n");
        logArea.appendText("Dataset: " + folder.getName() + "\n");
        logArea.appendText("Tamaño de imagen: " + width + "x" + height + "\n");
        logArea.appendText("Tipo: " + (isColor ? "RGB (3 canales)" : "Escala de grises (1 canal)") + "\n");
        logArea.appendText("Modelo: " + modelName + "\n");
        logArea.appendText("Servidor: " + clientService.getCurrentServer() + "\n\n");

        new Thread(() -> {
            try {
                Response response = clientService.trainImageModel(folder, modelName, width, height, isColor);

                Platform.runLater(() -> {
                    if (response.isSuccess()) {
                        logArea.appendText("✓ Entrenamiento iniciado exitosamente\n");
                        logArea.appendText("Mensaje: " + response.getMessage() + "\n");
                        logArea.appendText("(Se envió ZIP por STORE_FILE y TRAIN_MODEL IMAGE con 10 hiperparámetros)\n");
                        if (response.getData().containsKey("modelId")) {
                            logArea.appendText("ID del modelo: " + response.getData().get("modelId") + "\n");
                        }
                        logArea.appendText("\n");
                        
                        showAlert(Alert.AlertType.INFORMATION, "Éxito", 
                            "El entrenamiento de imágenes ha sido iniciado correctamente");
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
