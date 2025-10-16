package network;

import controller.ServerController;
import dao.UserDAO;
import model.User;
import org.json.JSONException;
import org.json.JSONObject;
import util.JwtUtil;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.SocketException;
import java.sql.SQLException;

public class ClientHandler implements Runnable {
    private final Socket clientSocket;
    private final ServerController controller;
    private final UserDAO userDAO;
    private final Server server;
    private PrintWriter out;
    private BufferedReader in;
    private String username;
    private boolean needsToClose = false;

    public ClientHandler(Socket socket, ServerController controller, Server server) {
        this.clientSocket = socket;
        this.controller = controller;
        this.server = server;
        this.userDAO = new UserDAO();
    }

    @Override
    public void run() {
        try {
            out = new PrintWriter(clientSocket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));

            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                String clientAddr = getIdentifier();
                controller.log("<- De " + clientAddr + ": " + inputLine, ServerController.LogType.REQUEST);
                String response = processRequest(inputLine);
                out.println(response);
                controller.log("-> Para " + clientAddr + ": " + response, ServerController.LogType.REQUEST);
                if (needsToClose) {
                    break;
                }
            }
        } catch (SocketException e) {
            controller.log("Conexão com " + getIdentifier() + " foi perdida.", ServerController.LogType.DISCONNECTION);
        } catch (IOException e) {
            controller.log("Erro no handler para " + getIdentifier() + ": " + e.getMessage(), ServerController.LogType.ERROR);
        } finally {
            closeConnection();
        }
    }

    private String processRequest(String jsonRequest) {
        try {
            JSONObject request = new JSONObject(jsonRequest);
            String operacao = request.getString("operacao");

            return switch (operacao) {
                case "LOGIN" -> handleLogin(request);
                case "CRIAR_USUARIO" -> handleCreateUser(request);
                case "LOGOUT" -> handleLogout(request);
                case "EDITAR_PROPRIO_USUARIO" -> handleUpdatePassword(request);
                case "EXCLUIR_PROPRIO_USUARIO" -> handleDeleteUser(request);
                case "LISTAR_PROPRIO_USUARIO" -> handleListOwnUser(request);
                default -> {
                    controller.log("Recebida operação desconhecida: '" + operacao + "' de " + getIdentifier(), ServerController.LogType.ERROR);
                    yield createErrorResponse(400, "Operação desconhecida.");
                }
            };
        } catch (JSONException e) {
            controller.log("Recebido JSON inválido de " + getIdentifier() + ": " + e.getMessage(), ServerController.LogType.ERROR);
            return createErrorResponse(400, "Requisição JSON inválida.");
        }
    }

    private String handleLogin(JSONObject request) {
        String user = request.getString("usuario");
        String pass = request.getString("senha");

        controller.log("Tentativa de login para o usuário '" + user + "'.", ServerController.LogType.INFO);
        try {
            User foundUser = userDAO.findByUsername(user);
            if (foundUser != null && foundUser.getSenha().equals(pass)) {
                this.username = user;
                String userWithIp = String.format("%s (%s)", user, clientSocket.getInetAddress().getHostAddress());
                server.addAuthenticatedUser(userWithIp);

                String token = JwtUtil.generateToken(user);
                JSONObject response = new JSONObject();
                response.put("status", "200");
                response.put("mensagem", "Login bem-sucedido.");
                response.put("token", token);
                controller.log("Usuário '" + user + "' autenticado com sucesso.", ServerController.LogType.CONNECTION);
                return response.toString();
            } else {
                controller.log("Falha na autenticação para o usuário '" + user + "'.", ServerController.LogType.ERROR);
                return createErrorResponse(401, "Usuário ou senha inválidos.");
            }
        } catch (SQLException e) {
            controller.log("Erro de banco de dados ao buscar usuário '" + user + "': " + e.getMessage(), ServerController.LogType.ERROR);
            return createErrorResponse(500, "Erro interno no servidor.");
        }
    }

    private String handleCreateUser(JSONObject request) {
        JSONObject userJson = request.getJSONObject("usuario");
        String username = userJson.getString("nome");
        String password = userJson.getString("senha");

        // --- CORREÇÃO 1: Uso do status 422 para erro de validação ---
        if (username.length() < 3 || username.length() > 20 || password.length() < 3 || password.length() > 20) {
            return createErrorResponse(422, "Usuário e senha devem ter entre 3 e 20 caracteres.");
        }

        User newUser = new User();
        newUser.setNome(username);
        newUser.setSenha(password);

        controller.log("Tentativa de criar usuário '" + newUser.getNome() + "'.", ServerController.LogType.INFO);
        if (userDAO.createUser(newUser)) {
            controller.log("Usuário '" + newUser.getNome() + "' criado com sucesso no banco de dados.", ServerController.LogType.INFO);
            JSONObject response = new JSONObject();
            response.put("status", "201");
            response.put("mensagem", "Usuário criado com sucesso.");
            return response.toString();
        } else {
            controller.log("Falha ao criar usuário '" + newUser.getNome() + "'. Usuário já existe.", ServerController.LogType.ERROR);
            return createErrorResponse(409, "Usuário já existe.");
        }
    }

    private String handleLogout(JSONObject request) {
        String token = request.getString("token");
        String userFromToken = JwtUtil.getUsernameFromToken(token);
        if (userFromToken != null) {
            controller.log("Usuário '" + userFromToken + "' fez logout.", ServerController.LogType.DISCONNECTION);
        }
        this.needsToClose = true;
        JSONObject response = new JSONObject();
        response.put("status", "200");
        response.put("mensagem", "Logout realizado com sucesso.");
        return response.toString();
    }

    private String handleUpdatePassword(JSONObject request) {
        String token = request.getString("token");
        String userFromToken = JwtUtil.getUsernameFromToken(token);
        if (userFromToken == null) {
            controller.log("Tentativa de alteração de senha com token inválido por " + getIdentifier(), ServerController.LogType.ERROR);
            return createErrorResponse(401, "Token inválido ou expirado.");
        }

        controller.log("Usuário '" + userFromToken + "' solicitou alteração de senha.", ServerController.LogType.INFO);
        String newPassword = request.getJSONObject("usuario").getString("senha");

        // --- CORREÇÃO 2: Adicionada validação de tamanho para a nova senha ---
        if (newPassword.length() < 3 || newPassword.length() > 20) {
            return createErrorResponse(422, "A nova senha deve ter entre 3 e 20 caracteres.");
        }

        try {
            if (userDAO.updatePassword(userFromToken, newPassword)) {
                controller.log("Senha do usuário '" + userFromToken + "' alterada com sucesso.", ServerController.LogType.INFO);
                return createSuccessResponse("Senha alterada com sucesso.");
            } else {
                controller.log("Falha ao alterar a senha do usuário '" + userFromToken + "'. Usuário não encontrado.", ServerController.LogType.ERROR);
                return createErrorResponse(404, "Usuário não encontrado.");
            }
        } catch (SQLException e) {
            controller.log("Erro de banco de dados ao atualizar senha para '" + userFromToken + "': " + e.getMessage(), ServerController.LogType.ERROR);
            return createErrorResponse(500, "Erro interno no servidor.");
        }
    }

    private String handleDeleteUser(JSONObject request) {
        String token = request.getString("token");
        String userFromToken = JwtUtil.getUsernameFromToken(token);
        if (userFromToken == null) {
            controller.log("Tentativa de exclusão de conta com token inválido por " + getIdentifier(), ServerController.LogType.ERROR);
            return createErrorResponse(401, "Token inválido ou expirado.");
        }

        controller.log("Usuário '" + userFromToken + "' solicitou a exclusão da própria conta.", ServerController.LogType.INFO);
        try {
            if (userDAO.deleteUser(userFromToken)) {
                this.needsToClose = true;
                controller.log("Usuário '" + userFromToken + "' foi excluído.", ServerController.LogType.DISCONNECTION);
                return createSuccessResponse("Usuário excluído com sucesso.");
            } else {
                controller.log("Falha ao excluir o usuário '" + userFromToken + "'. Usuário não encontrado.", ServerController.LogType.ERROR);
                return createErrorResponse(404, "Usuário não encontrado.");
            }
        } catch (SQLException e) {
            controller.log("Erro de banco de dados ao excluir usuário '" + userFromToken + "': " + e.getMessage(), ServerController.LogType.ERROR);
            return createErrorResponse(500, "Erro interno no servidor.");
        }
    }

    private String handleListOwnUser(JSONObject request) {
        String token = request.getString("token");
        String userFromToken = JwtUtil.getUsernameFromToken(token);

        if (userFromToken == null) {
            return createErrorResponse(401, "Token inválido ou expirado.");
        }

        JSONObject response = new JSONObject();
        response.put("status", "200");
        response.put("usuario", userFromToken);
        return response.toString();
    }

    private String createErrorResponse(int status, String message) {
        JSONObject errorResponse = new JSONObject();
        errorResponse.put("status", String.valueOf(status));
        errorResponse.put("mensagem", message);
        return errorResponse.toString();
    }

    private String createSuccessResponse(String message) {
        JSONObject response = new JSONObject();
        response.put("status", "200");
        response.put("mensagem", message);
        return response.toString();
    }

    public void closeConnection() {
        try {
            if (in != null) in.close();
            if (out != null) out.close();
            if (clientSocket != null && !clientSocket.isClosed()) clientSocket.close();
        } catch (IOException e) {
            controller.log("Erro ao fechar conexão com cliente: " + e.getMessage(), ServerController.LogType.ERROR);
        } finally {
            server.removeClient(this);
            controller.log("Conexão com " + getIdentifier() + " fechada.", ServerController.LogType.DISCONNECTION);
        }
    }

    public String getIdentifier() {
        return username != null ? username : clientSocket.getInetAddress().toString();
    }

    public String getUsername() {
        return username;
    }

    public String getClientIpAddress() {
        return clientSocket.getInetAddress().getHostAddress();
    }
}