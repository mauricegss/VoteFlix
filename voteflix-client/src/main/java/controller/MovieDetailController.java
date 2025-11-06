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
import javafx.scene.text.Text;
import javafx.stage.Modality;
import javafx.stage.Stage;
import model.Movie;
import model.Review;
import network.ServerConnection;
import org.json.JSONArray;
import org.json.JSONObject;
import session.SessionManager;
// import util.TokenDecoder; // Não é mais usado aqui

import java.io.IOException;
import java.util.Objects;
import java.util.Optional;

public class MovieDetailController {

    @FXML private Label titleLabel;
    @FXML private Label directorAndYearLabel;
    @FXML private Label genresLabel;
    @FXML private Label ratingLabel;
    @FXML private Text synopsisText;
    @FXML private ListView<Review> reviewsListView;
    @FXML private HBox reviewActionBox;
    @FXML private Button addEditReviewButton;
    @FXML private Label statusLabel;

    private Movie currentMovie;
    private final ObservableList<Review> currentReviews = FXCollections.observableArrayList();
    private Review userExistingReview = null;
    private String currentUserRole;
    private Integer currentUserId;


    @FXML
    private void initialize() {
        // Pega o Role e ID que o LoginController salvou
        currentUserRole = SessionManager.getInstance().getRole();
        currentUserId = SessionManager.getInstance().getUserId();

        synopsisText.getStyleClass().add("synopsis-text");
        synopsisText.setStyle("-fx-fill: #E0E0E0;");

        setupReviewListViewCellFactory();
        reviewsListView.setItems(currentReviews);
        reviewsListView.setPlaceholder(new Label("Carregando avaliações..."));

        if ("user".equals(currentUserRole)) {
            reviewActionBox.setVisible(true);
            reviewActionBox.setManaged(true);
        } else {
            reviewActionBox.setVisible(false);
            reviewActionBox.setManaged(false);
        }
    }

    public void setMovieDetails(Movie movie, JSONArray reviewsArray) {
        this.currentMovie = movie;
        this.userExistingReview = null;
        currentReviews.clear();

        if (movie != null) {
            titleLabel.setText(movie.getTitulo());
            directorAndYearLabel.setText(String.format("%s (%s)", movie.getDiretor(), movie.getAno()));
            genresLabel.setText("Gêneros: " + movie.getGenerosString());
            updateRatingLabel(movie.getNota(), movie.getQtdAvaliacoes());
            synopsisText.setText(movie.getSinopse());

            if (reviewsArray != null) {
                for (int i = 0; i < reviewsArray.length(); i++) {
                    JSONObject reviewJson = reviewsArray.getJSONObject(i);
                    Review review = parseReviewFromJson(reviewJson);
                    currentReviews.add(review);
                    if (currentUserId != null && review.getIdUsuario() == currentUserId) {
                        userExistingReview = review;
                    }
                }
            }

            if ("user".equals(currentUserRole)) {
                addEditReviewButton.setText(userExistingReview != null ? "Editar Minha Avaliação" : "Adicionar Avaliação");
            }

            if (currentReviews.isEmpty()) {
                reviewsListView.setPlaceholder(new Label("Ainda não há avaliações para este filme."));
            }

        } else {
            reviewsListView.setPlaceholder(new Label("Não foi possível carregar os detalhes do filme."));
        }
    }

    private void updateRatingLabel(double nota, int qtdAvaliacoes) {
        ratingLabel.setText(String.format("Nota: %.1f/5.0 (%d avaliações)", nota, qtdAvaliacoes));
    }


    private Review parseReviewFromJson(JSONObject json) {
        Review review = new Review();
        review.setId(json.optInt("id", 0));
        review.setIdFilme(json.optInt("id_filme", 0));
        review.setIdUsuario(json.optInt("id_usuario", 0));
        review.setNomeUsuario(json.optString("nome_usuario", "Desconhecido"));
        review.setNota(json.optInt("nota", 0));
        review.setTitulo(json.optString("titulo", ""));
        review.setDescricao(json.optString("descricao", ""));
        review.setData(json.optString("data", ""));
        return review;
    }

