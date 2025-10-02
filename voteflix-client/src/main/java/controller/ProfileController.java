package controller;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TextInputDialog;
import javafx.stage.Stage;
import network.ServerConnection;
import org.json.JSONObject;
import session.SessionManager;
import java.util.Optional;

public class ProfileController {

    @FXML private Label welcomeLabel;
    @FXML private Label statusLabel;

    @FXML
    private void initialize() {
        welcomeLabel.setText("Bem-vindo(a) ao seu Perfil!");
    }

    @FXML
    private void handleUpdatePassword() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Atualização de Senha");
        dialog.setHeaderText("Digite sua nova senha.");
        dialog.setContentText("Nova Senha:");

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(newPassword -> {
            if (newPassword.trim().isEmpty()) {
                showAlert(Alert.AlertType.ERROR, "Erro", "A senha não pode ser vazia.");
                return;
            }

            statusLabel.setText("Atualizando senha...");
            Task<String> updateTask = new Task<>() {
                @Override
                protected String call() {
                    String token = SessionManager.getInstance().getToken();
                    return ServerConnection.getInstance().updateOwnPassword(newPassword, token);
                }
            };
            updateTask.setOnSucceeded(e -> handleGenericResponse(updateTask.getValue(), "Senha atualizada com sucesso!"));
            new Thread(updateTask).start();
        });
    }

    @FXML
    private void handleListOwnUser() {
        statusLabel.setText("Buscando informações...");
        Task<String> listUserTask = new Task<>() {
            @Override
            protected String call() {
                String token = SessionManager.getInstance().getToken();
                // O método de conexão já está correto, chamando "LISTAR_PROPRIO_USUARIO"
                return ServerConnection.getInstance().listOwnUser(token);
            }
        };

        listUserTask.setOnSucceeded(e -> {
            statusLabel.setText("");
            String responseJson = listUserTask.getValue();
            if (responseJson == null) {
                showAlert(Alert.AlertType.ERROR, "Erro", "Erro de comunicação com o servidor.");
                return;
            }

            JSONObject response = new JSONObject(responseJson);
            if ("200".equals(response.getString("status"))) {
                // **ESTA É A LINHA CORRIGIDA**
                // Estamos lendo a String diretamente da chave "usuario", conforme o erro indicou.
                String username = response.getString("usuario");
                showAlert(Alert.AlertType.INFORMATION, "Informações do Usuário", "Seu nome de usuário é: " + username);
            } else {
                showAlert(Alert.AlertType.ERROR, "Erro", response.optString("mensagem", "Não foi possível obter as informações."));
            }
        });

        new Thread(listUserTask).start();
    }

    @FXML
    private void handleLogout() {
        statusLabel.setText("Saindo...");
        Task<String> logoutTask = new Task<>() {
            @Override
            protected String call() {
                String token = SessionManager.getInstance().getToken();
                return ServerConnection.getInstance().logout(token);
            }
        };
        logoutTask.setOnSucceeded(e -> {
            SessionManager.getInstance().clearSession();
            showAlert(Alert.AlertType.INFORMATION, "Logout", "Você foi desconectado com sucesso.");
            closeWindow();
        });
        new Thread(logoutTask).start();
    }

    @FXML
    private void handleDeleteAccount() {
        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
        confirmation.setTitle("Confirmar Exclusão");
        confirmation.setHeaderText("Você tem certeza que deseja apagar sua conta?");
        confirmation.setContentText("Esta ação é irreversível.");

        Optional<ButtonType> result = confirmation.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            statusLabel.setText("Apagando conta...");
            Task<String> deleteTask = createDeleteTask();
            deleteTask.setOnSucceeded(e -> {
                handleGenericResponse(deleteTask.getValue(), "Conta apagada com sucesso.");
                SessionManager.getInstance().clearSession();
                closeWindow();
            });
            new Thread(deleteTask).start();
        }
    }

    private Task<String> createDeleteTask() {
        return new Task<>() {
            @Override
            protected String call() {
                String token = SessionManager.getInstance().getToken();
                return ServerConnection.getInstance().deleteOwnUser(token);
            }
        };
    }

    private void handleGenericResponse(String responseJson, String successMessage) {
        Platform.runLater(() -> {
            statusLabel.setText("");
            if (responseJson == null) {
                showAlert(Alert.AlertType.ERROR, "Erro", "Erro de comunicação com o servidor.");
                return;
            }
            JSONObject response = new JSONObject(responseJson);
            String status = response.getString("status");
            if ("200".equals(status)) {
                showAlert(Alert.AlertType.INFORMATION, "Sucesso", successMessage);
            } else {
                showAlert(Alert.AlertType.ERROR, "Erro", "Ocorreu um erro: " + response.optString("mensagem"));
            }
        });
    }

    private void showAlert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void closeWindow() {
        Stage stage = (Stage) statusLabel.getScene().getWindow();
        stage.close();
    }
}