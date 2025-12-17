package com.raft.client.ui;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * Aplicación principal del cliente Desktop.
 */
public class DesktopClientApp extends Application {

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("RAFT Client - Sistema Distribuido con IA");

        MainView mainView = new MainView();
        Scene scene = new Scene(mainView, 1000, 700);

        // Agregar estilo CSS si existe
        String css = getClass().getResource("/styles.css") != null ? 
            getClass().getResource("/styles.css").toExternalForm() : null;
        if (css != null) {
            scene.getStylesheets().add(css);
        }

        primaryStage.setScene(scene);
        primaryStage.show();
    }

    @Override
    public void stop() {
        System.out.println("Cerrando aplicación...");
    }

    public static void main(String[] args) {
        launch(args);
    }
}
