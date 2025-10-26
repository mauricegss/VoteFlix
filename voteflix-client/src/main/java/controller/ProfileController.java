package controller;

import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import java.io.IOException;
import java.util.Objects;

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
        welcomeLabel.setText("Minha Conta");
    }

    @FXML
    private void handleUpdatePassword() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Atualização de Senha");
        dialog.setHeaderText("Digite sua nova senha.");
        dialog.setContentText("Nova Senha:");
        dialog.getDialogPane().getStylesheets().add(Objects.requireNonNull(getClass().getResource("/view/style.css")).toExternalForm());
        dialog.getDialogPane().getStyleClass().add("root");

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(newPassword -> {
            statusLabel.setText("Atualizando senha...");
            Task<String> updateTask = createUpdatePasswordTask(newPassword);
            updateTask.setOnSucceeded(e -> handleUpdatePasswordResponse(updateTask.getValue()));
            updateTask.setOnFailed(e -> handleUpdatePasswordResponse(null));
            new Thread(updateTask).start();
        });
    }

    private Task<String> createUpdatePasswordTask(String newPassword) {
        return new Task<>() {
            @Override
            protected String call() {
                String token = SessionManager.getInstance().getToken();
                return ServerConnection.getInstance().updateOwnPassword(newPassword, token);
            }
        };
    }


    @FXML
    private void handleListOwnUser() {
        statusLabel.setText("Buscando informações...");
        Task<String> listUserTask = createListOwnUserTask();
        listUserTask.setOnSucceeded(e -> handleListOwnUserResponse(listUserTask.getValue()));
        listUserTask.setOnFailed(e -> Platform.runLater(() -> statusLabel.setText("Falha ao buscar informações do usuário.")));
        new Thread(listUserTask).start();
    }

    private Task<String> createListOwnUserTask() {
        return new Task<>() {
            @Override
            protected String call() {
                String token = SessionManager.getInstance().getToken();
                return ServerConnection.getInstance().listOwnUser(token);
            }
        };
    }

    private void handleListOwnUserResponse(String responseJson) {
        Platform.runLater(() -> {
            statusLabel.setText("");
            if (responseJson == null) {
                showAlert(Alert.AlertType.ERROR, "Erro", "Erro de comunicação com o servidor.");
                return;
            }
            try {
                JSONObject response = new JSONObject(responseJson);
                String status = response.getString("status");
                if ("200".equals(status)) {
                    String username = response.getString("usuario");
                    showAlert(Alert.AlertType.INFORMATION, "Informações do Usuário", "Seu nome de usuário é: " + username);
                } else {
                    String finalMessage = response.optString("mensagem", "Erro ao listar usuário.");
                    showAlert(Alert.AlertType.ERROR, "Erro", finalMessage);
                }
            } catch (Exception ex) {
                showAlert(Alert.AlertType.ERROR, "Erro", "Erro ao processar resposta do servidor: " + ex.getMessage());
            }
        });
    }

    @FXML
    private void handleDeleteAccount() {
        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
        confirmation.setTitle("Confirmar Exclusão");
        confirmation.setHeaderText("Você tem certeza que deseja apagar sua conta?");
        confirmation.setContentText("Esta ação é irreversível.");
        confirmation.getDialogPane().getStylesheets().add(Objects.requireNonNull(getClass().getResource("/view/style.css")).toExternalForm());
        confirmation.getDialogPane().getStyleClass().add("root");


        Optional<ButtonType> result = confirmation.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            statusLabel.setText("Apagando conta...");
            Task<Void> deleteTask = createDeleteAccountTask();
            deleteTask.setOnSucceeded(e -> {
                SessionManager.getInstance().clearSession();
                closeWindowAndOpenConnection();
            });
            deleteTask.setOnFailed(e -> Platform.runLater(() -> {
                statusLabel.setText("");
                showAlert(Alert.AlertType.ERROR, "Erro", "Falha ao tentar apagar a conta.");
            }));
            new Thread(deleteTask).start();
        }
    }

    private Task<Void> createDeleteAccountTask() {
        return new Task<>() {
            @Override
            protected Void call() {
                String token = SessionManager.getInstance().getToken();
                ServerConnection.getInstance().deleteOwnUser(token);
                return null;
            }
        };
    }

    private void handleUpdatePasswordResponse(String responseJson) {
        Platform.runLater(() -> {
            statusLabel.setText("");
            if (responseJson == null) {
                showAlert(Alert.AlertType.ERROR, "Erro", "Erro de comunicação com o servidor.");
                return;
            }
            try {
                JSONObject response = new JSONObject(responseJson);
                String status = response.getString("status");
                if ("200".equals(status)) {
                    showAlert(Alert.AlertType.INFORMATION, "Sucesso", "Senha atualizada com sucesso!");
                } else {
                    String finalMessage = response.optString("mensagem", "Ocorreu um erro ao atualizar a senha.");
                    showAlert(Alert.AlertType.ERROR, "Erro", finalMessage);
                }
            } catch (Exception ex) {
                showAlert(Alert.AlertType.ERROR, "Erro", "Erro ao processar resposta do servidor: " + ex.getMessage());
            }
        });
    }

    private void showAlert(Alert.AlertType type, String title, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(type);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.getDialogPane().getStylesheets().add(Objects.requireNonNull(getClass().getResource("/view/style.css")).toExternalForm());
            alert.getDialogPane().getStyleClass().add("root");
            alert.showAndWait();
        });
    }

    private void closeWindowAndOpenConnection() {
        try {
            Stage currentStage = (Stage) statusLabel.getScene().getWindow();
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
}