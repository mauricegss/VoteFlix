package controller;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import model.Movie;
import model.Review;
import network.ServerConnection;
import org.json.JSONObject;
import session.SessionManager;

public class ReviewFormController {

    @FXML private Label titleLabel;
    @FXML private Label movieTitleLabel;
    @FXML private Slider ratingSlider;
    @FXML private Label ratingValueLabel;
    @FXML private TextField titleField;
    @FXML private TextArea descriptionArea;
    @FXML private Button saveButton;
    @FXML private Label statusLabel;

    private Stage dialogStage;
    private Movie movie;
    private Review existingReview;
    private boolean isEditMode = false;

    private MovieDetailController detailController;
    private MyReviewsController myReviewsController;

    @FXML
    private void initialize() {
        ratingSlider.valueProperty().addListener((obs, oldVal, newVal) ->
                ratingValueLabel.setText(String.valueOf(newVal.intValue())));
    }

    public void setDialogStage(Stage dialogStage) {
        this.dialogStage = dialogStage;
    }

    public void setDetailController(MovieDetailController detailController) {
        this.detailController = detailController;
    }

    public void setMyReviewsController(MyReviewsController myReviewsController) {
        this.myReviewsController = myReviewsController;
    }

    public void prepareForm(Movie movie, Review review) {
        this.movie = movie;
        this.existingReview = review;
        this.isEditMode = (review != null);

        titleLabel.setText(isEditMode ? "Editar Avaliação" : "Adicionar Avaliação");
        String movieName = (movie != null) ? movie.getTitulo() : "Desconhecido";
        movieTitleLabel.setText("Filme: " + movieName);

        if (isEditMode) {
            ratingSlider.setValue(existingReview.getNota());
            ratingValueLabel.setText(String.valueOf(existingReview.getNota()));
            titleField.setText(existingReview.getTitulo());
            descriptionArea.setText(existingReview.getDescricao());
        } else {
            ratingSlider.setValue(3.0);
            ratingValueLabel.setText("3");
        }
    }

    @FXML
    private void handleSave() {
        int rating = (int) ratingSlider.getValue();
        String title = titleField.getText();
        String description = descriptionArea.getText();

        saveButton.setDisable(true);
        statusLabel.setText("Salvando...");

        Task<String> saveTask = new Task<>() {
            @Override
            protected String call() {
                String token = SessionManager.getInstance().getToken();
                if (isEditMode) {
                    return ServerConnection.getInstance().editReview(
                            token,
                            String.valueOf(existingReview.getId()),
                            rating,
                            title,
                            description
                    );
                } else {
                    return ServerConnection.getInstance().createReview(
                            token,
                            String.valueOf(movie.getId()),
                            rating,
                            title,
                            description
                    );
                }
            }
        };

        saveTask.setOnSucceeded(e -> Platform.runLater(() -> {
            saveButton.setDisable(false);
            statusLabel.setText("");
            String responseJson = saveTask.getValue();
            if (responseJson == null) {
                showAlert(Alert.AlertType.ERROR, "Erro", "Erro de comunicação ao salvar avaliação.");
                return;
            }
            try {
                JSONObject response = new JSONObject(responseJson);
                String status = response.getString("status");

                if ("200".equals(status) || "201".equals(status)) {
                    // --- CORREÇÃO AQUI ---
                    // Usa a mensagem vinda do servidor (ex: "Sucesso: Operação realizada...")
                    showAlert(Alert.AlertType.INFORMATION, "Sucesso", response.getString("mensagem"));

                    if (detailController != null) {
                        detailController.refreshReviews();
                    }
                    if (myReviewsController != null) {
                        myReviewsController.loadAndShowReviews();
                    }

                    dialogStage.close();
                } else {
                    // O servidor pode mandar mensagens como "Erro: Você não tem permissão..."
                    String finalMessage = response.optString("mensagem", "Erro ao salvar avaliação.");
                    statusLabel.setText(finalMessage);
                    // Opcional: mostrar alerta se quiser forçar o popup
                    // showAlert(Alert.AlertType.ERROR, "Erro", finalMessage);
                }
            } catch (Exception ex) {
                showAlert(Alert.AlertType.ERROR, "Erro", "Erro ao processar resposta do servidor: " + ex.getMessage());
            }

        }));

        saveTask.setOnFailed(e -> Platform.runLater(() -> {
            saveButton.setDisable(false);
            statusLabel.setText("");
            showAlert(Alert.AlertType.ERROR, "Erro", "Falha na tarefa de salvar avaliação.");
        }));

        new Thread(saveTask).start();
    }

    @FXML
    private void handleCancel() {
        dialogStage.close();
    }

    private void showAlert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}