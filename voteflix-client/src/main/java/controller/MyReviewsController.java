package controller;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import network.ServerConnection;
import org.json.JSONArray;
import org.json.JSONObject;
import session.SessionManager;
import java.util.Objects;

public class MyReviewsController {

    @FXML private Label statusLabel;
    @FXML private Button viewReviewsButton;
    @FXML private Label reviewsLabel;
    @FXML private ListView<String> myReviewsListView;

    private final ObservableList<String> userReviewsList = FXCollections.observableArrayList();
    private boolean reviewsVisible = false;

    @FXML
    private void initialize() {
        myReviewsListView.setItems(userReviewsList);
        myReviewsListView.setPlaceholder(new Label("Clique em 'Carregar Minhas Avaliações' para começar."));
    }

    @FXML
    private void handleViewMyReviews() {
        if (reviewsVisible) {
            hideReviews();
        } else {
            loadAndShowReviews();
        }
    }

    public void loadAndShowReviews() {
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
                showErrorAlert("Erro de comunicação ao carregar avaliações.");
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
                            String display = String.format("[%s★] %s (Filme ID: %s - %s)\n%s",
                                    reviewJson.optString("nota","?"),
                                    reviewJson.optString("titulo", "(Sem Título)"),
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
                    showErrorAlert(message);
                    myReviewsListView.setPlaceholder(new Label(message));
                }
            } catch (Exception ex) {
                showErrorAlert("Erro ao processar suas avaliações: " + ex.getMessage());
                myReviewsListView.setPlaceholder(new Label("Erro ao processar dados."));
            }
        });
    }

    private void handleLoadReviewsFailure() {
        Platform.runLater(() -> {
            statusLabel.setText("");
            viewReviewsButton.setDisable(false);
            showErrorAlert("Falha na tarefa de carregar avaliações.");
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
    }

    private void hideReviews() {
        reviewsLabel.setVisible(false);
        reviewsLabel.setManaged(false);
        myReviewsListView.setVisible(false);
        myReviewsListView.setManaged(false);
        viewReviewsButton.setText("Carregar Minhas Avaliações");
        reviewsVisible = false;
        userReviewsList.clear();
        myReviewsListView.setPlaceholder(new Label("Clique em 'Carregar Minhas Avaliações' para começar."));
    }

    private void showErrorAlert(String message) {
        showAlert(message);
    }

    private void showAlert(String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Erro");
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.getDialogPane().getStylesheets().add(Objects.requireNonNull(getClass().getResource("/view/style.css")).toExternalForm());
            alert.getDialogPane().getStyleClass().add("root");
            alert.showAndWait();
        });
    }
}