package network;

import controller.ServerController;
import dao.MovieDAO;
import dao.UserDAO;
import model.Movie;
import model.User;
import org.json.JSONArray;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

public class ClientHandler implements Runnable {
    private final Socket clientSocket;
    private final ServerController controller;
    private final UserDAO userDAO;
    private final MovieDAO movieDAO;
    private final Server server;
    private PrintWriter out;
    private BufferedReader in;
    private String username;
    private boolean needsToClose = false;

    private static final Map<String, String> RESPONSE_MESSAGES = new HashMap<>();
    static {
        RESPONSE_MESSAGES.put("200", "Sucesso: operação realizada com sucesso");
        RESPONSE_MESSAGES.put("201", "Sucesso: Recurso cadastrado");
        RESPONSE_MESSAGES.put("400", "Erro: Operação não encontrada ou inválida");
        RESPONSE_MESSAGES.put("401", "Erro: Token inválido");
        RESPONSE_MESSAGES.put("403", "Erro: sem permissão");
        RESPONSE_MESSAGES.put("404", "Erro: Recurso inexistente");
        RESPONSE_MESSAGES.put("409", "Erro: Recurso ja existe");
        RESPONSE_MESSAGES.put("422", "Erro: Chaves faltantes ou invalidas");
        RESPONSE_MESSAGES.put("500", "Erro: Falha interna do servidor");
    }

