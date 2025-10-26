package controller;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import model.User;
import network.ServerConnection;
import org.json.JSONArray;
import org.json.JSONObject;
import session.SessionManager;

import java.util.Optional;

public class UserManagementController {

    @FXML private TableView<User> usersTable;
    @FXML private TableColumn<User, Integer> idColumn;
    @FXML private TableColumn<User, String> nameColumn;
    @FXML private TableColumn<User, Void> actionsColumn;

    private final ObservableList<User> userList = FXCollections.observableArrayList();

    @FXML
    private void initialize() {
        idColumn.setCellValueFactory(new PropertyValueFactory<>("id"));
        nameColumn.setCellValueFactory(new PropertyValueFactory<>("nome"));

        idColumn.getStyleClass().add("id-column");
        actionsColumn.getStyleClass().add("actions-column");

        setupActionsColumn();
        usersTable.setItems(userList);
    }

    public void loadUsers() {
        Task<String> loadUsersTask = new Task<>() {
            @Override
            protected String call() {
                String token = SessionManager.getInstance().getToken();
                return ServerConnection.getInstance().listUsers(token);
            }
        };

        loadUsersTask.setOnSucceeded(e -> {
            String responseJson = loadUsersTask.getValue();
            Platform.runLater(() -> {
                userList.clear();
                JSONObject response = new JSONObject(responseJson);
                if ("200".equals(response.getString("status"))) {
                    JSONArray users = response.getJSONArray("usuarios");
                    for (int i = 0; i < users.length(); i++) {
                        userList.add(User.fromJson(users.getJSONObject(i)));
                    }
                } else {
                    String message = response.optString("mensagem", "Não foi possível carregar os usuários.");
                    showAlert(Alert.AlertType.ERROR, "Erro", message);
                }
            });
        });

        new Thread(loadUsersTask).start();
    }

    private void setupActionsColumn() {
        actionsColumn.setCellFactory(param -> new TableCell<>() {
            private final Button updateBtn = new Button("Mudar Senha");
            private final Button deleteBtn = new Button("Excluir");
            private final HBox pane = new HBox(10, updateBtn, deleteBtn);

            {
                deleteBtn.getStyleClass().add("delete-button");

                pane.setAlignment(Pos.CENTER);
                updateBtn.setOnAction(event -> {
                    User user = getTableView().getItems().get(getIndex());
                    handleUpdatePassword(user);
                });
                deleteBtn.setOnAction(event -> {
                    User user = getTableView().getItems().get(getIndex());
                    handleDeleteUser(user);
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    setGraphic(pane);
                }
            }
        });
    }

    private void handleUpdatePassword(User user) {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Mudar Senha");
        dialog.setHeaderText("Alterando a senha para o usuário: " + user.getNome());
        dialog.setContentText("Nova Senha:");

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(newPassword -> {
            Task<String> updateTask = new Task<>() {
                @Override
                protected String call() {
                    String token = SessionManager.getInstance().getToken();
                    return ServerConnection.getInstance().adminEditUser(token, String.valueOf(user.getId()), newPassword);
                }
            };
            updateTask.setOnSucceeded(e -> {
                JSONObject response = new JSONObject(updateTask.getValue());
                String status = response.getString("status");
                if ("200".equals(status)) {
                    showAlert(Alert.AlertType.INFORMATION, "Sucesso", "Senha atualizada com sucesso.");
                } else {
                    String finalMessage = response.optString("mensagem", "Erro ao atualizar senha.");
                    showAlert(Alert.AlertType.ERROR, "Erro", finalMessage);
                }
            });
            new Thread(updateTask).start();
        });
    }

    private void handleDeleteUser(User user) {
        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
        confirmation.setTitle("Confirmar Exclusão");
        confirmation.setHeaderText("Tem certeza que deseja excluir o usuário '" + user.getNome() + "'?");
        confirmation.setContentText("Esta ação é irreversível.");

        Optional<ButtonType> result = confirmation.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            Task<String> deleteTask = new Task<>() {
                @Override
                protected String call() {
                    String token = SessionManager.getInstance().getToken();
                    return ServerConnection.getInstance().adminDeleteUser(token, String.valueOf(user.getId()));
                }
            };
            deleteTask.setOnSucceeded(e -> {
                JSONObject response = new JSONObject(deleteTask.getValue());
                String status = response.getString("status");
                if ("200".equals(status)) {
                    showAlert(Alert.AlertType.INFORMATION, "Sucesso", "Usuário excluído com sucesso.");
                    loadUsers();
                } else {
                    String finalMessage = response.optString("mensagem", "Erro ao excluir usuário.");
                    showAlert(Alert.AlertType.ERROR, "Erro", finalMessage);
                }
            });
            new Thread(deleteTask).start();
        }
    }

    private void showAlert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}