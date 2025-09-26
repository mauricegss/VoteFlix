package main;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import network.ServerConnection;
import java.io.IOException;
import java.net.URL;

public class MainApplication extends Application {
    @Override
    public void start(Stage stage) throws IOException {
        // Forma correta de obter o recurso e passar para o FXMLLoader
        URL fxmlLocation = MainApplication.class.getResource("/view/ConnectionView.fxml");
        FXMLLoader fxmlLoader = new FXMLLoader(fxmlLocation);

        Scene scene = new Scene(fxmlLoader.load(), 300, 250);
        stage.setTitle("VoteFlix Client - Conexão");
        stage.setScene(scene);
        stage.show();
    }

    @Override
    public void stop() {
        System.out.println("Fechando a aplicação e desconectando...");
        ServerConnection.getInstance().disconnect();
    }

    public static void main(String[] args) {
        launch();
    }
}