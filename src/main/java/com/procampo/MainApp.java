package com.procampo;

import com.procampo.db.DatabaseInitializer;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * Ponto de entrada da aplicação JavaFX Desktop.
 */
public class MainApp extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception {
        // Inicializa banco SQLite e popula com seed inicial caso esteja vazio
        DatabaseInitializer.initialize();

        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main_view.fxml"));
        Parent root = loader.load();

        Scene scene = new Scene(root, 1140, 740);
        primaryStage.setTitle("ProCampo");
        primaryStage.setScene(scene);
        primaryStage.setMinWidth(900);
        primaryStage.setMinHeight(600);
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
