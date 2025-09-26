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

public class LoginController {

    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private Button loginButton;
    @FXML private Label statusLabel;

    // ... (os outros métodos handleLoginButtonAction, handleRegisterButtonAction, handleLoginResponse estão corretos) ...
    @FXML
    protected void handleLoginButtonAction() {
        String username = usernameField.getText();
        String password = passwordField.getText();

        if (username.isEmpty() || password.isEmpty()) {
            statusLabel.setText("Usuário e senha não podem ser vazios.");
            return;
        }

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
                    openProfileWindow();
                } else {
                    statusLabel.setText(response.optString("mensagem", "Usuário ou senha inválidos."));
                }
            } catch (JSONException e) {
                showAlert(Alert.AlertType.ERROR, "Erro de Protocolo", "O servidor enviou uma resposta em um formato inesperado: " + responseJson);
                e.printStackTrace();
            }
        });
    }

    @FXML
    protected void handleRegisterButtonAction() {
        String username = usernameField.getText();
        String password = passwordField.getText();
        if (username.isEmpty() || password.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Atenção", "Preencha os campos de usuário e senha antes de cadastrar.");
            return;
        }

        statusLabel.setText("Cadastrando...");
        Task<String> registerTask = new Task<>() {
            @Override
            protected String call() {
                return ServerConnection.getInstance().createUser(username, password);
            }
        };
        registerTask.setOnSucceeded(e -> Platform.runLater(() -> {
            JSONObject response = new JSONObject(registerTask.getValue());
            if ("201".equals(response.getString("status"))) {
                showAlert(Alert.AlertType.INFORMATION, "Sucesso", "Usuário '" + username + "' cadastrado! Agora você pode fazer o login.");
            } else {
                showAlert(Alert.AlertType.ERROR, "Erro", "Não foi possível cadastrar: " + response.optString("mensagem"));
            }
            statusLabel.setText("");
        }));
        new Thread(registerTask).start();
    }

    private void openProfileWindow() {
        try {
            Stage currentStage = (Stage) loginButton.getScene().getWindow();
            currentStage.close();

            // Forma correta de obter o recurso e passar para o FXMLLoader
            URL fxmlLocation = getClass().getResource("/view/ProfileView.fxml");
            FXMLLoader loader = new FXMLLoader(fxmlLocation);

            if (fxmlLocation == null) {
                showAlert(Alert.AlertType.ERROR, "Erro Crítico", "Não foi possível encontrar o arquivo da tela de perfil: /view/ProfileView.fxml");
                return;
            }

            Stage stage = new Stage();
            stage.setTitle("Meu Perfil");
            stage.setScene(new Scene(loader.load()));
            stage.show();
        } catch (IOException e) {
            e.printStackTrace();
            showAlert(Alert.AlertType.ERROR, "Erro ao Carregar Tela", "Ocorreu um erro ao tentar carregar a tela de perfil.");
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