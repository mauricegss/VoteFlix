package controller;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.stage.Stage;
import network.ServerConnection;
import session.SessionManager;
// import util.TokenDecoder; // Não é mais usado para role

import java.io.IOException;
import java.util.Objects;

public class MainController {

    @FXML private TabPane mainTabPane;
    @FXML private Tab profileTab;
    @FXML private Tab moviesTab;
    @FXML private Tab myReviewsTab;
    @FXML private Tab userManagementTab;

    @FXML
    private void initialize() {
        // Pega o Role que o LoginController salvou
        String role = SessionManager.getInstance().getRole();

        try {
            profileTab.setContent(FXMLLoader.load(Objects.requireNonNull(getClass().getResource("/view/ProfileView.fxml"))));

            if ("admin".equals(role)) {
                moviesTab.setText("Gerenciar Filmes");
                MovieManagementController movieController = (MovieManagementController) getControllerForTab(moviesTab, "/view/MovieManagementView.fxml");
                moviesTab.setOnSelectionChanged(event -> {
                    if (moviesTab.isSelected() && movieController != null) movieController.loadMovies();
                });

                UserManagementController userController = (UserManagementController) getControllerForTab(userManagementTab, "/view/UserManagementView.fxml");
                userManagementTab.setOnSelectionChanged(event -> {
                    if (userManagementTab.isSelected() && userController != null) userController.loadUsers();
                });

                mainTabPane.getTabs().remove(myReviewsTab);

            } else {
                moviesTab.setText("Ver Filmes");
                UserMovieController movieController = (UserMovieController) getControllerForTab(moviesTab, "/view/UserMovieView.fxml");
                moviesTab.setOnSelectionChanged(event -> {
                    if (moviesTab.isSelected() && movieController != null) movieController.loadMovies();
                });

                MyReviewsController reviewsController = (MyReviewsController) getControllerForTab(myReviewsTab, "/view/MyReviewsView.fxml");
                myReviewsTab.setOnSelectionChanged(event -> {
                    if (myReviewsTab.isSelected() && reviewsController != null) reviewsController.loadAndShowReviews();
                });

                mainTabPane.getTabs().remove(userManagementTab);
            }

            mainTabPane.getSelectionModel().select(profileTab);

        } catch (IOException | NullPointerException e) {
            System.err.println("Erro Crítico ao carregar FXML: " + e.getMessage());
            showCriticalLoadError();
        }
    }

    private Object getControllerForTab(Tab tab, String fxmlPath) throws IOException {
        FXMLLoader loader = new FXMLLoader(Objects.requireNonNull(getClass().getResource(fxmlPath)));
        Parent content = loader.load();
        tab.setContent(content);
        return loader.getController();
    }

    @FXML
    private void handleLogout() {
        String token = SessionManager.getInstance().getToken();
        if (token != null) {
            new Thread(() -> ServerConnection.getInstance().logout(token)).start();
        }
        SessionManager.getInstance().clearSession();

        try {
            Stage currentStage = (Stage) mainTabPane.getScene().getWindow();
            currentStage.close();

            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/ConnectionView.fxml"));
            Stage stage = new Stage();
            stage.setTitle("VoteFlix Client - Conexão");
            Scene scene = new Scene(loader.load(), 350, 350);
            scene.getStylesheets().add(Objects.requireNonNull(getClass().getResource("/view/style.css")).toExternalForm());
            stage.setScene(scene);
            stage.show();

        } catch (IOException e) {
            System.err.println("Erro ao carregar tela de conexão: " + e.getMessage());
            Platform.exit();
        }
    }

    private void showCriticalLoadError() {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Erro Crítico");
        alert.setHeaderText(null);
        alert.setContentText("Não foi possível carregar as telas principais. Verifique os arquivos FXML.");
        alert.getDialogPane().getStylesheets().add(Objects.requireNonNull(getClass().getResource("/view/style.css")).toExternalForm());
        alert.getDialogPane().getStyleClass().add("root");
        alert.showAndWait();
    }
}