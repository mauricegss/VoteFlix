package main;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import network.ServerConnection;
import java.io.IOException;
import java.net.URL;
import java.util.Objects;

public class Main extends Application {
    @Override
    public void start(Stage stage) throws IOException {
        URL fxmlLocation = Main.class.getResource("/view/ConnectionView.fxml");
        FXMLLoader fxmlLoader = new FXMLLoader(fxmlLocation);

        Scene scene = new Scene(fxmlLoader.load(), 350, 300);
        scene.getStylesheets().add(Objects.requireNonNull(getClass().getResource("/view/style.css")).toExternalForm());
        stage.setTitle("VoteFlix Client - Conexão");
        stage.setScene(scene);
        stage.show();
    }

    @Override
    public void stop() {
        System.out.println("Fechando a aplicação e desconectando...");
        ServerConnection.getInstance().disconnect();
    }

    @SuppressWarnings("unused")
    public static void main(String[] args) {
        launch();
    }
}