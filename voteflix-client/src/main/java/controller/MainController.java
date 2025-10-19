package controller;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.stage.Stage;
import network.ServerConnection;
import session.SessionManager;
import util.TokenDecoder;

import java.io.IOException;
import java.util.Objects;

public class MainController {

    @FXML private TabPane mainTabPane;
    @FXML private Tab profileTab;
    @FXML private Tab moviesTab;
    @FXML private Tab userManagementTab;

    @FXML
    private void initialize() {
        String token = SessionManager.getInstance().getToken();
        String role = TokenDecoder.getRoleFromToken(token);

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

            } else {
                moviesTab.setText("Ver Filmes");
                UserMovieController movieController = (UserMovieController) getControllerForTab(moviesTab, "/view/UserMovieView.fxml");
                moviesTab.setOnSelectionChanged(event -> {
                    if (moviesTab.isSelected() && movieController != null) movieController.loadMovies();
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

        Stage currentStage = (Stage) mainTabPane.getScene().getWindow();
        currentStage.close();
    }

    private void showCriticalLoadError() {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Erro Crítico");
        alert.setHeaderText(null);
        alert.setContentText("Não foi possível carregar as telas principais. Verifique os arquivos FXML.");
        alert.showAndWait();
    }
}