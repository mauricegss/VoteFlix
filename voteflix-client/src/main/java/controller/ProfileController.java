package controller;

import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import java.io.IOException;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextInputDialog;
import javafx.stage.Stage;
import network.ServerConnection;
import org.json.JSONArray;
import org.json.JSONObject;
import session.SessionManager;

import java.util.Optional;

public class ProfileController {

    @FXML private Label welcomeLabel;
    @FXML private Label statusLabel;
    @FXML private Button viewReviewsButton;
    @FXML private Label reviewsLabel;
    @FXML private ListView<String> myReviewsListView;

    private final ObservableList<String> userReviewsList = FXCollections.observableArrayList();
    private boolean reviewsVisible = false;

    @FXML
    private void initialize() {
        welcomeLabel.setText("Bem-vindo(a) ao seu Perfil!");
        myReviewsListView.setItems(userReviewsList);
        myReviewsListView.setPlaceholder(new Label("Clique em 'Ver Minhas Avaliações' para carregar."));
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
    private void handleViewMyReviews() {
        if (reviewsVisible) {
            hideReviews();
        } else {
            loadAndShowReviews();
        }
    }

    private void loadAndShowReviews() {
        statusLabel.setText("Carregando avaliações...");
        viewReviewsButton.setDisable(true);
        myReviewsListView.setPlaceholder(new Label("Carregando..."));

        Task<String> loadReviewsTask = createLoadReviewsTask();
        loadReviewsTask.setOnSucceeded(e -> handleLoadReviewsResponse(loadReviewsTask.getValue()));
        loadReviewsTask.setOnFailed(e -> handleLoadReviewsFailure());
        new Thread(loadReviewsTask).start();
    }

    private Task<String> createLoadReviewsTask() {
        return new Task<>() {
            @Override
            protected String call() {
                String token = SessionManager.getInstance().getToken();
                return ServerConnection.getInstance().listUserReviews(token);
            }
        };
    }

    private void handleLoadReviewsResponse(String responseJson) {
        Platform.runLater(() -> {
            statusLabel.setText("");
            viewReviewsButton.setDisable(false);
            if (responseJson == null) {
                showAlert(Alert.AlertType.ERROR, "Erro", "Erro de comunicação ao carregar avaliações.");
                myReviewsListView.setPlaceholder(new Label("Erro de comunicação."));
                return;
            }
            try {
                JSONObject response = new JSONObject(responseJson);
                String status = response.getString("status");
                if ("200".equals(status)) {
                    JSONArray reviewsArray = response.getJSONArray("reviews");
                    userReviewsList.clear();
                    if (reviewsArray.isEmpty()) {
                        myReviewsListView.setPlaceholder(new Label("Você ainda não fez nenhuma avaliação."));
                    } else {
                        for (int i = 0; i < reviewsArray.length(); i++) {
                            JSONObject reviewJson = reviewsArray.getJSONObject(i);
                            String display = String.format("[%s★] %s (Filme ID: %s - %s): %s",
                                    reviewJson.optString("nota","?"),
                                    reviewJson.optString("titulo", "Avaliação"),
                                    reviewJson.optString("id_filme", "?"),
                                    reviewJson.optString("data", "sem data"),
                                    reviewJson.optString("descricao", "")
                            );
                            userReviewsList.add(display);
                        }
                    }
                    showReviewsUI();
                } else {
                    String message = response.optString("mensagem", "Não foi possível carregar suas avaliações.");
                    showAlert(Alert.AlertType.ERROR, "Erro", message);
                    myReviewsListView.setPlaceholder(new Label(message));
                }
            } catch (Exception ex) {
                showAlert(Alert.AlertType.ERROR, "Erro", "Erro ao processar suas avaliações: " + ex.getMessage());
                myReviewsListView.setPlaceholder(new Label("Erro ao processar dados."));
            }
        });
    }

    private void handleLoadReviewsFailure() {
        Platform.runLater(() -> {
            statusLabel.setText("");
            viewReviewsButton.setDisable(false);
            showAlert(Alert.AlertType.ERROR, "Erro", "Falha na tarefa de carregar avaliações.");
            myReviewsListView.setPlaceholder(new Label("Falha ao carregar."));
        });
    }


    private void showReviewsUI() {
        reviewsLabel.setVisible(true);
        reviewsLabel.setManaged(true);
        myReviewsListView.setVisible(true);
        myReviewsListView.setManaged(true);
        viewReviewsButton.setText("Esconder Avaliações");
        reviewsVisible = true;
        Stage stage = (Stage) statusLabel.getScene().getWindow();
        if (stage != null && stage.getHeight() < 500) {
            stage.setHeight(550);
        }

    }

    private void hideReviews() {
        reviewsLabel.setVisible(false);
        reviewsLabel.setManaged(false);
        myReviewsListView.setVisible(false);
        myReviewsListView.setManaged(false);
        viewReviewsButton.setText("Ver Minhas Avaliações");
        reviewsVisible = false;
        userReviewsList.clear();
        myReviewsListView.setPlaceholder(new Label("Clique em 'Ver Minhas Avaliações' para carregar."));
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
            Task<Void> deleteTask = createDeleteAccountTask(); // Usa o método extraído
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
            stage.setScene(new Scene(loader.load(), 300, 250));
            stage.show();

        } catch (IOException e) {
            System.err.println("Erro ao carregar tela de conexão: " + e.getMessage());
            Platform.exit();
        }
    }
}