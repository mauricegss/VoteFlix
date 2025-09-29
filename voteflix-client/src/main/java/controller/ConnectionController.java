package controller;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import network.ServerConnection;
import java.io.IOException;

public class ConnectionController {

    @FXML private TextField ipField;
    @FXML private TextField portField;
    @FXML private Label statusLabel;
    @FXML private Button connectButton;

    @FXML
    private void handleConnectButton() {
        String host = ipField.getText().trim();
        String portStr = portField.getText().trim();

        if (host.isEmpty() || portStr.isEmpty()) {
            statusLabel.setText("IP e Porta são obrigatórios.");
            return;
        }

        try {
            int port = Integer.parseInt(portStr);

            setUIState(true);
            statusLabel.setText("Conectando...");

            Task<Boolean> connectionTask = new Task<>() {
                @Override
                protected Boolean call() {
                    return ServerConnection.getInstance().connect(host, port);
                }
            };

            connectionTask.setOnSucceeded(event -> {
                boolean success = connectionTask.getValue();
                Platform.runLater(() -> {
                    if (success) {
                        try {
                            openLoginWindow();
                        } catch (IOException e) {
                            showLoginWindowErrorAlert();
                            setUIState(false);
                        }
                    } else {
                        statusLabel.setText("Falha ao conectar. Verifique o IP e a Porta.");
                        setUIState(false);
                    }
                });
            });

            new Thread(connectionTask).start();

        } catch (NumberFormatException e) {
            statusLabel.setText("Porta deve ser um número.");
        }
    }

    private void setUIState(boolean isConnecting) {
        connectButton.setDisable(isConnecting);
        ipField.setDisable(isConnecting);
        portField.setDisable(isConnecting);
    }

    private void openLoginWindow() throws IOException {
        Stage currentStage = (Stage) connectButton.getScene().getWindow();
        currentStage.close();

        FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/LoginView.fxml"));
        Stage stage = new Stage();
        stage.setTitle("Login / Cadastro");
        stage.setScene(new Scene(loader.load()));
        stage.show();
    }

    private void showLoginWindowErrorAlert() {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Erro");
        alert.setHeaderText(null);
        alert.setContentText("Não foi possível abrir a tela de login.");
        alert.showAndWait();
    }
}