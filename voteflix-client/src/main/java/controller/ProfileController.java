package controller;

import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import java.io.IOException;
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
            statusLabel.setText("Atualizando senha...");
            Task<String> updateTask = new Task<>() {
                @Override
                protected String call() {
                    String token = SessionManager.getInstance().getToken();
                    return ServerConnection.getInstance().updateOwnPassword(newPassword, token);
                }
            };
            updateTask.setOnSucceeded(e -> handleUpdatePasswordResponse(updateTask.getValue()));
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
            String status = response.getString("status");
            if ("200".equals(status)) {
                String username = response.getString("usuario");
                showAlert(Alert.AlertType.INFORMATION, "Informações do Usuário", "Seu nome de usuário é: " + username);
            } else {
                String finalMessage = response.optString("mensagem", "Erro ao listar usuário.");
                showAlert(Alert.AlertType.ERROR, "Erro", finalMessage);
            }
        });

        new Thread(listUserTask).start();
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
            Task<Void> deleteTask = new Task<>() {
                @Override
                protected Void call() {
                    String token = SessionManager.getInstance().getToken();
                    ServerConnection.getInstance().deleteOwnUser(token);
                    return null;
                }
            };
            deleteTask.setOnSucceeded(e -> {
                SessionManager.getInstance().clearSession();
                closeWindow();
            });
            new Thread(deleteTask).start();
        }
    }

    private void handleUpdatePasswordResponse(String responseJson) {
        Platform.runLater(() -> {
            statusLabel.setText("");
            if (responseJson == null) {
                showAlert(Alert.AlertType.ERROR, "Erro", "Erro de comunicação com o servidor.");
                return;
            }
            JSONObject response = new JSONObject(responseJson);
            String status = response.getString("status");
            if ("200".equals(status)) {
                showAlert(Alert.AlertType.INFORMATION, "Sucesso", "Senha atualizada com sucesso!");
            } else {
                String finalMessage = response.optString("mensagem", "Ocorreu um erro ao atualizar a senha.");
                showAlert(Alert.AlertType.ERROR, "Erro", "Ocorreu um erro: " + finalMessage);
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
        try {
            Stage currentStage = (Stage) statusLabel.getScene().getWindow();
            currentStage.close();

            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/ConnectionView.fxml"));
            Stage stage = new Stage();
            stage.setTitle("VoteFlix Client - Conexão");
            stage.setScene(new Scene(loader.load(), 300, 250));
            stage.show();

        } catch (IOException e) {
            System.err.println("Erro ao carregar tela de conexão: " + e.getMessage());
            Platform.exit();
        }
    }
}