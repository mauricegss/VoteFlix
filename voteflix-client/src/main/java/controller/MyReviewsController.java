package controller;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import model.Movie;
import model.Review;
import network.ServerConnection;
import org.json.JSONArray;
import org.json.JSONObject;
import session.SessionManager;

import java.io.IOException;
import java.util.Objects;
import java.util.Optional;

public class MyReviewsController {

    @FXML private Label statusLabel;
    @FXML private ListView<Review> myReviewsListView;

    private final ObservableList<Review> userReviewsList = FXCollections.observableArrayList();

    @FXML
    private void initialize() {
        myReviewsListView.setItems(userReviewsList);
        myReviewsListView.setPlaceholder(new Label("Carregando suas avaliações..."));
        setupReviewListViewCellFactory();
    }

    public void loadAndShowReviews() {
        statusLabel.setText("Carregando avaliações...");
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
                            JSONObject rJson = reviewsArray.getJSONObject(i);
                            Review review = new Review();
                            review.setId(rJson.optInt("id"));
                            review.setIdFilme(rJson.optInt("id_filme"));
                            review.setNota(rJson.optInt("nota"));
                            review.setTitulo(rJson.optString("titulo"));
                            review.setDescricao(rJson.optString("descricao"));
                            review.setData(rJson.optString("data"));
                            review.setNomeUsuario(rJson.optString("nome_usuario"));
                            review.setEditado(rJson.optString("editado", "false"));

                            userReviewsList.add(review);
                        }
                    }
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

    private void setupReviewListViewCellFactory() {
        myReviewsListView.setCellFactory(lv -> new ListCell<>() {
            private final HBox hbox = new HBox(10);
            private final VBox reviewContent = new VBox(5);
            private final Label reviewTextLabel = new Label();
            private final Button editButton = new Button("Editar");
            private final Button deleteButton = new Button("Excluir");
            private final Pane spacer = new Pane();

            {
                reviewTextLabel.setWrapText(true);
                reviewContent.getChildren().add(reviewTextLabel);
                HBox.setHgrow(spacer, Priority.ALWAYS);

                editButton.getStyleClass().add("button");
                deleteButton.getStyleClass().add("delete-button");

                editButton.setOnAction(event -> {
                    Review review = getItem();
                    if (review != null) {
                        handleEditReview(review);
                    }
                });

                deleteButton.setOnAction(event -> {
                    Review review = getItem();
                    if (review != null) {
                        handleDeleteReview(review);
                    }
                });

                hbox.getChildren().addAll(reviewContent, spacer, editButton, deleteButton);
                hbox.setAlignment(Pos.CENTER_LEFT);
                hbox.setPadding(new Insets(10));
            }

            @Override
            protected void updateItem(Review review, boolean empty) {
                super.updateItem(review, empty);
                if (empty || review == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    reviewTextLabel.setText(formatReviewDisplay(review));
                    setGraphic(hbox);
                }
            }
        });
    }

    private String formatReviewDisplay(Review review) {
        String editedSuffix = "true".equalsIgnoreCase(review.getEditado()) ? " (Editado)" : "";

        return String.format("[%d★] %s\nData: %s%s\n%s",
                review.getNota(),
                (review.getTitulo() != null && !review.getTitulo().isEmpty()) ? review.getTitulo() : "(Sem Título)",
                review.getData(),
                editedSuffix,
                review.getDescricao()
        );
    }

    private void handleEditReview(Review review) {
        statusLabel.setText("Preparando edição...");
        Task<String> fetchMovieTask = new Task<>() {
            @Override
            protected String call() {
                String token = SessionManager.getInstance().getToken();
                return ServerConnection.getInstance().getMovieById(token, String.valueOf(review.getIdFilme()));
            }
        };

        fetchMovieTask.setOnSucceeded(e -> {
            statusLabel.setText("");
            String responseJson = fetchMovieTask.getValue();
            if (responseJson != null) {
                try {
                    JSONObject response = new JSONObject(responseJson);
                    if ("200".equals(response.getString("status"))) {
                        JSONObject movieJson = response.getJSONObject("filme");
                        Movie movie = Movie.fromJson(movieJson);
                        openReviewForm(movie, review);
                    } else {
                        showErrorAlert("Não foi possível buscar dados do filme para edição.");
                    }
                } catch (Exception ex) {
                    showErrorAlert("Erro ao processar dados do filme: " + ex.getMessage());
                }
            } else {
                showErrorAlert("Erro de comunicação ao buscar filme.");
            }
        });

        fetchMovieTask.setOnFailed(e -> {
            statusLabel.setText("");
            showErrorAlert("Falha ao buscar filme.");
        });

        new Thread(fetchMovieTask).start();
    }

    private void openReviewForm(Movie movie, Review review) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/ReviewFormView.fxml"));
            Stage stage = new Stage();
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setTitle("Editar Avaliação");
            Scene scene = new Scene(loader.load());
            scene.getStylesheets().add(Objects.requireNonNull(getClass().getResource("/view/style.css")).toExternalForm());
            stage.setScene(scene);

            ReviewFormController controller = loader.getController();
            controller.setDialogStage(stage);
            controller.setMyReviewsController(this);
            controller.prepareForm(movie, review);

            stage.showAndWait();

        } catch (IOException e) {
            showErrorAlert("Não foi possível abrir o formulário: " + e.getMessage());
        }
    }

    private void handleDeleteReview(Review review) {
        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
        confirmation.setTitle("Confirmar Exclusão");
        confirmation.setHeaderText("Tem certeza que deseja excluir esta avaliação?");
        confirmation.setContentText("Esta ação é irreversível.");
        confirmation.getDialogPane().getStylesheets().add(Objects.requireNonNull(getClass().getResource("/view/style.css")).toExternalForm());
        confirmation.getDialogPane().getStyleClass().add("root");

        Optional<ButtonType> result = confirmation.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            statusLabel.setText("Excluindo...");
            Task<String> deleteTask = new Task<>() {
                @Override
                protected String call() {
                    String token = SessionManager.getInstance().getToken();
                    return ServerConnection.getInstance().deleteReview(token, String.valueOf(review.getId()));
                }
            };

            deleteTask.setOnSucceeded(e -> Platform.runLater(() -> {
                statusLabel.setText("");
                String responseJson = deleteTask.getValue();
                if (responseJson == null) {
                    showErrorAlert("Erro de comunicação ao excluir.");
                    return;
                }
                try {
                    JSONObject response = new JSONObject(responseJson);
                    if ("200".equals(response.getString("status"))) {
                        showAlert(Alert.AlertType.INFORMATION, "Sucesso", response.getString("mensagem"));
                        loadAndShowReviews();
                    } else {
                        showErrorAlert(response.optString("mensagem", "Erro ao excluir."));
                    }
                } catch (Exception ex) {
                    showErrorAlert("Erro ao processar resposta: " + ex.getMessage());
                }
            }));

            deleteTask.setOnFailed(e -> Platform.runLater(() -> {
                statusLabel.setText("");
                showErrorAlert("Falha ao excluir avaliação.");
            }));

            new Thread(deleteTask).start();
        }
    }

    private void handleLoadReviewsFailure() {
        Platform.runLater(() -> {
            statusLabel.setText("");
            showErrorAlert("Falha na tarefa de carregar avaliações.");
            myReviewsListView.setPlaceholder(new Label("Falha ao carregar."));
        });
    }

    private void showErrorAlert(String message) {
        showAlert(Alert.AlertType.ERROR, "Erro", message);
    }

    private void showAlert(Alert.AlertType type, String title, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(type);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);
            try {
                alert.getDialogPane().getStylesheets().add(Objects.requireNonNull(getClass().getResource("/view/style.css")).toExternalForm());
                alert.getDialogPane().getStyleClass().add("root");
            } catch (Exception ignored) {}
            alert.showAndWait();
        });
    }
}