    public ClientHandler(Socket socket, ServerController controller, Server server) {
        this.clientSocket = socket;
        this.controller = controller;
        this.server = server;
        this.userDAO = new UserDAO();
        this.movieDAO = new MovieDAO();
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

            switch (operacao) {
                case "LOGIN":
                    return handleLogin(request);
                case "CRIAR_USUARIO":
                    return handleCreateUser(request);
            }

            String token = request.optString("token");
            if (token.isEmpty()) {
                return createErrorResponse(401);
            }
            String userFromToken = JwtUtil.getUsernameFromToken(token);
            if (userFromToken == null) {
                return createErrorResponse(401);
            }

            switch (operacao) {
                case "LOGOUT":
                    return handleLogout();
                case "EDITAR_PROPRIO_USUARIO":
                    return handleUpdateOwnPassword(request, userFromToken);
                case "EXCLUIR_PROPRIO_USUARIO":
                    return handleDeleteOwnUser(userFromToken);
                case "LISTAR_PROPRIO_USUARIO":
                    return handleListOwnUser(userFromToken);
                case "LISTAR_FILMES":
                    return handleListMovies();
                case "BUSCAR_FILME_ID":
                    return handleGetMovieById(request);
            }

            if (!"admin".equals(userFromToken)) {
                return createErrorResponse(403);
            }

            return switch (operacao) {
                case "CRIAR_FILME" -> handleCreateMovie(request);
                case "EDITAR_FILME" -> handleUpdateMovie(request);
                case "EXCLUIR_FILME" -> handleDeleteMovie(request);
                case "LISTAR_USUARIOS" -> handleListUsers();
                case "ADMIN_EDITAR_USUARIO" -> handleAdminEditUser(request);
                case "ADMIN_EXCLUIR_USUARIO" -> handleAdminDeleteUser(request);
                default -> createErrorResponse(400);
            };

        } catch (JSONException e) {
            return createErrorResponse(400);
        }
    }

    private String handleLogin(JSONObject request) {
        try {
            String user = request.getString("usuario");
            String pass = request.getString("senha");

            User foundUser = userDAO.findByUsername(user);
            if (foundUser != null && foundUser.getSenha().equals(pass)) {
                this.username = user;
                String userWithIp = String.format("%s (%s)", user, clientSocket.getInetAddress().getHostAddress());
                server.addAuthenticatedUser(userWithIp);

                String role = "admin".equals(user) ? "admin" : "user";
                String token = JwtUtil.generateToken(user, role, foundUser.getId());

                JSONObject response = createSuccessResponse("200");
                response.put("token", token);
                return response.toString();
            } else {
                return createErrorResponse(401);
            }
        } catch (SQLException e) {
            return createErrorResponse(500);
        } catch (JSONException e) {
            return createErrorResponse(400);
        }
    }

    private String handleCreateUser(JSONObject request) {
        try {
            JSONObject userJson = request.getJSONObject("usuario");
            String username = userJson.getString("nome");
            String password = userJson.getString("senha");

            if (isInvalidUserFields(username, password)) {
                return createErrorResponse(422);
            }
            if ("admin".equalsIgnoreCase(username)) {
                return createErrorResponse(403);
            }

            User newUser = new User();
            newUser.setNome(username);
            newUser.setSenha(password);
            userDAO.createUser(newUser);
            return createSuccessResponse("201").toString();

        } catch (SQLException e) {
            return createErrorResponse(409);
        } catch (JSONException e) {
            return createErrorResponse(400);
        }
    }

    private String handleUpdateOwnPassword(JSONObject request, String userFromToken) {
        try {
            String newPassword = request.getJSONObject("usuario").getString("senha");
            if (isInvalidUserFields(userFromToken, newPassword)) {
                return createErrorResponse(422);
            }
            if (userDAO.updatePassword(userFromToken, newPassword)) {
                return createSuccessResponse("200").toString();
            } else {
                return createErrorResponse(404);
            }
        } catch (SQLException e) {
            return createErrorResponse(500);
        } catch (JSONException e) {
            return createErrorResponse(400);
        }
    }

    private String handleDeleteOwnUser(String userFromToken) {
        try {
            if ("admin".equalsIgnoreCase(userFromToken)) {
                return createErrorResponse(403);
            }

            if (userDAO.deleteUser(userFromToken)) {
                this.needsToClose = true;
                return createSuccessResponse("200").toString();
            } else {
                return createErrorResponse(404);
            }
        } catch (SQLException e) {
            return createErrorResponse(500);
        }
    }

    private String handleListOwnUser(String userFromToken) {
        JSONObject response = createSuccessResponse("200");
        response.put("usuario", userFromToken);
        return response.toString();
    }

    private String handleCreateMovie(JSONObject request) {
        try {
            JSONObject movieJson = request.getJSONObject("filme");
            if (isInvalidMovieFields(movieJson)) {
                return createErrorResponse(422);
            }
            Movie movie = movieFromJson(movieJson);
            movieDAO.createMovie(movie);
            return createSuccessResponse("201").toString();
        } catch (SQLException e) {
            return createErrorResponse(409);
        } catch (JSONException e) {
            return createErrorResponse(400);
        }
    }

    private String handleUpdateMovie(JSONObject request) {
        try {
            JSONObject movieJson = request.getJSONObject("filme");
            if (isInvalidMovieFields(movieJson) || !movieJson.has("id")) {
                return createErrorResponse(422);
            }
            Movie movie = movieFromJson(movieJson);
            if (movieDAO.updateMovie(movie)) {
                return createSuccessResponse("200").toString();
            } else {
                return createErrorResponse(404);
            }
        } catch (SQLException e) {
            return createErrorResponse(409);
        } catch (JSONException | NumberFormatException e) {
            return createErrorResponse(400);
        }
    }

    private String handleDeleteMovie(JSONObject request) {
        try {
            int id = Integer.parseInt(request.getString("id"));
            if (movieDAO.deleteMovie(id)) {
                return createSuccessResponse("200").toString();
            } else {
                return createErrorResponse(404);
            }
        } catch (SQLException e) {
            return createErrorResponse(500);
        } catch (JSONException | NumberFormatException e) {
            return createErrorResponse(400);
        }
    }

    private String handleGetMovieById(JSONObject request) {
        try {
            int id = Integer.parseInt(request.getString("id_filme"));
            Movie movie = movieDAO.findMovieById(id);
            if (movie == null) {
                return createErrorResponse(404);
            }
            JSONObject response = createSuccessResponse("200");
            response.put("filme", jsonFromMovie(movie));
            response.put("reviews", new JSONArray());
            return response.toString();
        } catch (NumberFormatException | JSONException e) {
            return createErrorResponse(400);
        } catch (SQLException e) {
            return createErrorResponse(500);
        }
    }

    private String handleListMovies() {
        try {
            List<Movie> movies = movieDAO.listMovies();
            JSONArray moviesJson = new JSONArray();
            for (Movie movie : movies) {
                moviesJson.put(jsonFromMovie(movie));
            }
            JSONObject response = createSuccessResponse("200");
            response.put("filmes", moviesJson);
            return response.toString();
        } catch (SQLException e) {
            return createErrorResponse(500);
        }
    }

    private String handleListUsers() {
        try {
            List<User> users = userDAO.listAllUsers();
            JSONArray usersJson = new JSONArray();
            for (User user : users) {
                JSONObject userJson = new JSONObject();
                userJson.put("id", String.valueOf(user.getId()));
                userJson.put("nome", user.getNome());
                usersJson.put(userJson);
            }
            JSONObject response = createSuccessResponse("200");
            response.put("usuarios", usersJson);
            return response.toString();
        } catch (SQLException e) {
            return createErrorResponse(500);
        }
    }

    private String handleAdminEditUser(JSONObject request) {
        try {
            int userId = Integer.parseInt(request.getString("id"));
            String newPassword = request.getJSONObject("usuario").getString("senha");
            if (isInvalidUserFields("tempuser", newPassword)) {
                return createErrorResponse(422);
            }
            if (userDAO.updateUserPasswordById(userId, newPassword)) {
                return createSuccessResponse("200").toString();
            } else {
                return createErrorResponse(404);
            }
        } catch (SQLException e) {
            return createErrorResponse(500);
        } catch (JSONException | NumberFormatException e) {
            return createErrorResponse(400);
        }
    }

    private String handleAdminDeleteUser(JSONObject request) {
        try {
            int userId = Integer.parseInt(request.getString("id"));

            if (userId == 1) {
                return createErrorResponse(403);
            }

            if (userDAO.deleteUserById(userId)) {
                return createSuccessResponse("200").toString();
            } else {
                return createErrorResponse(404);
            }
        } catch (SQLException e) {
            return createErrorResponse(500);
        } catch (JSONException | NumberFormatException e) {
            return createErrorResponse(400);
        }
    }

    private String handleLogout() {
        this.needsToClose = true;
        return createSuccessResponse("200").toString();
    }

    private boolean isInvalidUserFields(String username, String password) {
        return username.length() < 3 || username.length() > 20 || !username.matches("[a-zA-Z0-9]+") ||
                password.length() < 3 || password.length() > 20 || !password.matches("[a-zA-Z0-9]+");
    }

    private boolean isInvalidMovieFields(JSONObject movieJson) {
        return movieJson.optString("titulo").length() > 30 || movieJson.optString("titulo").isEmpty() ||
                !movieJson.optString("ano").matches("\\d{4}") ||
                movieJson.optString("sinopse").length() > 250 ||
                movieJson.optString("diretor").isEmpty() ||
                movieJson.optJSONArray("genero") == null || movieJson.optJSONArray("genero").isEmpty();
    }

    private Movie movieFromJson(JSONObject json) {
        Movie movie = new Movie();
        if (json.has("id")) movie.setId(Integer.parseInt(json.getString("id")));
        movie.setTitulo(json.getString("titulo"));
        movie.setDiretor(json.getString("diretor"));
        movie.setAno(json.getString("ano"));
        movie.setSinopse(json.getString("sinopse"));

        JSONArray generosJson = json.getJSONArray("genero");
        List<String> generos = new ArrayList<>();
        for (int i = 0; i < generosJson.length(); i++) {
            generos.add(generosJson.getString(i));
        }
        movie.setGeneros(generos);
        return movie;
    }

    private JSONObject jsonFromMovie(Movie movie) {
        JSONObject movieJson = new JSONObject();
        movieJson.put("id", String.valueOf(movie.getId()));
        movieJson.put("titulo", movie.getTitulo());
        movieJson.put("diretor", movie.getDiretor());
        movieJson.put("ano", movie.getAno());
        movieJson.put("genero", new JSONArray(movie.getGeneros()));
        movieJson.put("sinopse", movie.getSinopse());
        movieJson.put("nota", String.format("%.1f", movie.getNota()));
        movieJson.put("qtd_avaliacoes", String.valueOf(movie.getQtdAvaliacoes()));
        return movieJson;
    }

    private String createErrorResponse(int status) {
        String statusCode = String.valueOf(status);
        JSONObject errorResponse = new JSONObject();
        errorResponse.put("status", statusCode);
        errorResponse.put("mensagem", RESPONSE_MESSAGES.getOrDefault(statusCode, "Erro: Erro desconhecido"));
        return errorResponse.toString();
    }

    private JSONObject createSuccessResponse(String status) {
        JSONObject response = new JSONObject();
        response.put("status", status);
        response.put("mensagem", RESPONSE_MESSAGES.getOrDefault(status, "Sucesso: operação realizada com sucesso"));
        return response;
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
    public String getUsername() { return username; }
    public String getClientIpAddress() { return clientSocket.getInetAddress().getHostAddress(); }
}