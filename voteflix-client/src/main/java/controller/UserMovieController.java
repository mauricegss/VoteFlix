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
                    if (movies.isEmpty()){
                        moviesTable.setPlaceholder(new javafx.scene.control.Label("Nenhum filme cadastrado no momento."));
                    }
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
                        handleViewDetails(movie);
                    });
                }
            }
        });
    }

    private void handleViewDetails(Movie movie) {
        if (movie == null) return;
        showDetailsWindow(movie);
    }

    @FXML
    private void handleSearchById() {
        String idText = searchIdField.getText().trim();

        Task<String> loadDetailsTask = new Task<>() {
            @Override
            protected String call() {
                String token = SessionManager.getInstance().getToken();
                return ServerConnection.getInstance().getMovieById(token, idText);
            }
        };

        loadDetailsTask.setOnSucceeded(e -> {
            String responseJson = loadDetailsTask.getValue();
            JSONObject response = new JSONObject(responseJson);
            String status = response.getString("status");

            if ("200".equals(status)) {
                Movie detailedMovie = Movie.fromJson(response.getJSONObject("filme"));
                showDetailsWindow(detailedMovie);
            } else {
                String message = response.optString("mensagem", "Erro ao buscar filme por ID.");
                showErrorAlert(message);
            }
        });

        new Thread(loadDetailsTask).start();
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
            System.err.println("Erro ao abrir a tela de detalhes: " + e.getMessage());
            showErrorAlert("Não foi possível abrir a tela de detalhes.");
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