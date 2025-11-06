package dao;

import database.DatabaseConnection;
import model.User;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class UserDAO {

    // Adiciona uma instância do ReviewDAO
    private final ReviewDAO reviewDAO;

    // Construtor para inicializar o ReviewDAO
    public UserDAO() {
        this.reviewDAO = new ReviewDAO();
    }

    public void createUser(User user) throws SQLException {
        String sql = "INSERT INTO usuarios(nome, senha) VALUES(?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, user.getNome());
            pstmt.setString(2, user.getSenha());
            pstmt.executeUpdate();
        }
    }

    public User findByUsername(String username) throws SQLException {
        String sql = "SELECT * FROM usuarios WHERE nome = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return mapResultSetToUser(rs);
            }
        }
        return null;
    }

    public User findById(int id) throws SQLException {
        String sql = "SELECT * FROM usuarios WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, id);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return mapResultSetToUser(rs);
            }
        }
        return null;
    }


    public boolean updatePassword(String username, String newPassword) throws SQLException {
        String sql = "UPDATE usuarios SET senha = ? WHERE nome = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, newPassword);
            pstmt.setString(2, username);
            return pstmt.executeUpdate() > 0;
        }
    }

    /**
     * Modificado para encontrar o usuário, obter o ID e
     * chamar o método deleteUserById, que contém a lógica de transação.
     */
    public boolean deleteUser(String username) throws SQLException {
        User userToDelete = findByUsername(username);
        if (userToDelete == null) {
            return false; // Usuário não encontrado
        }
        // Reutiliza a lógica principal de exclusão
        return deleteUserById(userToDelete.getId());
    }

    public List<User> listAllUsers() throws SQLException {
        List<User> users = new ArrayList<>();
        String sql = "SELECT id, nome FROM usuarios WHERE nome != 'admin'";
        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                User user = new User();
                user.setId(rs.getInt("id"));
                user.setNome(rs.getString("nome"));
                users.add(user);
            }
        }
        return users;
    }

    public boolean updateUserPasswordById(int userId, String newPassword) throws SQLException {
        String sql = "UPDATE usuarios SET senha = ? WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, newPassword);
            pstmt.setInt(2, userId);
            return pstmt.executeUpdate() > 0;
        }
    }

    /**
     * MODIFICADO: Agora executa uma transação para
     * 1. Encontrar os filmes que o usuário avaliou.
     * 2. Deletar o usuário (o que deleta as reviews em cascata).
     * 3. Atualizar a nota dos filmes afetados.
     * * CORRIGIDO: Removida a variável 'success' redundante.
     */
    public boolean deleteUserById(int userId) throws SQLException {
        String findReviewedMoviesSql = "SELECT DISTINCT id_filme FROM reviews WHERE id_usuario = ?";
        String deleteUserSql = "DELETE FROM usuarios WHERE id = ?";

        Connection conn = null;
        PreparedStatement pstmtFindMovies = null;
        PreparedStatement pstmtDeleteUser = null;
        ResultSet rs = null;
        List<Integer> movieIdsToUpdate = new ArrayList<>();
        // boolean success = false; // <-- Variável removida

        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false); // Inicia a transação

            // 1. Encontra todos os filmes que o usuário avaliou ANTES de deletá-lo
            pstmtFindMovies = conn.prepareStatement(findReviewedMoviesSql);
            pstmtFindMovies.setInt(1, userId);
            rs = pstmtFindMovies.executeQuery();
            while (rs.next()) {
                movieIdsToUpdate.add(rs.getInt("id_filme"));
            }
            rs.close();
            pstmtFindMovies.close();

            // 2. Deleta o usuário (o DB vai deletar as reviews em cascata)
            pstmtDeleteUser = conn.prepareStatement(deleteUserSql);
            pstmtDeleteUser.setInt(1, userId);
            int affectedRows = pstmtDeleteUser.executeUpdate();

            if (affectedRows == 0) {
                conn.rollback(); // Usuário não existe
                return false;
            }

            // 3. Atualiza a nota de cada filme afetado
            for (int movieId : movieIdsToUpdate) {
                // Passa a conexão da transação atual
                reviewDAO.updateMovieRating(conn, movieId);
            }

            // 4. Confirma a transação
            conn.commit();

            return true; // <-- CORRIGIDO AQUI: Retorna true diretamente

        } catch (SQLException e) {
            if (conn != null) try { conn.rollback(); } catch (SQLException ex) { System.err.println("Erro ao reverter transação em deleteUserById: " + ex.getMessage()); }
            throw e; // Lança a exceção original
        } finally {
            // Garante que todos os recursos sejam fechados
            if (rs != null) try { rs.close(); } catch (SQLException e) { /* ignora */ }
            if (pstmtFindMovies != null) try { pstmtFindMovies.close(); } catch (SQLException e) { /* ignora */ }
            if (pstmtDeleteUser != null) try { pstmtDeleteUser.close(); } catch (SQLException e) { /* ignora */ }
            // Restaura o autoCommit para a conexão (importante para pool de conexões)
            if (conn != null) try { conn.setAutoCommit(true); } catch (SQLException e) { /* ignora */ }
        }

        // Esta linha não é mais alcançável, mas alguns compiladores podem exigir.
        // Em nosso fluxo, ou ele retorna false, true, ou lança uma exceção.
        // Se o seu IDE ainda reclamar, pode deixar `return false;` aqui,
        // embora a lógica do 'try/catch' já cubra tudo.
    }

    private User mapResultSetToUser(ResultSet rs) throws SQLException {
        User user = new User();
        user.setId(rs.getInt("id"));
        user.setNome(rs.getString("nome"));
        user.setSenha(rs.getString("senha"));
        return user;
    }
}