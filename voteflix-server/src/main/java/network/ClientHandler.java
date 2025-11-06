package network;

import controller.ServerController;
import dao.MovieDAO;
import dao.UserDAO;
import dao.ReviewDAO;
import model.Movie;
import model.User;
import model.Review;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import util.JwtUtil;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays; // Adicionado
import java.util.HashMap;
import java.util.HashSet; // Adicionado
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set; // Adicionado

public class ClientHandler {
    private final SocketChannel channel;
    private final ServerController controller;
    private final UserDAO userDAO;
    private final MovieDAO movieDAO;
    private final ReviewDAO reviewDAO;
    private final Server server;
    private String username;
    private boolean needsToClose = false;

    private final ByteBuffer readBuffer = ByteBuffer.allocate(8192);
    private final StringBuilder lineBuilder = new StringBuilder();
    private final Queue<ByteBuffer> writeQueue = new LinkedList<>();
    private final Charset charset = StandardCharsets.UTF_8;

    private static final Map<String, String> RESPONSE_MESSAGES = new HashMap<>();
    static {
        RESPONSE_MESSAGES.put("200", "Sucesso: Operação realizada com sucesso");
        RESPONSE_MESSAGES.put("201", "Sucesso: Recurso cadastrado");
        RESPONSE_MESSAGES.put("400", "Erro: Operação não encontrada ou inválida");
        RESPONSE_MESSAGES.put("401", "Erro: Autenticação falhou (credenciais ou token inválidos)");
        RESPONSE_MESSAGES.put("403", "Erro: sem permissão");
        RESPONSE_MESSAGES.put("404", "Erro: Recurso inexistente");
        RESPONSE_MESSAGES.put("405", "Erro: Campos inválidos, verifique o tipo e quantidade de caracteres");
        RESPONSE_MESSAGES.put("409", "Erro: Recurso ja existe");
        RESPONSE_MESSAGES.put("422", "Erro: Chaves faltantes ou invalidas");
        RESPONSE_MESSAGES.put("500", "Erro: Falha interna do servidor");
    }

    // Adicionada lista de gêneros permitidos
    private static final Set<String> PREDEFINED_GENRES = new HashSet<>(Arrays.asList(
            "Ação", "Aventura", "Comédia", "Drama", "Fantasia", "Ficção Científica",
            "Terror", "Romance", "Documentário", "Musical", "Animação"
    ));


    public ClientHandler(SocketChannel channel, ServerController controller, Server server) {
        this.channel = channel;
        this.controller = controller;
        this.server = server;
        this.userDAO = new UserDAO();
        this.movieDAO = new MovieDAO();
        this.reviewDAO = new ReviewDAO();
    }

    public void handleRead() throws IOException {
        readBuffer.clear();
        int bytesRead = channel.read(readBuffer);

        if (bytesRead == -1) {
            throw new IOException("Cliente fechou a conexão.");
        }

        if (bytesRead > 0) {
            readBuffer.flip();
            String chunk = charset.decode(readBuffer).toString();
            lineBuilder.append(chunk);

            processBufferLines();
        }
    }

    private void processBufferLines() {
        while (true) {
            int newlineIndex = lineBuilder.indexOf("\n");
            if (newlineIndex == -1) {
                break;
            }

            String line = lineBuilder.substring(0, newlineIndex);
            lineBuilder.delete(0, newlineIndex + 1);

            if (line.isEmpty()) continue;

            String clientAddr = getIdentifier();
            controller.log("<- De " + clientAddr + ": " + line, ServerController.LogType.REQUEST);
            String response = processRequest(line);

            controller.log("-> Para " + clientAddr + ": " + response, ServerController.LogType.REQUEST);
            queueResponse(response);
        }
    }

    private void queueResponse(String response) {
        String line = response + "\n";
        ByteBuffer buffer = charset.encode(line);
        synchronized (writeQueue) {
            writeQueue.add(buffer);
        }
        server.registerForWrites(this);
    }

    public boolean handleWrite(SelectionKey key) throws IOException {
        synchronized (writeQueue) {
            while (!writeQueue.isEmpty()) {
                ByteBuffer buffer = writeQueue.peek();
                channel.write(buffer);

                if (buffer.hasRemaining()) {
                    return true;
                }
                writeQueue.poll();
            }
        }

        key.interestOps(SelectionKey.OP_READ);

        return !needsToClose || !writeQueue.isEmpty();
    }