    private void setupReviewListViewCellFactory() {
        reviewsListView.setCellFactory(lv -> new ListCell<>() {
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

                editButton.setStyle("-fx-padding: 3px 8px;");
                deleteButton.setStyle("-fx-padding: 3px 8px;");
                deleteButton.getStyleClass().add("delete-button");

                hbox.getChildren().addAll(reviewContent, spacer, editButton, deleteButton);
                hbox.setAlignment(Pos.CENTER_LEFT);
                hbox.setPadding(new Insets(5, 5, 5, 5));

                editButton.setOnAction(event -> {
                    Review review = getItem();
                    if (review != null) {
                        openReviewForm(review);
                    }
                });

                deleteButton.setOnAction(event -> {
                    Review review = getItem();
                    if (review != null) {
                        handleDeleteReview(review);
                    }
                });
            }

            @Override
            protected void updateItem(Review review, boolean empty) {
                super.updateItem(review, empty);
                if (empty || review == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    String reviewDisplay = String.format("[%d★] %s (%s - %s)\n%s",
                            review.getNota(),
                            review.getTitulo().isEmpty() ? "(Sem Título)" : review.getTitulo(),
                            review.getNomeUsuario(),
                            review.getData(),
                            review.getDescricao()
                    );
                    reviewTextLabel.setText(reviewDisplay);

                    boolean canEdit = "user".equals(currentUserRole) && currentUserId != null && review.getIdUsuario() == currentUserId;
                    boolean canDelete = canEdit || "admin".equals(currentUserRole);

                    editButton.setVisible(canEdit);
                    editButton.setManaged(canEdit);
                    deleteButton.setVisible(canDelete);
                    deleteButton.setManaged(canDelete);

                    setGraphic(hbox);
                }
            }
        });
    }

    @FXML
    private void handleAddEditReview() {
        openReviewForm(userExistingReview);
    }

    private void openReviewForm(Review reviewToEdit) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/ReviewFormView.fxml"));
            Stage stage = new Stage();
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setTitle(reviewToEdit != null ? "Editar Avaliação" : "Adicionar Avaliação");
            Scene scene = new Scene(loader.load());
            scene.getStylesheets().add(Objects.requireNonNull(getClass().getResource("/view/style.css")).toExternalForm());
            stage.setScene(scene);

            ReviewFormController controller = loader.getController();
            controller.setDialogStage(stage);
            controller.setDetailController(this);
            controller.prepareForm(currentMovie, reviewToEdit);

            stage.showAndWait();

        } catch (IOException e) {
            System.err.println("Erro ao abrir formulário de review: " + e.getMessage());
            showErrorAlert("Não foi possível abrir o formulário de avaliação.");
        }
    }


    private void handleDeleteReview(Review review) {
        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
        confirmation.setTitle("Confirmar Exclusão");
        confirmation.setHeaderText("Tem certeza que deseja excluir esta avaliação?");
        confirmation.setContentText("Avaliação de: " + review.getNomeUsuario());
        confirmation.getDialogPane().getStylesheets().add(Objects.requireNonNull(getClass().getResource("/view/style.css")).toExternalForm());
        confirmation.getDialogPane().getStyleClass().add("root");

        Optional<ButtonType> result = confirmation.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            statusLabel.setText("Excluindo avaliação...");
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
                    showErrorAlert("Erro de comunicação ao excluir avaliação.");
                    return;
                }
                try {
                    JSONObject response = new JSONObject(responseJson);
                    String status = response.getString("status");
                    if ("200".equals(status)) {
                        showAlert(Alert.AlertType.INFORMATION, "Sucesso", "Avaliação excluída com sucesso.");
                        refreshReviews();
                    } else {
                        String finalMessage = response.optString("mensagem", "Erro ao excluir avaliação.");
                        showErrorAlert(finalMessage);
                    }
                } catch (Exception ex) {
                    showErrorAlert("Erro ao processar resposta da exclusão: " + ex.getMessage());
                }
            }));

            deleteTask.setOnFailed(e -> Platform.runLater(() -> {
                statusLabel.setText("");
                showErrorAlert("Falha na tarefa de excluir avaliação.");
            }));

            new Thread(deleteTask).start();
        }
    }

    public void refreshReviews() {
        if (currentMovie == null) return;
        statusLabel.setText("Atualizando avaliações...");

        Task<String> refreshTask = new Task<>() {
            @Override
            protected String call() {
                String token = SessionManager.getInstance().getToken();
                return ServerConnection.getInstance().getMovieById(token, String.valueOf(currentMovie.getId()));
            }
        };

        refreshTask.setOnSucceeded(e -> Platform.runLater(() -> {
            statusLabel.setText("");
            String responseJson = refreshTask.getValue();
            if (responseJson == null) {
                showErrorAlert("Erro de comunicação ao atualizar avaliações.");
                return;
            }
            try {
                JSONObject response = new JSONObject(responseJson);
                if ("200".equals(response.getString("status"))) {
                    Movie updatedMovie = Movie.fromJson(response.getJSONObject("filme"));
                    JSONArray reviewsArray = response.getJSONArray("reviews");
                    setMovieDetails(updatedMovie, reviewsArray);
                } else {
                    String message = response.optString("mensagem", "Não foi possível atualizar as avaliações.");
                    showErrorAlert(message);
                }
            } catch (Exception ex) {
                showErrorAlert("Erro ao processar atualização: " + ex.getMessage());
            }
        }));

        refreshTask.setOnFailed(e -> Platform.runLater(() -> {
            statusLabel.setText("");
            showErrorAlert("Falha na tarefa de atualização das avaliações.");
        }));

        new Thread(refreshTask).start();
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
            } catch (NullPointerException e) {
                System.err.println("Não foi possível carregar o CSS para o Alerta.");
            }
            alert.showAndWait();
        });
    }
}