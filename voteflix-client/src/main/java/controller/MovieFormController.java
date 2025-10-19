package controller;

import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import model.Movie;
import network.ServerConnection;
import org.json.JSONObject;
import session.SessionManager;
import util.StatusCodeHandler;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class MovieFormController {

    @FXML private Label titleLabel;
    @FXML private TextField titleField;
    @FXML private TextField directorField;
    @FXML private TextField yearField;
    @FXML private TextField genresField;
    @FXML private TextArea synopsisArea;

    private Stage dialogStage;
    private Movie movie;
    private boolean isEditMode = false;

    public void setDialogStage(Stage dialogStage) {
        this.dialogStage = dialogStage;
    }

    public void prepareForm(Movie movie, boolean isEdit) {
        this.isEditMode = isEdit;
        this.movie = movie;

        titleLabel.setText(isEdit ? "Editar Filme" : "Adicionar Novo Filme");

        if (isEdit && movie != null) {
            titleField.setText(movie.getTitulo());
            directorField.setText(movie.getDiretor());
            yearField.setText(movie.getAno());
            genresField.setText(movie.getGenerosString());
            synopsisArea.setText(movie.getSinopse());
        }
    }

    @FXML
    private void handleSave() {
        String title = titleField.getText();
        String director = directorField.getText();
        String year = yearField.getText();
        List<String> genres = Arrays.stream(genresField.getText().split(","))
                .map(String::trim)
                .collect(Collectors.toList());
        String synopsis = synopsisArea.getText();

        Task<String> saveTask = new Task<>() {
            @Override
            protected String call() {
                String token = SessionManager.getInstance().getToken();
                if (isEditMode) {
                    String movieId = (movie != null) ? String.valueOf(movie.getId()) : "0";
                    return ServerConnection.getInstance().updateMovie(token, movieId, title, director, year, genres, synopsis);
                } else {
                    return ServerConnection.getInstance().createMovie(token, title, director, year, genres, synopsis);
                }
            }
        };

        saveTask.setOnSucceeded(e -> {
            String responseJson = saveTask.getValue();
            JSONObject response = new JSONObject(responseJson);
            String status = response.getString("status");

            if ("200".equals(status) || "201".equals(status)) {
                showAlert(Alert.AlertType.INFORMATION, "Sucesso", "Filme salvo com sucesso.");
                dialogStage.close();
            } else {
                String finalMessage = StatusCodeHandler.getMessage(status);
                showAlert(Alert.AlertType.ERROR, "Erro", finalMessage);
            }
        });

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