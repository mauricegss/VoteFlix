package network;

import controller.ServerController;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class Server implements Runnable {
    private final int port;
    private final ServerController controller;
    private ServerSocket serverSocket;
    private boolean running = true;
    private final Set<ClientHandler> activeClients = Collections.synchronizedSet(new HashSet<>());


    public Server(int port, ServerController controller) {
        this.port = port;
        this.controller = controller;
    }

    @Override
    public void run() {
        try {
            serverSocket = new ServerSocket(port);
            controller.log("Servidor iniciado na porta " + port, ServerController.LogType.INFO);
            while (running) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    controller.log("Novo cliente conectado: " + clientSocket.getInetAddress(), ServerController.LogType.CONNECTION);
                    ClientHandler clientHandler = new ClientHandler(clientSocket, controller, this);
                    activeClients.add(clientHandler);
                    new Thread(clientHandler).start();
                } catch (IOException e) {
                    if (running) {
                        controller.log("Erro ao aceitar conexão: " + e.getMessage(), ServerController.LogType.ERROR);
                    }
                }
            }
        } catch (IOException e) {
            controller.log("Não foi possível iniciar o servidor na porta " + port + ": " + e.getMessage(), ServerController.LogType.ERROR);
        } finally {
            if (running) {
                stop();
            }
        }
    }

    public void stop() {
        running = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
            synchronized (activeClients) {
                for (ClientHandler client : activeClients) {
                    client.closeConnection();
                }
                activeClients.clear();
            }
        } catch (IOException e) {
            controller.log("Erro ao parar o servidor: " + e.getMessage(), ServerController.LogType.ERROR);
        }
    }

    public void removeClient(ClientHandler clientHandler) {
        activeClients.remove(clientHandler);
        if (clientHandler.getUsername() != null && !clientHandler.getUsername().isEmpty()) {
            String userWithIp = String.format("%s (%s)", clientHandler.getUsername(), clientHandler.getIdentifier());
            controller.updateActiveUsers(userWithIp, false);
        }
    }

    public void addAuthenticatedUser(String userWithIp) {
        controller.updateActiveUsers(userWithIp, true);
    }
}