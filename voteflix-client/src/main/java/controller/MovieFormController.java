package controller;

import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import model.Movie;
import network.ServerConnection;
import org.json.JSONObject;
import session.SessionManager;

import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;

public class MovieFormController {

    @FXML private Label titleLabel;
    @FXML private TextField titleField;
    @FXML private TextField directorField;
    @FXML private TextField yearField;
    @FXML private VBox genresVBox;
    @FXML private TextArea synopsisArea;

    private Stage dialogStage;
    private Movie movie;
    private boolean isEditMode = false;

    private static final List<String> PREDEFINED_GENRES = Arrays.asList(
            "Ação", "Aventura", "Comédia", "Drama", "Fantasia", "Ficção Científica",
            "Terror", "Romance", "Documentário", "Musical", "Animação"
    );

    @FXML
    private void initialize() {
        for (String genre : PREDEFINED_GENRES) {
            CheckBox cb = new CheckBox(genre);
            cb.setStyle("-fx-text-fill: #E0E0E0;");
            genresVBox.getChildren().add(cb);
        }
    }

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
            synopsisArea.setText(movie.getSinopse());

            List<String> movieGenres = movie.getGeneros();
            if (movieGenres != null) {
                for (Node node : genresVBox.getChildren()) {
                    if (node instanceof CheckBox cb) {
                        if (movieGenres.contains(cb.getText())) {
                            cb.setSelected(true);
                        }
                    }
                }
            }
        }
    }

    @FXML
    private void handleSave() {
        String title = titleField.getText();
        String director = directorField.getText();
        String year = yearField.getText();

        List<String> genres = new ArrayList<>();
        for (Node node : genresVBox.getChildren()) {
            if (node instanceof CheckBox cb) {
                if (cb.isSelected()) {
                    genres.add(cb.getText());
                }
            }
        }

        String synopsis = synopsisArea.getText();

        // 1. A Task agora é criada pelo novo método
        Task<String> saveTask = createSaveTask(title, director, year, genres, synopsis);

        // 2. A lógica de Succeeded (o que fazer após a Task rodar) continua aqui
        saveTask.setOnSucceeded(e -> {
            String responseJson = saveTask.getValue();
            if (responseJson == null) {
                showAlert(Alert.AlertType.ERROR, "Erro", "Erro de comunicação com o servidor.");
                return;
            }
            try {
                JSONObject response = new JSONObject(responseJson);
                String status = response.getString("status");

                if ("200".equals(status) || "201".equals(status)) {
                    showAlert(Alert.AlertType.INFORMATION, "Sucesso", "Filme salvo com sucesso.");
                    dialogStage.close();
                } else {
                    String finalMessage = response.optString("mensagem", "Erro ao salvar filme.");
                    showAlert(Alert.AlertType.ERROR, "Erro", finalMessage);
                }
            } catch (Exception ex) {
                showAlert(Alert.AlertType.ERROR, "Erro", "Erro ao processar resposta: " + ex.getMessage());
            }
        });

        // 3. A Task é iniciada em uma nova Thread
        new Thread(saveTask).start();
    }

    /**
     * NOVO MÉTODO EXTRAÍDO
     * Cria a Task de salvar (Create/Update) o filme.
     * Isso move a lógica de 'background' para fora do 'handleSave'.
     */
    private Task<String> createSaveTask(String title, String director, String year, List<String> genres, String synopsis) {
        return new Task<>() {
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