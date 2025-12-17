package com.raft.client.ui;

import com.raft.client.network.ClientService;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/**
 * Vista principal con pestañas para las diferentes funcionalidades.
 */
public class MainView extends BorderPane {
    private ClientService clientService;
    private Label statusLabel;
    private TextField hostField;
    private TextField portField;

    public MainView() {
        initializeClient();
        setupUI();
    }

    private void initializeClient() {
        // Valores por defecto
        String host = "localhost";
        int port = 8080;
        this.clientService = new ClientService(host, port);
    }

    private void setupUI() {
        // Barra superior con conexión
        VBox topBar = createTopBar();
        setTop(topBar);

        // Pestañas principales
        TabPane tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        // Pestaña de conexión
        Tab connectionTab = new Tab("Conexión");
        connectionTab.setContent(new ConnectionPanel(clientService, statusLabel));

        // Pestaña de entrenamiento con datos
        Tab trainTab = new Tab("Entrenar (Datos)");
        trainTab.setContent(new TrainPanel(clientService));

        // Pestaña de entrenamiento con imágenes
        Tab trainImageTab = new Tab("Entrenar (Imágenes)");
        trainImageTab.setContent(new TrainImagePanel(clientService));

        // Pestaña de predicción con datos
        Tab predictTab = new Tab("Predicción (Datos)");
        predictTab.setContent(new PredictPanel(clientService));

        // Pestaña de predicción con imágenes
        Tab predictImageTab = new Tab("Predicción (Imagen)");
        predictImageTab.setContent(new PredictImagePanel(clientService));

        // Pestaña de modelos
        Tab modelsTab = new Tab("Modelos");
        modelsTab.setContent(new ModelsPanel(clientService));

        // Pestaña de archivos
        Tab filesTab = new Tab("Archivos");
        filesTab.setContent(new FilesPanel(clientService));

        tabPane.getTabs().addAll(connectionTab, trainTab, trainImageTab, predictTab, 
            predictImageTab, modelsTab, filesTab);

        setCenter(tabPane);

        // Barra inferior con estado
        HBox bottomBar = createBottomBar();
        setBottom(bottomBar);
    }

    private VBox createTopBar() {
        VBox topBar = new VBox(10);
        topBar.setPadding(new Insets(10));
        topBar.setStyle("-fx-background-color: #2c3e50;");

        Label titleLabel = new Label("Cliente RAFT - Sistema Distribuido con IA");
        titleLabel.setStyle("-fx-text-fill: white; -fx-font-size: 18px; -fx-font-weight: bold;");

        HBox connectionBox = new HBox(10);
        connectionBox.setAlignment(Pos.CENTER_LEFT);

        Label hostLabel = new Label("Host:");
        hostLabel.setStyle("-fx-text-fill: white;");
        hostField = new TextField("localhost");
        hostField.setPrefWidth(150);

        Label portLabel = new Label("Puerto:");
        portLabel.setStyle("-fx-text-fill: white;");
        portField = new TextField("8080");
        portField.setPrefWidth(80);

        Button connectButton = new Button("Conectar");
        connectButton.setOnAction(e -> updateConnection());

        connectionBox.getChildren().addAll(hostLabel, hostField, portLabel, portField, connectButton);

        topBar.getChildren().addAll(titleLabel, connectionBox);

        return topBar;
    }

    private HBox createBottomBar() {
        HBox bottomBar = new HBox(10);
        bottomBar.setPadding(new Insets(5, 10, 5, 10));
        bottomBar.setAlignment(Pos.CENTER_LEFT);
        bottomBar.setStyle("-fx-background-color: #ecf0f1;");

        statusLabel = new Label("Desconectado");
        statusLabel.setStyle("-fx-text-fill: #e74c3c;");

        bottomBar.getChildren().add(statusLabel);

        return bottomBar;
    }

    private void updateConnection() {
        String host = hostField.getText().trim();
        String portStr = portField.getText().trim();

        try {
            int port = Integer.parseInt(portStr);
            clientService.setServer(host, port);

            if (clientService.testConnection()) {
                statusLabel.setText("Conectado a " + clientService.getCurrentServer());
                statusLabel.setStyle("-fx-text-fill: #27ae60;");
                showAlert(Alert.AlertType.INFORMATION, "Conexión Exitosa", 
                    "Conectado correctamente a " + clientService.getCurrentServer());
            } else {
                statusLabel.setText("Error de conexión a " + host + ":" + port);
                statusLabel.setStyle("-fx-text-fill: #e74c3c;");
                showAlert(Alert.AlertType.ERROR, "Error de Conexión", 
                    "No se pudo conectar al servidor " + host + ":" + port);
            }
        } catch (NumberFormatException e) {
            showAlert(Alert.AlertType.ERROR, "Error", "El puerto debe ser un número válido");
        }
    }

    private void showAlert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
