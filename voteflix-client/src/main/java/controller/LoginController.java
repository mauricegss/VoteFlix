package controller;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;
import network.ServerConnection;
import org.json.JSONException;
import org.json.JSONObject;
import session.SessionManager;

import java.io.IOException;
import java.net.URL;
import java.util.Objects;

public class LoginController {

    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private Button loginButton;
    @FXML private Label statusLabel;

    @FXML
    protected void handleLoginButtonAction() {
        String username = usernameField.getText();
        String password = passwordField.getText();

        statusLabel.setText("Autenticando...");
        loginButton.setDisable(true);

        Task<String> loginTask = new Task<>() {
            @Override
            protected String call() {
                return ServerConnection.getInstance().login(username, password);
            }
        };

        loginTask.setOnSucceeded(event -> handleLoginResponse(loginTask.getValue()));
        loginTask.setOnFailed(event -> handleLoginResponse(null));

        new Thread(loginTask).start();
    }

    private void handleLoginResponse(String responseJson) {
        Platform.runLater(() -> {
            loginButton.setDisable(false);
            if (responseJson == null) {
                statusLabel.setText("Erro de comunicação. O servidor respondeu nulo.");
                return;
            }

            try {
                JSONObject response = new JSONObject(responseJson);
                String status = response.getString("status");

                if ("200".equals(status)) {
                    String token = response.getString("token");
                    SessionManager.getInstance().setToken(token);
                    openMainWindow();
                } else {
                    String finalMessage = response.optString("mensagem", "Erro desconhecido.");
                    statusLabel.setText(finalMessage);
                }
            } catch (JSONException e) {
                showAlert(Alert.AlertType.ERROR, "Erro de Protocolo", "O servidor enviou uma resposta em um formato inesperado: " + responseJson);
                System.err.println("Erro de JSONException: " + e.getMessage());
            }
        });
    }

    @FXML
    protected void handleRegisterButtonAction() {
        String username = usernameField.getText();
        String password = passwordField.getText();

        statusLabel.setText("Cadastrando...");
        Task<String> registerTask = new Task<>() {
            @Override
            protected String call() {
                return ServerConnection.getInstance().createUser(username, password);
            }
        };
        registerTask.setOnSucceeded(e -> Platform.runLater(() -> {
            JSONObject response = new JSONObject(registerTask.getValue());
            String status = response.getString("status");
            if ("201".equals(status)) {
                showAlert(Alert.AlertType.INFORMATION, "Sucesso", "Usuário '" + username + "' cadastrado! Agora você pode fazer o login.");
            } else {
                String finalMessage = response.optString("mensagem", "Erro no cadastro.");
                showAlert(Alert.AlertType.ERROR, "Erro", "Não foi possível cadastrar: " + finalMessage);
            }
            statusLabel.setText("");
        }));
        new Thread(registerTask).start();
    }

    private void openMainWindow() {
        try {
            Stage currentStage = (Stage) loginButton.getScene().getWindow();
            currentStage.close();

            URL fxmlLocation = getClass().getResource("/view/MainView.fxml");
            FXMLLoader loader = new FXMLLoader(fxmlLocation);

            if (fxmlLocation == null) {
                showAlert(Alert.AlertType.ERROR, "Erro Crítico", "Não foi possível encontrar o arquivo da tela principal: /view/MainView.fxml");
                return;
            }

            Stage stage = new Stage();
            stage.setTitle("VoteFlix");
            Scene scene = new Scene(loader.load());
            scene.getStylesheets().add(Objects.requireNonNull(getClass().getResource("/view/style.css")).toExternalForm());
            stage.setScene(scene);
            stage.show();
        } catch (IOException e) {
            System.err.println("Erro ao carregar FXML da tela principal: " + e.getMessage());
            showAlert(Alert.AlertType.ERROR, "Erro ao Carregar Tela", "Ocorreu um erro ao tentar carregar a tela principal.");
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