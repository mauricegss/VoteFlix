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

public class UserMovieController {

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
        moviesTable.setPlaceholder(new Label("Carregando filmes..."));
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
    private void handleSearchById() {
        String idText = searchIdField.getText().trim();
        fetchAndShowDetails(idText);
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
                String status = response.getString("status");

                if ("200".equals(status)) {
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


    private void showDetailsWindow(Movie movie, JSONArray reviewsArray) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/MovieDetailView.fxml"));
            Stage stage = new Stage();
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setTitle("Detalhes do Filme");
            stage.setScene(new Scene(loader.load()));

            MovieDetailController controller = loader.getController();
            controller.setMovieDetails(movie, reviewsArray);

            stage.showAndWait();
        } catch (IOException e) {
            System.err.println("Erro ao abrir a tela de detalhes: " + e.getMessage());
            showErrorAlert("Não foi possível abrir a tela de detalhes.");
        }
    }

    private void showErrorAlert(String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Erro");
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }
}