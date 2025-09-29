package controller;

import database.DatabaseConnection;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import network.Server;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class ServerController {

    public enum LogType {
        INFO(Color.BLACK),
        CONNECTION(Color.GREEN),
        DISCONNECTION(Color.ORANGE),
        ERROR(Color.RED),
        REQUEST(Color.BLUE);

        private final Color color;
        LogType(Color color) { this.color = color; }
        public Color getColor() { return color; }
    }

    @FXML private TextField ipField;
    @FXML private TextField portField;
    @FXML private ListView<String> activeUsersList;
    @FXML private ScrollPane logScrollPane;
    @FXML private TextFlow logFlow;

    private Server server;
    private Thread serverThread;
    private final ObservableList<String> activeUsers = FXCollections.observableArrayList();
    private final Set<String> userSet = Collections.synchronizedSet(new HashSet<>());
    private final DateTimeFormatter dtf = DateTimeFormatter.ofPattern("HH:mm:ss");

    @FXML
    private void initialize() {
        activeUsersList.setItems(activeUsers);
        findAndSetIpAddress();
        logFlow.heightProperty().addListener((obs, oldVal, newVal) -> logScrollPane.setVvalue(1.0));
        startServer();
    }

    @FXML
    private void handleClearLog() {
        logFlow.getChildren().clear();
    }

    private void findAndSetIpAddress() {
        try {
            ipField.setText(InetAddress.getLocalHost().getHostAddress());
        } catch (UnknownHostException e) {
            ipField.setText("IP não encontrado");
            log("Erro ao tentar obter o IP local: " + e.getMessage(), LogType.ERROR);
        }
    }

    private void startServer() {
        try {
            int port = Integer.parseInt(portField.getText());
            server = new Server(port, this);
            serverThread = new Thread(server);
            serverThread.start();
        } catch (NumberFormatException e) {
            log("A porta '" + portField.getText() + "' é inválida.", LogType.ERROR);
        }
    }

    private void stopServer() {
        if (server != null) {
            server.stop();
            if (serverThread != null && serverThread.isAlive()) {
                serverThread.interrupt();
            }
        }
        log("Servidor sendo desligado...", LogType.INFO);
    }

    public void log(String message, LogType type) {
        Platform.runLater(() -> {
            String timestamp = dtf.format(LocalDateTime.now());
            Text time = new Text("[" + timestamp + "] ");
            time.setFill(Color.GRAY);
            Text msg = new Text(message + "\n");
            msg.setFill(type.getColor());
            logFlow.getChildren().addAll(time, msg);
        });
    }

    public void updateActiveUsers(String username, boolean isActive) {
        Platform.runLater(() -> {
            if (isActive) {
                if (userSet.add(username)) {
                    activeUsers.add(username);
                }
            } else {
                if (userSet.remove(username)) {
                    activeUsers.remove(username);
                }
            }
        });
    }

    public void shutdown() {
        stopServer();
        DatabaseConnection.closeConnection();
        Platform.exit();
        System.exit(0);
    }
}