    private String processRequest(String jsonRequest) {
        try {
            JSONObject request = new JSONObject(jsonRequest);
            String operacao = request.getString("operacao");

            switch (operacao) {
                case "LOGIN":
                    if (!request.has("usuario") || !request.has("senha")) {
                        return createErrorResponse(422);
                    }
                    return handleLogin(request);
                case "CRIAR_USUARIO":
                    if (!request.has("usuario")) {
                        return createErrorResponse(422);
                    }
                    return handleCreateUser(request);
            }

            if (!request.has("token")) {
                return createErrorResponse(422);
            }

            String token = request.optString("token");
            if (token.isEmpty()) {
                return createErrorResponse(401);
            }

            Integer userIdFromToken = JwtUtil.getUserIdFromToken(token);
            String userFromToken = JwtUtil.getUsernameFromToken(token);
            String roleFromToken = JwtUtil.getRoleFromToken(token);

            if (userFromToken == null || userIdFromToken == null || roleFromToken == null) {
                return createErrorResponse(401);
            }

            try {
                User currentUser = userDAO.findById(userIdFromToken);
                if (currentUser == null) {
                    needsToClose = true;
                    return createErrorResponse(404);
                }
            } catch (SQLException e) {
                controller.log("Erro ao verificar existência do usuário: " + e.getMessage(), ServerController.LogType.ERROR);
                return createErrorResponse(500);
            }


            switch (operacao) {
                case "LOGOUT":
                    return handleLogout();
                case "EDITAR_PROPRIO_USUARIO":
                    if (!request.has("usuario")) {
                        return createErrorResponse(422);
                    }
                    return handleUpdateOwnPassword(request, userFromToken);
                case "EXCLUIR_PROPRIO_USUARIO":
                    return handleDeleteOwnUser(userFromToken);
                case "LISTAR_PROPRIO_USUARIO":
                    return handleListOwnUser(userFromToken);
                case "LISTAR_FILMES":
                    return handleListMovies();
                case "BUSCAR_FILME_ID":
                    return handleGetMovieById(request);
                case "CRIAR_REVIEW":
                    return handleCreateReview(request, userIdFromToken, userFromToken, roleFromToken);
                case "LISTAR_REVIEWS_USUARIO":
                    return handleListUserReviews(userIdFromToken);
                case "EDITAR_REVIEW":
                    return handleEditReview(request, userIdFromToken, roleFromToken);
                case "EXCLUIR_REVIEW":
                    return handleDeleteReview(request, userIdFromToken, roleFromToken);
            }

            if (!"admin".equals(roleFromToken)) {
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
                String userWithIp = String.format("%s (%s)", user, getClientIpAddress());
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
            controller.log("Erro SQL no login: " + e.getMessage(), ServerController.LogType.ERROR);
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
                return createErrorResponse(405);
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
            if (e.getMessage().contains("UNIQUE constraint failed")) {
                return createErrorResponse(409);
            }
            controller.log("Erro SQL ao criar usuário: " + e.getMessage(), ServerController.LogType.ERROR);
            return createErrorResponse(500);
        } catch (JSONException e) {
            return createErrorResponse(422);
        }
    }

    private String handleUpdateOwnPassword(JSONObject request, String userFromToken) {
        try {
            if ("admin".equalsIgnoreCase(userFromToken)) {
                return createErrorResponse(403);
            }

            String newPassword = request.getJSONObject("usuario").getString("senha");

            if (isInvalidUserFields(userFromToken, newPassword)) {
                return createErrorResponse(405);
            }

            if (userDAO.updatePassword(userFromToken, newPassword)) {
                return createSuccessResponse("200").toString();
            } else {
                return createErrorResponse(404);
            }
        } catch (SQLException e) {
            controller.log("Erro SQL ao atualizar própria senha: " + e.getMessage(), ServerController.LogType.ERROR);
            return createErrorResponse(500);
        } catch (JSONException e) {
            return createErrorResponse(422);
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
            controller.log("Erro SQL ao excluir próprio usuário: " + e.getMessage(), ServerController.LogType.ERROR);
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
            List<String> receivedGenres = jsonArrayToList(movieJson.optJSONArray("genero")); // Modificado

            if (isInvalidMovieFields(movieJson, receivedGenres)) { // Modificado
                return createErrorResponse(422);
            }
            Movie movie = movieFromJson(movieJson, receivedGenres); // Modificado
            movieDAO.createMovie(movie);
            return createSuccessResponse("201").toString();
        } catch (SQLException e) {
            if (e.getMessage().contains("UNIQUE constraint failed")) {
                return createErrorResponse(409);
            }
            controller.log("Erro SQL ao criar filme: " + e.getMessage(), ServerController.LogType.ERROR);
            return createErrorResponse(500);
        } catch (JSONException e) {
            return createErrorResponse(400);
        }
    }

    private String handleUpdateMovie(JSONObject request) {
        try {
            JSONObject movieJson = request.getJSONObject("filme");
            List<String> receivedGenres = jsonArrayToList(movieJson.optJSONArray("genero")); // Modificado

            if (!movieJson.has("id") || isInvalidMovieFields(movieJson, receivedGenres)) { // Modificado
                return createErrorResponse(422);
            }
            Movie movie = movieFromJson(movieJson, receivedGenres); // Modificado
            if (movieDAO.updateMovie(movie)) {
                return createSuccessResponse("200").toString();
            } else {
                return createErrorResponse(404);
            }
        } catch (SQLException e) {
            if (e.getMessage().contains("UNIQUE constraint failed")) {
                return createErrorResponse(409);
            }
            controller.log("Erro SQL ao editar filme: " + e.getMessage(), ServerController.LogType.ERROR);
            return createErrorResponse(500);
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
            controller.log("Erro SQL ao excluir filme: " + e.getMessage(), ServerController.LogType.ERROR);
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
            List<Review> reviews = reviewDAO.findReviewsByMovieId(id);
            JSONArray reviewsJson = new JSONArray();
            for (Review review : reviews) {
                reviewsJson.put(jsonFromReview(review));
            }

            JSONObject response = createSuccessResponse("200");
            response.put("filme", jsonFromMovie(movie));
            response.put("reviews", reviewsJson);
            return response.toString();
        } catch (NumberFormatException | JSONException e) {
            return createErrorResponse(400);
        } catch (SQLException e) {
            controller.log("Erro SQL ao buscar filme por ID: " + e.getMessage(), ServerController.LogType.ERROR);
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
            controller.log("Erro SQL ao listar filmes: " + e.getMessage(), ServerController.LogType.ERROR);
            return createErrorResponse(500);
        }
    }

    private String handleCreateReview(JSONObject request, int userId, String username, String role) {
        if (!"user".equals(role)) {
            return createErrorResponse(403);
        }
        try {
            JSONObject reviewJson = request.getJSONObject("review");
            Review newReview = reviewFromJson(reviewJson);

            if (newReview.getNota() < 1 || newReview.getNota() > 5 || (newReview.getDescricao() != null && newReview.getDescricao().length() > 250)) {
                return createErrorResponse(422);
            }

            newReview.setIdUsuario(userId);
            newReview.setNomeUsuario(username);

            reviewDAO.createReview(newReview);
            return createSuccessResponse("201").toString();

        } catch (SQLException e) {
            if (e.getMessage().contains("UNIQUE constraint failed")) {
                return createErrorResponse(409, "Erro: Você já avaliou este filme.");
            } else if (e.getMessage().contains("FOREIGN KEY constraint failed")) {
                return createErrorResponse(404, "Erro: Filme não encontrado.");
            }
            controller.log("Erro SQL ao criar review: " + e.getMessage(), ServerController.LogType.ERROR);
            return createErrorResponse(500);
        } catch (JSONException | NumberFormatException e) {
            return createErrorResponse(400);
        }
    }

    private String handleListUserReviews(int userId) {
        try {
            List<Review> reviews = reviewDAO.findReviewsByUserId(userId);
            JSONArray reviewsJson = new JSONArray();
            for (Review review : reviews) {
                reviewsJson.put(jsonFromReview(review));
            }
            JSONObject response = createSuccessResponse("200");
            response.put("reviews", reviewsJson);
            return response.toString();
        } catch (SQLException e) {
            controller.log("Erro SQL ao listar reviews do usuário: " + e.getMessage(), ServerController.LogType.ERROR);
            return createErrorResponse(500);
        }
    }

    private String handleEditReview(JSONObject request, int userId, String role) {
        if (!"user".equals(role)) {
            return createErrorResponse(403);
        }
        try {
            JSONObject reviewJson = request.getJSONObject("review");

            if (!reviewJson.has("id") || !reviewJson.has("nota")) {
                return createErrorResponse(422);
            }

            int reviewId = Integer.parseInt(reviewJson.getString("id"));
            int nota = Integer.parseInt(reviewJson.getString("nota"));
            String titulo = reviewJson.optString("titulo", "");
            String descricao = reviewJson.optString("descricao", "");


            if (nota < 1 || nota > 5 || descricao.length() > 250) {
                return createErrorResponse(422);
            }

            Review reviewToUpdate = reviewDAO.findByIdAndUserId(reviewId, userId);
            if (reviewToUpdate == null) {
                return createErrorResponse(404);
            }


            reviewToUpdate.setNota(nota);
            reviewToUpdate.setTitulo(titulo);
            reviewToUpdate.setDescricao(descricao);


            if (reviewDAO.updateReview(reviewToUpdate)) {
                return createSuccessResponse("200").toString();
            } else {

                return createErrorResponse(404);
            }

        } catch (SQLException e) {
            controller.log("Erro SQL ao editar review: " + e.getMessage(), ServerController.LogType.ERROR);
            return createErrorResponse(500);
        } catch (JSONException | NumberFormatException e) {
            return createErrorResponse(400);
        }
    }

    private String handleDeleteReview(JSONObject request, int userId, String role) {
        try {
            int reviewId = Integer.parseInt(request.getString("id"));
            boolean success;

            if ("admin".equals(role)) {
                success = reviewDAO.deleteReviewAsAdmin(reviewId);
            } else if ("user".equals(role)) {
                success = reviewDAO.deleteReview(reviewId, userId);
            } else {
                return createErrorResponse(403);
            }

            if (success) {
                return createSuccessResponse("200").toString();
            } else {
                return createErrorResponse(404);
            }
        } catch (SQLException e) {
            controller.log("Erro SQL ao excluir review: " + e.getMessage(), ServerController.LogType.ERROR);
            return createErrorResponse(500);
        } catch (JSONException | NumberFormatException e) {
            return createErrorResponse(400);
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
            controller.log("Erro SQL ao listar usuários: " + e.getMessage(), ServerController.LogType.ERROR);
            return createErrorResponse(500);
        }
    }

    private String handleAdminEditUser(JSONObject request) {
        try {
            int userId = Integer.parseInt(request.getString("id"));
            String newPassword = request.getJSONObject("usuario").getString("senha");

            if (isInvalidUserFields("tempUsernameForValidation", newPassword)) {
                return createErrorResponse(422);
            }

            if (userDAO.updateUserPasswordById(userId, newPassword)) {
                return createSuccessResponse("200").toString();
            } else {
                return createErrorResponse(404);
            }
        } catch (SQLException e) {
            controller.log("Erro SQL ao admin editar usuário: " + e.getMessage(), ServerController.LogType.ERROR);
            return createErrorResponse(500);
        } catch (JSONException | NumberFormatException e) {
            return createErrorResponse(400);
        }
    }

    private String handleAdminDeleteUser(JSONObject request) {
        try {
            int userId = Integer.parseInt(request.getString("id"));

            User userToDelete = userDAO.findById(userId);
            if (userToDelete != null && "admin".equalsIgnoreCase(userToDelete.getNome())) {
                return createErrorResponse(403);
            }

            if (userDAO.deleteUserById(userId)) {
                return createSuccessResponse("200").toString();
            } else {
                return createErrorResponse(404);
            }
        } catch (SQLException e) {
            controller.log("Erro SQL ao admin excluir usuário: " + e.getMessage(), ServerController.LogType.ERROR);
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
        return username == null || username.length() < 3 || username.length() > 20 || !username.matches("[a-zA-Z0-9]+") ||
                password == null || password.length() < 3 || password.length() > 20 || !password.matches("[a-zA-Z0-9]+");
    }

    // Modificado para aceitar List<String> e validar
    private boolean isInvalidMovieFields(JSONObject movieJson, List<String> generos) {
        if (movieJson == null) return true;
        String titulo = movieJson.optString("titulo");
        String ano = movieJson.optString("ano");
        String sinopse = movieJson.optString("sinopse");
        String diretor = movieJson.optString("diretor");

        if (titulo.isEmpty() || titulo.length() > 30 ||
                !ano.matches("\\d{4}") ||
                sinopse.length() > 250 ||
                diretor.isEmpty() ||
                generos == null || generos.isEmpty()) {
            return true;
        }

        // Nova validação: checa se todos os gêneros recebidos estão na lista pré-definida
        for (String g : generos) {
            if (!PREDEFINED_GENRES.contains(g)) {
                return true; // Encontrou um gênero inválido
            }
        }

        return false;
    }

    // Adicionado Helper para converter JSONArray para List<String>
    private List<String> jsonArrayToList(JSONArray jsonArray) {
        if (jsonArray == null) {
            return new ArrayList<>();
        }
        List<String> list = new ArrayList<>();
        for (int i = 0; i < jsonArray.length(); i++) {
            list.add(jsonArray.optString(i));
        }
        return list;
    }


    // Modificado para aceitar List<String>
    private Movie movieFromJson(JSONObject json, List<String> generos) throws JSONException{
        Movie movie = new Movie();
        movie.setId(json.optInt("id"));
        movie.setTitulo(json.getString("titulo"));
        movie.setDiretor(json.getString("diretor"));
        movie.setAno(json.getString("ano"));
        movie.setSinopse(json.getString("sinopse"));
        movie.setGeneros(generos); // Usa a lista já validada
        return movie;
    }

    private Review reviewFromJson(JSONObject json) throws JSONException {
        Review review = new Review();
        review.setIdFilme(Integer.parseInt(json.getString("id_filme")));
        review.setNota(Integer.parseInt(json.getString("nota")));
        review.setTitulo(json.optString("titulo"));
        review.setDescricao(json.optString("descricao"));
        review.setId(json.optInt("id"));
        return review;
    }

    private JSONObject jsonFromMovie(Movie movie) {
        JSONObject movieJson = new JSONObject();
        movieJson.put("id", String.valueOf(movie.getId()));
        movieJson.put("titulo", movie.getTitulo());
        movieJson.put("diretor", movie.getDiretor());
        movieJson.put("ano", movie.getAno());
        movieJson.put("genero", new JSONArray(movie.getGeneros()));
        movieJson.put("sinopse", movie.getSinopse());
        movieJson.put("nota", String.format("%.1f", movie.getNota()).replace(",","."));
        movieJson.put("qtd_avaliacoes", String.valueOf(movie.getQtdAvaliacoes()));
        return movieJson;
    }

    private JSONObject jsonFromReview(Review review) {
        JSONObject reviewJson = new JSONObject();
        reviewJson.put("id", String.valueOf(review.getId()));
        reviewJson.put("id_filme", String.valueOf(review.getIdFilme()));
        reviewJson.put("nome_usuario", review.getNomeUsuario());
        reviewJson.put("nota", String.valueOf(review.getNota()));
        reviewJson.put("titulo", review.getTitulo() != null ? review.getTitulo() : "");
        reviewJson.put("descricao", review.getDescricao() != null ? review.getDescricao() : "");
        reviewJson.put("data", review.getData() != null ? review.getData() : "");
        return reviewJson;
    }


    private String createErrorResponse(int status, String customMessage) {
        JSONObject errorResponse = new JSONObject();
        errorResponse.put("status", String.valueOf(status));
        errorResponse.put("mensagem", customMessage);
        return errorResponse.toString();
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
        server.removeClient(this);
        controller.log("Conexão com " + getIdentifier() + " fechada.", ServerController.LogType.DISCONNECTION);
    }

    public String getIdentifier() {
        if (username != null) {
            return username;
        }
        try {
            SocketAddress addr = channel.getRemoteAddress();
            return (addr != null) ? addr.toString() : "Cliente desconhecido";
        } catch (IOException e) {
            return "Cliente desconhecido";
        }
    }

    public String getUsername() { return username; }

    public String getClientIpAddress() {
        try {
            InetSocketAddress addr = (InetSocketAddress) channel.getRemoteAddress();
            return (addr != null) ? addr.getAddress().getHostAddress() : "N/A";
        } catch (IOException e) {
            return "N/A";
        }
    }

    public SocketChannel getChannel() {
        return channel;
    }
}