package controller;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import model.Movie;
import network.ServerConnection;
import org.json.JSONArray;
import org.json.JSONObject;
import session.SessionManager;

import java.io.IOException;
import java.util.Optional;

public class MovieManagementController {

    @FXML private TableView<Movie> moviesTable;
    @FXML private TableColumn<Movie, Integer> idColumn;
    @FXML private TableColumn<Movie, String> titleColumn;
    @FXML private TableColumn<Movie, String> directorColumn;
    @FXML private TableColumn<Movie, String> yearColumn;
    @FXML private TableColumn<Movie, String> genresColumn;
    @FXML private TableColumn<Movie, String> synopsisColumn;
    @FXML private TableColumn<Movie, Double> ratingColumn;
    @FXML private TableColumn<Movie, Void> actionsColumn;
    @FXML private TextField searchIdField;
    @FXML private Button editButton;
    @FXML private Button deleteButton;

    private final ObservableList<Movie> movieList = FXCollections.observableArrayList();

    @FXML
    private void initialize() {
        idColumn.setCellValueFactory(new PropertyValueFactory<>("id"));
        titleColumn.setCellValueFactory(new PropertyValueFactory<>("titulo"));
        directorColumn.setCellValueFactory(new PropertyValueFactory<>("diretor"));
        yearColumn.setCellValueFactory(new PropertyValueFactory<>("ano"));
        genresColumn.setCellValueFactory(new PropertyValueFactory<>("generosString"));
        synopsisColumn.setCellValueFactory(new PropertyValueFactory<>("sinopse"));
        ratingColumn.setCellValueFactory(new PropertyValueFactory<>("nota"));
        setupActionsColumn();
        moviesTable.setItems(movieList);

        moviesTable.getSelectionModel().selectedItemProperty().addListener((obs, oldSelection, newSelection) -> {
            boolean isItemSelected = newSelection != null;
            editButton.setDisable(!isItemSelected);
            deleteButton.setDisable(!isItemSelected);
        });
    }

    public void loadMovies() {
        Task<String> loadMoviesTask = new Task<>() {
            @Override
            protected String call() {
                String token = SessionManager.getInstance().getToken();
                return ServerConnection.getInstance().listMovies(token);
            }
        };

        loadMoviesTask.setOnSucceeded(event -> {
            String responseJson = loadMoviesTask.getValue();
            Platform.runLater(() -> {
                movieList.clear();
                JSONObject response = new JSONObject(responseJson);
                if ("200".equals(response.getString("status"))) {
                    JSONArray movies = response.getJSONArray("filmes");
                    for (int i = 0; i < movies.length(); i++) {
                        movieList.add(Movie.fromJson(movies.getJSONObject(i)));
                    }
                } else {
                    String message = response.optString("mensagem", "Não foi possível carregar os filmes.");
                    showErrorAlert(message);
                }
            });
        });

        new Thread(loadMoviesTask).start();
    }

    private void setupActionsColumn() {
        actionsColumn.setCellFactory(param -> new TableCell<>() {
            private final Button detailsButton = new Button("Ver Detalhes");

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    HBox pane = new HBox(detailsButton);
                    pane.setAlignment(Pos.CENTER);
                    setGraphic(pane);
                    detailsButton.setOnAction(event -> {
                        Movie movie = getTableView().getItems().get(getIndex());
                        fetchAndShowDetails(String.valueOf(movie.getId()));
                    });
                }
            }
        });
    }

    @FXML
    private void handleAddMovie() {
        showMovieForm(null, false);
    }

    @FXML
    private void handleEditMovie() {
        Movie selectedMovie = moviesTable.getSelectionModel().getSelectedItem();
        showMovieForm(selectedMovie, true);
    }

    private void showMovieForm(Movie movie, boolean isEdit) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/MovieFormView.fxml"));
            Stage stage = new Stage();
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setTitle(isEdit ? "Editar Filme" : "Adicionar Novo Filme");
            stage.setScene(new Scene(loader.load()));

            MovieFormController controller = loader.getController();
            controller.prepareForm(movie, isEdit);
            controller.setDialogStage(stage);

            stage.showAndWait();

            loadMovies();
        } catch (IOException e) {
            System.err.println("Erro ao abrir formulário de filme: " + e.getMessage());
            showErrorAlert("Não foi possível abrir o formulário de filme.");
        }
    }

    private void fetchAndShowDetails(String movieId) {
        if (movieId == null || movieId.isEmpty()) return;

        Task<String> loadDetailsTask = new Task<>() {
            @Override
            protected String call() {
                String token = SessionManager.getInstance().getToken();
                return ServerConnection.getInstance().getMovieById(token, movieId);
            }
        };

        loadDetailsTask.setOnSucceeded(e -> {
            String responseJson = loadDetailsTask.getValue();
            JSONObject response = new JSONObject(responseJson);
            if ("200".equals(response.getString("status"))) {
                Movie detailedMovie = Movie.fromJson(response.getJSONObject("filme"));
                showDetailsWindow(detailedMovie);
            } else {
                String message = response.optString("mensagem", "Erro ao buscar detalhes do filme.");
                showErrorAlert(message);
            }
        });

        new Thread(loadDetailsTask).start();
    }

    @FXML
    private void handleSearchById() {
        String idText = searchIdField.getText().trim();
        fetchAndShowDetails(idText);
    }

    private void showDetailsWindow(Movie movie) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/MovieDetailView.fxml"));
            Stage stage = new Stage();
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setTitle("Detalhes do Filme");
            stage.setScene(new Scene(loader.load()));

            MovieDetailController controller = loader.getController();
            controller.setMovie(movie);

            stage.showAndWait();
        } catch (IOException e) {
            System.err.println("Erro ao abrir detalhes do filme: " + e.getMessage());
            showErrorAlert("Não foi possível abrir a tela de detalhes.");
        }
    }

    @FXML
    private void handleDeleteMovie() {
        Movie selectedMovie = moviesTable.getSelectionModel().getSelectedItem();

        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
        confirmation.setTitle("Confirmar Exclusão");
        confirmation.setHeaderText("Tem certeza que deseja excluir o filme '" + selectedMovie.getTitulo() + "'?");

        Optional<ButtonType> result = confirmation.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            Task<String> deleteTask = new Task<>() {
                @Override
                protected String call() {
                    String token = SessionManager.getInstance().getToken();
                    return ServerConnection.getInstance().deleteMovie(token, String.valueOf(selectedMovie.getId()));
                }
            };

            deleteTask.setOnSucceeded(e -> {
                String responseJson = deleteTask.getValue();
                JSONObject response = new JSONObject(responseJson);
                String status = response.getString("status");
                if ("200".equals(status)) {
                    Platform.runLater(() -> {
                        Alert alert = new Alert(Alert.AlertType.INFORMATION);
                        alert.setTitle("Sucesso");
                        alert.setHeaderText(null);
                        alert.setContentText("Filme excluído com sucesso.");
                        alert.showAndWait();
                        loadMovies();
                    });
                } else {
                    String finalMessage = response.optString("mensagem", "Erro ao excluir filme.");
                    Platform.runLater(() -> showErrorAlert(finalMessage));
                }
            });
            new Thread(deleteTask).start();
        }
    }

    private void showErrorAlert(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Erro");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}