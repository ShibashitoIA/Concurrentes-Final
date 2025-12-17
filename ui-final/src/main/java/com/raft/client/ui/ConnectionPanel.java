package com.raft.client.ui;

import com.raft.client.network.ClientService;
import com.raft.client.protocol.Response;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

/**
 * Panel para verificar la conexión y estado del servidor.
 */
public class ConnectionPanel extends VBox {
    private ClientService clientService;
    private Label mainStatusLabel;
    private TextArea statusArea;

    public ConnectionPanel(ClientService clientService, Label mainStatusLabel) {
        this.clientService = clientService;
        this.mainStatusLabel = mainStatusLabel;
        setupUI();
    }

    private void setupUI() {
        setPadding(new Insets(20));
        setSpacing(15);

        Label titleLabel = new Label("Estado de la Conexión");
        titleLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        GridPane infoGrid = new GridPane();
        infoGrid.setHgap(10);
        infoGrid.setVgap(10);

        Label serverLabel = new Label("Servidor actual:");
        Label serverValue = new Label(clientService.getCurrentServer());
        serverValue.setStyle("-fx-font-weight: bold;");

        infoGrid.add(serverLabel, 0, 0);
        infoGrid.add(serverValue, 1, 0);

        Button testButton = new Button("Probar Conexión");
        testButton.setOnAction(e -> testConnection());

        Button statusButton = new Button("Obtener Estado del Servidor");
        statusButton.setOnAction(e -> getServerStatus());

        statusArea = new TextArea();
        statusArea.setEditable(false);
        statusArea.setPrefRowCount(15);
        statusArea.setWrapText(true);

        getChildren().addAll(titleLabel, infoGrid, testButton, statusButton, 
            new Label("Detalles:"), statusArea);
    }

    private void testConnection() {
        statusArea.appendText("Probando conexión...\n");
        
        new Thread(() -> {
            boolean connected = clientService.testConnection();
            Platform.runLater(() -> {
                if (connected) {
                    statusArea.appendText("✓ Conexión exitosa\n\n");
                    if (mainStatusLabel != null) {
                        mainStatusLabel.setText("Conectado a " + clientService.getCurrentServer());
                        mainStatusLabel.setStyle("-fx-text-fill: #27ae60;");
                    }
                } else {
                    statusArea.appendText("✗ Error de conexión\n\n");
                    if (mainStatusLabel != null) {
                        mainStatusLabel.setText("Error de conexión");
                        mainStatusLabel.setStyle("-fx-text-fill: #e74c3c;");
                    }
                }
            });
        }).start();
    }

    private void getServerStatus() {
        statusArea.appendText("Obteniendo estado del servidor...\n");
        
        new Thread(() -> {
            try {
                Response response = clientService.getStatus();
                Platform.runLater(() -> {
                    if (response.isSuccess()) {
                        statusArea.appendText("✓ Estado recibido:\n");
                        response.getData().forEach((key, value) -> {
                            statusArea.appendText("  " + key + ": " + value + "\n");
                        });
                        statusArea.appendText("\n");
                    } else {
                        statusArea.appendText("✗ Error: " + response.getMessage() + "\n\n");
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    statusArea.appendText("✗ Error: " + e.getMessage() + "\n\n");
                });
            }
        }).start();
    }
}
