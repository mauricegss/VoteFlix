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
import javafx.scene.control.Label;
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
import java.util.Objects;
import java.util.Optional;

// Nome da classe atualizado
public class MoviesController {

    @FXML private TableView<Movie> moviesTable;
    @FXML private TableColumn<Movie, Integer> idColumn;
    @FXML private TableColumn<Movie, String> titleColumn;
    @FXML private TableColumn<Movie, String> directorColumn;
    @FXML private TableColumn<Movie, String> yearColumn;
    @FXML private TableColumn<Movie, String> genresColumn;
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
        ratingColumn.setCellValueFactory(new PropertyValueFactory<>("nota"));
        setupActionsColumn();
        moviesTable.setItems(movieList);
        moviesTable.setPlaceholder(new Label("Carregando filmes..."));

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
                if (responseJson == null) {
                    showErrorAlert("Erro de comunicação ao carregar filmes.");
                    moviesTable.setPlaceholder(new Label("Erro ao carregar filmes."));
                    return;
                }
                try {
                    JSONObject response = new JSONObject(responseJson);
                    if ("200".equals(response.getString("status"))) {
                        JSONArray movies = response.getJSONArray("filmes");
                        if (movies.isEmpty()){
                            moviesTable.setPlaceholder(new Label("Nenhum filme cadastrado no momento."));
                        } else {
                            for (int i = 0; i < movies.length(); i++) {
                                movieList.add(Movie.fromJson(movies.getJSONObject(i)));
                            }
                        }
                    } else {
                        String message = response.optString("mensagem", "Não foi possível carregar os filmes.");
                        showErrorAlert(message);
                        moviesTable.setPlaceholder(new Label(message));
                    }
                } catch (Exception e) {
                    showErrorAlert("Erro ao processar a resposta do servidor: " + e.getMessage());
                    moviesTable.setPlaceholder(new Label("Erro ao processar dados."));
                }
            });
        });

        loadMoviesTask.setOnFailed(event -> Platform.runLater(() -> {
            showErrorAlert("Falha na tarefa de carregar filmes.");
            moviesTable.setPlaceholder(new Label("Falha ao carregar filmes."));
        }));

        new Thread(loadMoviesTask).start();
    }

    private void setupActionsColumn() {
        actionsColumn.setCellFactory(param -> new TableCell<>() {
            private final Button detailsButton = new Button("Ver Detalhes");

            {
                detailsButton.setStyle("-fx-padding: 5px 10px;");

                detailsButton.setOnAction(event -> {
                    Movie movie = getTableView().getItems().get(getIndex());
                    fetchAndShowDetails(String.valueOf(movie.getId()));
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    HBox pane = new HBox(detailsButton);
                    pane.setAlignment(Pos.CENTER);
                    setGraphic(pane);
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
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/MovieFormVIew.fxml"));
            Stage stage = new Stage();
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setTitle(isEdit ? "Editar Filme" : "Adicionar Novo Filme");
            Scene scene = new Scene(loader.load());
            scene.getStylesheets().add(Objects.requireNonNull(getClass().getResource("/view/style.css")).toExternalForm());
            stage.setScene(scene);

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
            if (responseJson == null) {
                showErrorAlert("Erro de comunicação ao buscar detalhes do filme.");
                return;
            }
            try {
                JSONObject response = new JSONObject(responseJson);
                if ("200".equals(response.getString("status"))) {
                    Movie detailedMovie = Movie.fromJson(response.getJSONObject("filme"));
                    JSONArray reviewsArray = response.getJSONArray("reviews");
                    showDetailsWindow(detailedMovie, reviewsArray);
                } else {
                    String message = response.optString("mensagem", "Erro ao buscar detalhes do filme.");
                    showErrorAlert(message);
                }
            } catch(Exception ex) {
                showErrorAlert("Erro ao processar detalhes do filme: " + ex.getMessage());
            }
        });

        loadDetailsTask.setOnFailed(event -> Platform.runLater(() -> showErrorAlert("Falha na tarefa de buscar detalhes.")));

        new Thread(loadDetailsTask).start();
    }

    @FXML
    private void handleSearchById() {
        String idText = searchIdField.getText().trim();
        if (!idText.matches("\\d+")) {
            showErrorAlert("Por favor, digite um ID numérico válido.");
            return;
        }
        fetchAndShowDetails(idText);
    }

    private void showDetailsWindow(Movie movie, JSONArray reviewsArray) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/MovieDetailView.fxml"));
            Stage stage = new Stage();
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setTitle("Detalhes do Filme");
            Scene scene = new Scene(loader.load());
            scene.getStylesheets().add(Objects.requireNonNull(getClass().getResource("/view/style.css")).toExternalForm());
            stage.setScene(scene);

            MovieDetailController controller = loader.getController();
            controller.setMovieDetails(movie, reviewsArray);

            stage.showAndWait();
        } catch (IOException e) {
            System.err.println("Erro ao abrir detalhes do filme: " + e.getMessage());
            showErrorAlert("Não foi possível abrir a tela de detalhes.");
        }
    }

    @FXML
    private void handleDeleteMovie() {
        Movie selectedMovie = moviesTable.getSelectionModel().getSelectedItem();
        if (selectedMovie == null) return;

        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
        confirmation.setTitle("Confirmar Exclusão");
        confirmation.setHeaderText("Tem certeza que deseja excluir o filme '" + selectedMovie.getTitulo() + "'?");
        confirmation.getDialogPane().getStylesheets().add(Objects.requireNonNull(getClass().getResource("/view/style.css")).toExternalForm());
        confirmation.getDialogPane().getStyleClass().add("root");

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
                if (responseJson == null) {
                    showErrorAlert("Erro de comunicação ao excluir filme.");
                    return;
                }
                try {
                    JSONObject response = new JSONObject(responseJson);
                    String status = response.getString("status");
                    if ("200".equals(status)) {
                        Platform.runLater(() -> {
                            showAlert(Alert.AlertType.INFORMATION, "Sucesso", "Filme excluído com sucesso.");
                            loadMovies();
                        });
                    } else {
                        String finalMessage = response.optString("mensagem", "Erro ao excluir filme.");
                        Platform.runLater(() -> showErrorAlert(finalMessage));
                    }
                } catch (Exception ex) {
                    Platform.runLater(() -> showErrorAlert("Erro ao processar resposta da exclusão: " + ex.getMessage()));
                }
            });

            deleteTask.setOnFailed(event -> Platform.runLater(() -> showErrorAlert("Falha na tarefa de excluir filme.")));

            new Thread(deleteTask).start();
        }
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
            alert.getDialogPane().getStylesheets().add(Objects.requireNonNull(getClass().getResource("/view/style.css")).toExternalForm());
            alert.getDialogPane().getStyleClass().add("root");
            alert.showAndWait();
        });
    }
}