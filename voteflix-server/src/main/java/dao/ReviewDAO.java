package dao;

import database.DatabaseConnection;
import model.Review;
import java.sql.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class ReviewDAO {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public void createReview(Review review) throws SQLException {
        String sql = "INSERT INTO reviews(id_filme, id_usuario, nome_usuario, nota, titulo, descricao, data) VALUES(?, ?, ?, ?, ?, ?, ?)";
        Connection conn = null;
        PreparedStatement pstmt = null;
        String currentDateStr = LocalDate.now().format(DATE_FORMATTER);
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false);

            pstmt = conn.prepareStatement(sql);
            pstmt.setInt(1, review.getIdFilme());
            pstmt.setInt(2, review.getIdUsuario());
            pstmt.setString(3, review.getNomeUsuario());
            pstmt.setInt(4, review.getNota());
            pstmt.setString(5, review.getTitulo());
            pstmt.setString(6, review.getDescricao());
            pstmt.setString(7, currentDateStr);
            int affectedRows = pstmt.executeUpdate();

            if (affectedRows == 0) {
                conn.rollback();
                throw new SQLException("Falha ao criar review, nenhuma linha afetada.");
            }

            try (Statement stmt = conn.createStatement();
                 ResultSet generatedKeys = stmt.executeQuery("SELECT last_insert_rowid()")) {

                if (generatedKeys.next()) {
                    review.setId(generatedKeys.getInt(1));
                    review.setData(currentDateStr);
                } else {
                    System.err.println("Aviso: Não foi possível obter o ID gerado para a nova review.");
                    conn.rollback();
                    throw new SQLException("Falha ao obter o ID da review após a inserção.");
                }
            }

            updateMovieRating(conn, review.getIdFilme());

            conn.commit();

        } catch (SQLException e) {
            if (conn != null) try { conn.rollback(); } catch (SQLException ex) { System.err.println("Erro ao reverter transação em createReview: " + ex.getMessage()); }
            throw e;
        } finally {
            if (pstmt != null) try { pstmt.close(); } catch (SQLException e) { /* ignore */ }
            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                    conn.close();
                } catch (SQLException e) {
                    System.err.println("Erro ao fechar conexão em createReview: " + e.getMessage());
                }
            }
        }
    }

    public List<Review> findReviewsByMovieId(int idFilme) throws SQLException {
        List<Review> reviews = new ArrayList<>();
        String sql = "SELECT * FROM reviews WHERE id_filme = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, idFilme);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    reviews.add(mapResultSetToReview(rs));
                }
            }
        }
        return reviews;
    }

    public List<Review> findReviewsByUserId(int idUsuario) throws SQLException {
        List<Review> reviews = new ArrayList<>();
        String sql = "SELECT * FROM reviews WHERE id_usuario = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, idUsuario);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    reviews.add(mapResultSetToReview(rs));
                }
            }
        }
        return reviews;
    }

    public Review findByIdAndUserId(int reviewId, int userId) throws SQLException {
        String sql = "SELECT * FROM reviews WHERE id = ? AND id_usuario = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, reviewId);
            pstmt.setInt(2, userId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToReview(rs);
                }
            }
        }
        return null;
    }


    public boolean updateReview(Review review) throws SQLException {
        String sql = "UPDATE reviews SET nota = ?, titulo = ?, descricao = ?, data = ? WHERE id = ? AND id_usuario = ?";
        Connection conn = null;
        PreparedStatement pstmt = null;
        boolean success;
        String currentDateStr = LocalDate.now().format(DATE_FORMATTER);
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false);

            pstmt = conn.prepareStatement(sql);
            pstmt.setInt(1, review.getNota());
            pstmt.setString(2, review.getTitulo());
            pstmt.setString(3, review.getDescricao());
            pstmt.setString(4, currentDateStr);
            pstmt.setInt(5, review.getId());
            pstmt.setInt(6, review.getIdUsuario());

            int affectedRows = pstmt.executeUpdate();
            success = affectedRows > 0;

            if (success) {
                updateMovieRating(conn, review.getIdFilme());
                conn.commit();
            } else {
                conn.rollback();
            }

        } catch (SQLException e) {
            if (conn != null) try { conn.rollback(); } catch (SQLException ex) { System.err.println("Erro ao reverter transação em updateReview: " + ex.getMessage()); }
            throw e;
        } finally {
            if (pstmt != null) try { pstmt.close(); } catch (SQLException e) { /* ignore */ }
            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                    conn.close();
                } catch (SQLException e) {
                    System.err.println("Erro ao fechar conexão em updateReview: " + e.getMessage());
                }
            }
        }
        return success;
    }

    public boolean deleteReview(int reviewId, int userId) throws SQLException {
        String sqlSelect = "SELECT id_filme FROM reviews WHERE id = ? AND id_usuario = ?";
        String sqlDelete = "DELETE FROM reviews WHERE id = ? AND id_usuario = ?";
        Connection conn = null;
        PreparedStatement pstmtSelect = null;
        PreparedStatement pstmtDelete = null;
        ResultSet rs = null;
        boolean success;
        int movieId;

        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false);

            pstmtSelect = conn.prepareStatement(sqlSelect);
            pstmtSelect.setInt(1, reviewId);
            pstmtSelect.setInt(2, userId);
            rs = pstmtSelect.executeQuery();

            if (rs.next()) {
                movieId = rs.getInt("id_filme");
            } else {
                conn.rollback();
                return false;
            }

            pstmtDelete = conn.prepareStatement(sqlDelete);
            pstmtDelete.setInt(1, reviewId);
            pstmtDelete.setInt(2, userId);

            int affectedRows = pstmtDelete.executeUpdate();
            success = affectedRows > 0;

            if (success) {
                updateMovieRating(conn, movieId);
                conn.commit();
            } else {
                conn.rollback();
            }

        } catch (SQLException e) {
            if (conn != null) try { conn.rollback(); } catch (SQLException ex) { System.err.println("Erro ao reverter transação em deleteReview: " + ex.getMessage()); }
            throw e;
        } finally {
            if (rs != null) try { rs.close(); } catch (SQLException e) { /* ignore */ }
            if (pstmtSelect != null) try { pstmtSelect.close(); } catch (SQLException e) { /* ignore */ }
            if (pstmtDelete != null) try { pstmtDelete.close(); } catch (SQLException e) { /* ignore */ }
            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                    conn.close();
                } catch (SQLException e) {
                    System.err.println("Erro ao fechar conexão em deleteReview: " + e.getMessage());
                }
            }
        }
        return success;
    }

    public boolean deleteReviewAsAdmin(int reviewId) throws SQLException {
        String sqlSelect = "SELECT id_filme FROM reviews WHERE id = ?";
        String sqlDelete = "DELETE FROM reviews WHERE id = ?";
        Connection conn = null;
        PreparedStatement pstmtSelect = null;
        PreparedStatement pstmtDelete = null;
        ResultSet rs = null;
        boolean success;
        int movieId;

        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false);

            pstmtSelect = conn.prepareStatement(sqlSelect);
            pstmtSelect.setInt(1, reviewId);
            rs = pstmtSelect.executeQuery();

            if (rs.next()) {
                movieId = rs.getInt("id_filme");
            } else {
                conn.rollback();
                return false;
            }

            pstmtDelete = conn.prepareStatement(sqlDelete);
            pstmtDelete.setInt(1, reviewId);

            int affectedRows = pstmtDelete.executeUpdate();
            success = affectedRows > 0;

            if (success) {
                updateMovieRating(conn, movieId);
                conn.commit();
            } else {
                conn.rollback();
            }

        } catch (SQLException e) {
            if (conn != null) try { conn.rollback(); } catch (SQLException ex) { System.err.println("Erro ao reverter transação em deleteReviewAsAdmin: " + ex.getMessage()); }
            throw e;
        } finally {
            if (rs != null) try { rs.close(); } catch (SQLException e) { /* ignore */ }
            if (pstmtSelect != null) try { pstmtSelect.close(); } catch (SQLException e) { /* ignore */ }
            if (pstmtDelete != null) try { pstmtDelete.close(); } catch (SQLException e) { /* ignore */ }
            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                    conn.close();
                } catch (SQLException e) {
                    System.err.println("Erro ao fechar conexão em deleteReviewAsAdmin: " + e.getMessage());
                }
            }
        }
        return success;
    }

    private void updateMovieRating(Connection conn, int movieId) throws SQLException {
        String sqlAvg = "SELECT AVG(CAST(nota AS REAL)), COUNT(id) FROM reviews WHERE id_filme = ?";
        String sqlUpdate = "UPDATE filmes SET nota = ?, qtd_avaliacoes = ? WHERE id = ?";

        double averageRating = 0.0;
        int reviewCount = 0;

        try (PreparedStatement pstmtAvg = conn.prepareStatement(sqlAvg)) {
            pstmtAvg.setInt(1, movieId);
            try (ResultSet rsAvg = pstmtAvg.executeQuery()) {
                if (rsAvg.next()) {
                    averageRating = rsAvg.getDouble(1);
                    reviewCount = rsAvg.getInt(2);
                }
            }
        } catch (SQLException e) {
            System.err.println("Erro ao calcular média/contagem de reviews para filme ID " + movieId + ": " + e.getMessage());
            throw e;
        }


        try (PreparedStatement pstmtUpdate = conn.prepareStatement(sqlUpdate)) {
            pstmtUpdate.setDouble(1, averageRating);
            pstmtUpdate.setInt(2, reviewCount);
            pstmtUpdate.setInt(3, movieId);
            pstmtUpdate.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Erro ao atualizar nota/contagem no filme ID " + movieId + ": " + e.getMessage());
            throw e;
        }

    }

    private Review mapResultSetToReview(ResultSet rs) throws SQLException {
        Review review = new Review();
        review.setId(rs.getInt("id"));
        review.setIdFilme(rs.getInt("id_filme"));
        review.setIdUsuario(rs.getInt("id_usuario"));
        review.setNomeUsuario(rs.getString("nome_usuario"));
        review.setNota(rs.getInt("nota"));
        review.setTitulo(rs.getString("titulo"));
        review.setDescricao(rs.getString("descricao"));
        review.setData(rs.getString("data"));
        return review;
    }
}