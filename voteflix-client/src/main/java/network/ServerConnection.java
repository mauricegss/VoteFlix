package network;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class ServerConnection {

    private static ServerConnection instance;
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private final DateTimeFormatter dtf = DateTimeFormatter.ofPattern("HH:mm:ss");

    private ServerConnection() {
    }

    public static ServerConnection getInstance() {
        if (instance == null) {
            instance = new ServerConnection();
        }
        return instance;
    }

    public boolean connect(String host, int port) {
        try {
            if (isConnected()) {
                disconnect();
            }
            socket = new Socket();
            int timeout = 3000;
            socket.connect(new InetSocketAddress(host, port), timeout);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            return true;
        } catch (IOException e) {
            disconnect();
            return false;
        }
    }

    public void disconnect() {
        try {
            if (in != null) in.close();
            if (out != null) out.close();
            if (socket != null) socket.close();
        } catch (IOException e) {
            System.err.println("Erro ao desconectar: " + e.getMessage());
        } finally {
            socket = null;
            out = null;
            in = null;
        }
    }

    public boolean isConnected() {
        return socket != null && socket.isConnected() && !socket.isClosed();
    }

    private String sendRequestAndGetResponse(String jsonRequest) {
        if (!isConnected()) {
            return createErrorResponse("Nao conectado ao servidor.");
        }
        try {
            log("-> Para Servidor: " + jsonRequest);
            out.println(jsonRequest);
            String response = in.readLine();
            if (response == null) {
                disconnect();
                String errorResponse = createErrorResponse("A conexao com o servidor foi perdida.");
                log("<- De Servidor: " + errorResponse);
                return errorResponse;
            }
            log("<- De Servidor: " + response);
            return response;
        } catch (IOException e) {
            disconnect();
            String errorResponse = createErrorResponse("A conexao com o servidor foi perdida.");
            log("<- De Servidor: " + errorResponse);
            return errorResponse;
        }
    }

    public String login(String username, String password) {
        JSONObject request = new JSONObject();
        request.put("operacao", "LOGIN");
        request.put("usuario", username);
        request.put("senha", password);
        return sendRequestAndGetResponse(request.toString());
    }

    public String createUser(String username, String password) {
        JSONObject user = new JSONObject();
        user.put("nome", username);
        user.put("senha", password);
        JSONObject request = new JSONObject();
        request.put("operacao", "CRIAR_USUARIO");
        request.put("usuario", user);
        return sendRequestAndGetResponse(request.toString());
    }

    public void logout(String token) {
        JSONObject request = new JSONObject();
        request.put("operacao", "LOGOUT");
        request.put("token", token);
        sendRequestAndGetResponse(request.toString());
        disconnect();
    }

    public String updateOwnPassword(String newPassword, String token) {
        JSONObject user = new JSONObject();
        user.put("senha", newPassword);
        JSONObject request = new JSONObject();
        request.put("operacao", "EDITAR_PROPRIO_USUARIO");
        request.put("usuario", user);
        request.put("token", token);
        return sendRequestAndGetResponse(request.toString());
    }

    public void deleteOwnUser(String token) {
        JSONObject request = new JSONObject();
        request.put("operacao", "EXCLUIR_PROPRIO_USUARIO");
        request.put("token", token);
        sendRequestAndGetResponse(request.toString());
        disconnect();
    }

    public String listOwnUser(String token) {
        JSONObject request = new JSONObject();
        request.put("operacao", "LISTAR_PROPRIO_USUARIO");
        request.put("token", token);
        return sendRequestAndGetResponse(request.toString());
    }

    public String createMovie(String token, String titulo, String diretor, String ano, List<String> generos, String sinopse) {
        JSONObject movie = new JSONObject();
        movie.put("titulo", titulo);
        movie.put("diretor", diretor);
        movie.put("ano", ano);
        movie.put("genero", new JSONArray(generos));
        movie.put("sinopse", sinopse);

        JSONObject request = new JSONObject();
        request.put("operacao", "CRIAR_FILME");
        request.put("filme", movie);
        request.put("token", token);
        return sendRequestAndGetResponse(request.toString());
    }

    public String listMovies(String token) {
        JSONObject request = new JSONObject();
        request.put("operacao", "LISTAR_FILMES");
        request.put("token", token);
        return sendRequestAndGetResponse(request.toString());
    }

    public String getMovieById(String token, String movieId) {
        JSONObject request = new JSONObject();
        request.put("operacao", "BUSCAR_FILME_ID");
        request.put("id_filme", movieId);
        request.put("token", token);
        return sendRequestAndGetResponse(request.toString());
    }

    public String updateMovie(String token, String id, String titulo, String diretor, String ano, List<String> generos, String sinopse) {
        JSONObject movie = new JSONObject();
        movie.put("id", id);
        movie.put("titulo", titulo);
        movie.put("diretor", diretor);
        movie.put("ano", ano);
        movie.put("genero", new JSONArray(generos));
        movie.put("sinopse", sinopse);

        JSONObject request = new JSONObject();
        request.put("operacao", "EDITAR_FILME");
        request.put("filme", movie);
        request.put("token", token);
        return sendRequestAndGetResponse(request.toString());
    }

    public String deleteMovie(String token, String id) {
        JSONObject request = new JSONObject();
        request.put("operacao", "EXCLUIR_FILME");
        request.put("id", id);
        request.put("token", token);
        return sendRequestAndGetResponse(request.toString());
    }

    public String listUsers(String token) {
        JSONObject request = new JSONObject();
        request.put("operacao", "LISTAR_USUARIOS");
        request.put("token", token);
        return sendRequestAndGetResponse(request.toString());
    }

    public String adminEditUser(String token, String userId, String newPassword) {
        JSONObject user = new JSONObject();
        user.put("senha", newPassword);

        JSONObject request = new JSONObject();
        request.put("operacao", "ADMIN_EDITAR_USUARIO");
        request.put("id", userId);
        request.put("usuario", user);
        request.put("token", token);
        return sendRequestAndGetResponse(request.toString());
    }

    public String adminDeleteUser(String token, String userId) {
        JSONObject request = new JSONObject();
        request.put("operacao", "ADMIN_EXCLUIR_USUARIO");
        request.put("id", userId);
        request.put("token", token);
        return sendRequestAndGetResponse(request.toString());
    }

    private String createErrorResponse(String message) {
        JSONObject errorResponse = new JSONObject();
        errorResponse.put("status", "999");
        errorResponse.put("mensagem", message);
        return errorResponse.toString();
    }

    private void log(String message) {
        String timestamp = dtf.format(LocalDateTime.now());
        System.out.println("[" + timestamp + "] " + message);
    }
}