package network;

import controller.ServerController;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

public class Server implements Runnable {
    private final int port;
    private final ServerController controller;
    private ServerSocketChannel serverSocketChannel;
    private Selector selector;
    private boolean running = true;
    private final Set<ClientHandler> activeClients = Collections.synchronizedSet(new HashSet<>());


    public Server(int port, ServerController controller) {
        this.port = port;
        this.controller = controller;
    }

    @Override
    public void run() {
        try {
            selector = Selector.open();
            serverSocketChannel = ServerSocketChannel.open();
            serverSocketChannel.bind(new InetSocketAddress(port));
            serverSocketChannel.configureBlocking(false);
            serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);

            controller.log("Servidor não-bloqueante iniciado na porta " + port, ServerController.LogType.INFO);

            while (running) {
                try {
                    selector.select(); // Bloqueia até que um canal esteja pronto
                    if (!running) break;

                    Iterator<SelectionKey> keys = selector.selectedKeys().iterator();
                    while (keys.hasNext()) {
                        SelectionKey key = keys.next();
                        keys.remove();

                        if (!key.isValid()) continue;

                        if (key.isAcceptable()) {
                            handleAccept();
                        } else if (key.isReadable()) {
                            handleRead(key);
                        } else if (key.isWritable()) {
                            handleWrite(key);
                        }
                    }
                } catch (IOException e) {
                    controller.log("Erro no loop do selector: " + e.getMessage(), ServerController.LogType.ERROR);
                }
            }
        } catch (IOException e) {
            controller.log("Não foi possível iniciar o servidor na porta " + port + ": " + e.getMessage(), ServerController.LogType.ERROR);
        } finally {
            stop();
        }
    }

    private void handleAccept() {
        try {
            SocketChannel socketChannel = serverSocketChannel.accept();
            if (socketChannel != null) {
                socketChannel.configureBlocking(false);
                ClientHandler handler = new ClientHandler(socketChannel, controller, this);
                socketChannel.register(selector, SelectionKey.OP_READ, handler);
                activeClients.add(handler);
                controller.log("Novo cliente conectado: " + socketChannel.getRemoteAddress(), ServerController.LogType.CONNECTION);
            }
        } catch (IOException e) {
            controller.log("Erro ao aceitar nova conexão: " + e.getMessage(), ServerController.LogType.ERROR);
        }
    }

    private void handleRead(SelectionKey key) {
        ClientHandler handler = (ClientHandler) key.attachment();
        try {
            handler.handleRead();
        } catch (IOException e) {
            controller.log("Cliente desconectado (read): " + handler.getIdentifier(), ServerController.LogType.DISCONNECTION);
            disconnectClient(key);
        }
    }

    private void handleWrite(SelectionKey key) {
        ClientHandler handler = (ClientHandler) key.attachment();
        try {
            handler.handleWrite(key);
        } catch (IOException e) {
            controller.log("Cliente desconectado (write): " + handler.getIdentifier(), ServerController.LogType.DISCONNECTION);
            disconnectClient(key);
        }
    }

    public void registerForWrites(ClientHandler handler) {
        SocketChannel channel = handler.getChannel();
        try {
            if (channel.isOpen()) {
                SelectionKey key = channel.keyFor(selector);
                if (key != null && key.isValid()) {
                    key.interestOps(SelectionKey.OP_READ | SelectionKey.OP_WRITE);
                }
            }
        } catch (Exception e) {
            controller.log("Erro ao registrar para escrita: " + e.getMessage(), ServerController.LogType.ERROR);
            disconnectClient(channel.keyFor(selector));
        }
    }

    private void disconnectClient(SelectionKey key) {
        if (key == null) return;

        ClientHandler handler = (ClientHandler) key.attachment();
        if (handler != null) {
            handler.closeConnection(); // Chama o cleanup lógico (remover da lista, etc)
        }

        try {
            key.channel().close();
        } catch (IOException e) {
            // Ignora, já estamos fechando
        }
        key.cancel();
    }

    public void stop() {
        running = false;
        try {
            if (selector != null && selector.isOpen()) {
                selector.wakeup(); // Força o selector.select() a sair
                selector.close();
            }
            if (serverSocketChannel != null && serverSocketChannel.isOpen()) {
                serverSocketChannel.close();
            }
            synchronized (activeClients) {
                for (ClientHandler client : activeClients) {
                    client.getChannel().close();
                }
                activeClients.clear();
            }
            controller.log("Servidor sendo desligado...", ServerController.LogType.INFO);
        } catch (IOException e) {
            controller.log("Erro ao parar o servidor: " + e.getMessage(), ServerController.LogType.ERROR);
        }
    }

    public void removeClient(ClientHandler clientHandler) {
        activeClients.remove(clientHandler);
        if (clientHandler.getUsername() != null && !clientHandler.getUsername().isEmpty()) {
            String userWithIp = String.format("%s (%s)", clientHandler.getUsername(), clientHandler.getClientIpAddress());
            controller.updateActiveUsers(userWithIp, false);
        }
    }

    public void addAuthenticatedUser(String userWithIp) {
        controller.updateActiveUsers(userWithIp, true);
    }
}