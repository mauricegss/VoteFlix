package main;

import controller.ServerController;
import database.DatabaseInitializer;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.URL;

public class Main extends Application {
    @Override
    public void start(Stage stage) throws IOException {

        DatabaseInitializer.createTables();
        URL fxmlLocation = getClass().getResource("/view/ServerView.fxml");
        FXMLLoader fxmlLoader = new FXMLLoader(fxmlLocation);
        Scene scene = new Scene(fxmlLoader.load(), 800, 600);
        ServerController controller = fxmlLoader.getController();

        stage.setTitle("VoteFlix Server");
        stage.setScene(scene);
        stage.setOnCloseRequest(event -> controller.shutdown());
        stage.show();
    }

    @SuppressWarnings("unused")
    public static void main(String[] args) {
        launch();
    }
}