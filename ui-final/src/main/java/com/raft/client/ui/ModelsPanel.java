package com.raft.client.ui;

import com.raft.client.network.ClientService;
import com.raft.client.protocol.ModelInfo;
import com.raft.client.protocol.Response;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.List;

/**
 * Panel para listar y gestionar modelos.
 */
public class ModelsPanel extends VBox {
    private ClientService clientService;
    private TableView<ModelInfo> modelsTable;
    private ObservableList<ModelInfo> modelsList;
    private ProgressIndicator progressIndicator;
    private Label countLabel;

    public ModelsPanel(ClientService clientService) {
        this.clientService = clientService;
        this.modelsList = FXCollections.observableArrayList();
        setupUI();
    }

    private void setupUI() {
        setPadding(new Insets(20));
        setSpacing(15);

        Label titleLabel = new Label("Modelos Disponibles");
        titleLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        // Botones de acción
        Button refreshButton = new Button("Actualizar Lista");
        refreshButton.setStyle("-fx-background-color: #3498db; -fx-text-fill: white;");
        refreshButton.setOnAction(e -> loadModels());

        Button detailsButton = new Button("Ver Detalles");
        detailsButton.setStyle("-fx-background-color: #16a085; -fx-text-fill: white;");
        detailsButton.setDisable(true);
        detailsButton.setOnAction(e -> showModelDetails());

        Button deleteButton = new Button("Eliminar Modelo");
        deleteButton.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white;");
        deleteButton.setDisable(true);
        deleteButton.setOnAction(e -> deleteSelectedModel());

        progressIndicator = new ProgressIndicator();
        progressIndicator.setVisible(false);
        progressIndicator.setPrefSize(25, 25);

        countLabel = new Label("0 modelos");
        countLabel.setStyle("-fx-font-weight: bold;");

        HBox buttonBox = new HBox(10, refreshButton, detailsButton, deleteButton, 
            progressIndicator, countLabel);
        buttonBox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        // Tabla de modelos
        modelsTable = new TableView<>();
        modelsTable.setItems(modelsList);
        modelsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<ModelInfo, String> idColumn = new TableColumn<>("ID");
        idColumn.setCellValueFactory(new PropertyValueFactory<>("modelId"));
        idColumn.setPrefWidth(150);

        TableColumn<ModelInfo, String> nameColumn = new TableColumn<>("Nombre");
        nameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));
        nameColumn.setPrefWidth(200);

        TableColumn<ModelInfo, String> statusColumn = new TableColumn<>("Estado");
        statusColumn.setCellValueFactory(new PropertyValueFactory<>("status"));
        statusColumn.setPrefWidth(100);

        TableColumn<ModelInfo, String> dateColumn = new TableColumn<>("Fecha Creación");
        dateColumn.setCellValueFactory(new PropertyValueFactory<>("createdAt"));
        dateColumn.setPrefWidth(150);

        modelsTable.getColumns().addAll(idColumn, nameColumn, statusColumn, dateColumn);

        // Información del modelo seleccionado
        TextArea infoArea = new TextArea();
        infoArea.setEditable(false);
        infoArea.setPrefRowCount(5);
        infoArea.setPromptText("Selecciona un modelo para ver más detalles...");

        modelsTable.getSelectionModel().selectedItemProperty().addListener((obs, oldSelection, newSelection) -> {
            boolean hasSelection = (newSelection != null);
            detailsButton.setDisable(!hasSelection);
            deleteButton.setDisable(!hasSelection);
            
            if (newSelection != null) {
                infoArea.setText(
                    "ID: " + newSelection.getModelId() + "\n" +
                    "Nombre: " + newSelection.getName() + "\n" +
                    "Estado: " + newSelection.getStatus() + "\n" +
                    "Fecha: " + (newSelection.getCreatedAt() != null ? newSelection.getCreatedAt() : "N/A") + "\n" +
                    "Archivo: " + (newSelection.getTrainingFile() != null ? newSelection.getTrainingFile() : "N/A")
                );
            }
        });

        getChildren().addAll(titleLabel, buttonBox, modelsTable, new Label("Detalles:"), infoArea);

        // Cargar modelos al iniciar
        loadModels();
    }

    private void loadModels() {
        progressIndicator.setVisible(true);
        countLabel.setText("Cargando...");

        new Thread(() -> {
            try {
                List<ModelInfo> models = clientService.listModels();

                Platform.runLater(() -> {
                    modelsList.clear();
                    modelsList.addAll(models);
                    countLabel.setText(models.size() + " modelo(s)");
                    progressIndicator.setVisible(false);
                });

            } catch (Exception e) {
                Platform.runLater(() -> {
                    countLabel.setText("Error al cargar");
                    progressIndicator.setVisible(false);
                    showAlert(Alert.AlertType.ERROR, "Error", 
                        "No se pudo cargar la lista de modelos: " + e.getMessage());
                });
            }
        }).start();
    }

    private void showModelDetails() {
        ModelInfo selected = modelsTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }

        progressIndicator.setVisible(true);

        new Thread(() -> {
            try {
                Response response = clientService.getModelInfo(selected.getModelId());

                Platform.runLater(() -> {
                    if (response.isSuccess()) {
                        java.util.Map<String, Object> data = response.getData();
                        
                        // Crear diálogo con detalles completos
                        Alert dialog = new Alert(Alert.AlertType.INFORMATION);
                        dialog.setTitle("Detalles del Modelo");
                        dialog.setHeaderText(selected.getName());
                        
                        StringBuilder content = new StringBuilder();
                        content.append("═".repeat(50)).append("\n");
                        content.append("INFORMACIÓN GENERAL\n");
                        content.append("═".repeat(50)).append("\n\n");
                        content.append("ID:              ").append(data.getOrDefault("modelId", "N/A")).append("\n");
                        content.append("Nombre:          ").append(data.getOrDefault("name", "N/A")).append("\n");
                        content.append("Estado:          ").append(data.getOrDefault("status", "N/A")).append("\n");
                        content.append("Tipo:            ").append(data.getOrDefault("modelType", "N/A")).append("\n");
                        content.append("Fecha Creación:  ").append(data.getOrDefault("createdAt", "N/A")).append("\n\n");
                        
                        content.append("═".repeat(50)).append("\n");
                        content.append("ARQUITECTURA\n");
                        content.append("═".repeat(50)).append("\n\n");
                        content.append("Tamaño Entrada:  ").append(data.getOrDefault("inputSize", "N/A")).append(" características\n");
                        content.append("Tamaño Salida:   ").append(data.getOrDefault("outputSize", "N/A")).append(" clases\n");
                        
                        if (data.containsKey("imageWidth") && data.containsKey("imageHeight")) {
                            content.append("Dimensiones:     ").append(data.get("imageWidth"))
                                   .append("x").append(data.get("imageHeight")).append(" píxeles\n");
                            content.append("Canales:         ").append(data.getOrDefault("channels", "N/A")).append("\n");
                        }
                        
                        if (data.containsKey("trainingFile")) {
                            content.append("\n═".repeat(50)).append("\n");
                            content.append("ENTRENAMIENTO\n");
                            content.append("═".repeat(50)).append("\n\n");
                            content.append("Archivo:         ").append(data.get("trainingFile")).append("\n");
                        }
                        
                        if (data.containsKey("predictionCount") || data.containsKey("lastUsed")) {
                            content.append("\n═".repeat(50)).append("\n");
                            content.append("USO\n");
                            content.append("═".repeat(50)).append("\n\n");
                            if (data.containsKey("predictionCount")) {
                                content.append("Predicciones:    ").append(data.get("predictionCount")).append(" realizadas\n");
                            }
                            if (data.containsKey("lastUsed")) {
                                content.append("Último Uso:      ").append(data.get("lastUsed")).append("\n");
                            }
                        }
                        
                        dialog.setContentText(content.toString());
                        dialog.getDialogPane().setPrefWidth(600);
                        dialog.showAndWait();
                        
                    } else {
                        showAlert(Alert.AlertType.ERROR, "Error", 
                            "No se pudo obtener información del modelo:\n" + 
                            response.getMessage());
                    }
                    progressIndicator.setVisible(false);
                });

            } catch (Exception e) {
                Platform.runLater(() -> {
                    showAlert(Alert.AlertType.ERROR, "Error", 
                        "Error al consultar el modelo: " + e.getMessage());
                    progressIndicator.setVisible(false);
                });
            }
        }).start();
    }

    private void deleteSelectedModel() {
        ModelInfo selected = modelsTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }

        // Confirmación
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Confirmar Eliminación");
        confirm.setHeaderText("¿Eliminar modelo?");
        confirm.setContentText(
            "¿Estás seguro de eliminar el modelo?\n\n" +
            "ID:     " + selected.getModelId() + "\n" +
            "Nombre: " + selected.getName() + "\n" +
            "Estado: " + selected.getStatus() + "\n\n" +
            "Esta acción NO se puede deshacer."
        );

        java.util.Optional<ButtonType> result = confirm.showAndWait();
        if (!result.isPresent() || result.get() != ButtonType.OK) {
            return;
        }

        performDelete(selected.getModelId());
    }

    private void performDelete(String modelId) {
        progressIndicator.setVisible(true);

        new Thread(() -> {
            try {
                Response response = clientService.deleteModel(modelId);

                Platform.runLater(() -> {
                    if (response.isSuccess()) {
                        showAlert(Alert.AlertType.INFORMATION, "Éxito", 
                            "Modelo eliminado correctamente");
                        loadModels(); // Recargar lista
                    } else {
                        showAlert(Alert.AlertType.ERROR, "Error", 
                            "No se pudo eliminar el modelo:\n" + response.getMessage());
                    }
                    progressIndicator.setVisible(false);
                });

            } catch (Exception e) {
                Platform.runLater(() -> {
                    showAlert(Alert.AlertType.ERROR, "Error de Conexión", 
                        "Error al eliminar: " + e.getMessage());
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
