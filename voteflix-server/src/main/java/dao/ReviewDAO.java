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
        String sql = "INSERT INTO reviews(id_filme, id_usuario, nome_usuario, nota, titulo, descricao, data, editado) VALUES(?, ?, ?, ?, ?, ?, ?, ?)";
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
            pstmt.setString(8, "false");

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
                    review.setEditado("false");
                }
            }

            updateMovieStatsIncremental(conn, review.getIdFilme(), review.getNota(), 0, "ADD");

            conn.commit();

        } catch (SQLException e) {
            if (conn != null) try { conn.rollback(); } catch (SQLException ex) { System.err.println("Erro rollback create: " + ex.getMessage()); }
            throw e;
        } finally {
            closeStatement(pstmt, "createReview");
            setAutoCommitTrue(conn, "createReview");
        }
    }

    public boolean updateReview(Review review) throws SQLException {
        String sqlUpdate = "UPDATE reviews SET nota = ?, titulo = ?, descricao = ?, editado = ? WHERE id = ? AND id_usuario = ?";
        String sqlSelectOld = "SELECT nota FROM reviews WHERE id = ? AND id_usuario = ?";

        Connection conn = null;
        PreparedStatement pstmtUpdate = null;
        PreparedStatement pstmtSelect = null;
        ResultSet rs = null;
        boolean success;

        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false);

            pstmtSelect = conn.prepareStatement(sqlSelectOld);
            pstmtSelect.setInt(1, review.getId());
            pstmtSelect.setInt(2, review.getIdUsuario());
            rs = pstmtSelect.executeQuery();

            int oldRating;
            if (rs.next()) {
                oldRating = rs.getInt("nota");
            } else {
                conn.rollback();
                return false;
            }

            pstmtUpdate = conn.prepareStatement(sqlUpdate);
            pstmtUpdate.setInt(1, review.getNota());
            pstmtUpdate.setString(2, review.getTitulo());
            pstmtUpdate.setString(3, review.getDescricao());
            pstmtUpdate.setString(4, "true");
            pstmtUpdate.setInt(5, review.getId());
            pstmtUpdate.setInt(6, review.getIdUsuario());

            int affectedRows = pstmtUpdate.executeUpdate();
            success = affectedRows > 0;

            if (success) {
                if (oldRating != review.getNota()) {
                    updateMovieStatsIncremental(conn, review.getIdFilme(), review.getNota(), oldRating, "UPDATE");
                }
                conn.commit();
            } else {
                conn.rollback();
            }

        } catch (SQLException e) {
            if (conn != null) try { conn.rollback(); } catch (SQLException ex) { System.err.println("Erro rollback update: " + ex.getMessage()); }
            throw e;
        } finally {
            closeResultSet(rs, "updateReview");
            closeStatement(pstmtSelect, "updateReview-Select");
            closeStatement(pstmtUpdate, "updateReview-Update");
            setAutoCommitTrue(conn, "updateReview");
        }
        return success;
    }

    public boolean deleteReview(int reviewId, int userId) throws SQLException {
        String sqlSelect = "SELECT id_filme, nota FROM reviews WHERE id = ? AND id_usuario = ?";
        String sqlDelete = "DELETE FROM reviews WHERE id = ? AND id_usuario = ?";

        return executeDeleteTransaction(sqlSelect, sqlDelete, reviewId, userId, false);
    }

    public boolean deleteReviewAsAdmin(int reviewId) throws SQLException {
        String sqlSelect = "SELECT id_filme, nota FROM reviews WHERE id = ?";
        String sqlDelete = "DELETE FROM reviews WHERE id = ?";

        return executeDeleteTransaction(sqlSelect, sqlDelete, reviewId, 0, true);
    }

    private boolean executeDeleteTransaction(String sqlSelect, String sqlDelete, int reviewId, int userId, boolean isAdmin) throws SQLException {
        Connection conn = null;
        PreparedStatement pstmtSelect = null;
        PreparedStatement pstmtDelete = null;
        ResultSet rs = null;
        boolean success;
        int movieId;
        int oldRating;

        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false);

            pstmtSelect = conn.prepareStatement(sqlSelect);
            pstmtSelect.setInt(1, reviewId);
            if (!isAdmin) {
                pstmtSelect.setInt(2, userId);
            }
            rs = pstmtSelect.executeQuery();

            if (rs.next()) {
                movieId = rs.getInt("id_filme");
                oldRating = rs.getInt("nota");
            } else {
                conn.rollback();
                return false;
            }

            pstmtDelete = conn.prepareStatement(sqlDelete);
            pstmtDelete.setInt(1, reviewId);
            if (!isAdmin) {
                pstmtDelete.setInt(2, userId);
            }

            int affectedRows = pstmtDelete.executeUpdate();
            success = affectedRows > 0;

            if (success) {
                updateMovieStatsIncremental(conn, movieId, 0, oldRating, "DELETE");
                conn.commit();
            } else {
                conn.rollback();
            }

        } catch (SQLException e) {
            if (conn != null) try { conn.rollback(); } catch (SQLException ex) { System.err.println("Erro rollback delete: " + ex.getMessage()); }
            throw e;
        } finally {
            closeResultSet(rs, "deleteTransaction");
            closeStatement(pstmtSelect, "deleteTransaction-Select");
            closeStatement(pstmtDelete, "deleteTransaction-Delete");
            setAutoCommitTrue(conn, "deleteTransaction");
        }
        return success;
    }

    private void updateMovieStatsIncremental(Connection conn, int movieId, int newRatingScore, int oldRatingScore, String operation) throws SQLException {
        String selectMovieSql = "SELECT nota, qtd_avaliacoes FROM filmes WHERE id = ?";
        String updateMovieSql = "UPDATE filmes SET nota = ?, qtd_avaliacoes = ? WHERE id = ?";

        try (PreparedStatement pstmtSelect = conn.prepareStatement(selectMovieSql)) {
            pstmtSelect.setInt(1, movieId);
            try (ResultSet rs = pstmtSelect.executeQuery()) {
                if (rs.next()) {
                    double currentAvg = rs.getDouble("nota");
                    int currentCount = rs.getInt("qtd_avaliacoes");

                    double currentSum = currentAvg * currentCount;
                    double newSum = currentSum;
                    int newCount = currentCount;

                    switch (operation) {
                        case "ADD":
                            newSum = currentSum + newRatingScore;
                            newCount = currentCount + 1;
                            break;
                        case "DELETE":
                            newSum = currentSum - oldRatingScore;
                            newCount = currentCount - 1;
                            break;
                        case "UPDATE":
                            newSum = currentSum - oldRatingScore + newRatingScore;
                            break;
                    }

                    if (newCount < 0) newCount = 0;
                    double newAvg = (newCount == 0) ? 0.0 : (newSum / newCount);

                    try (PreparedStatement pstmtUpdate = conn.prepareStatement(updateMovieSql)) {
                        pstmtUpdate.setDouble(1, newAvg);
                        pstmtUpdate.setInt(2, newCount);
                        pstmtUpdate.setInt(3, movieId);
                        pstmtUpdate.executeUpdate();
                    }
                }
            }
        }
    }

    public void updateMovieRating(Connection conn, int movieId) throws SQLException {
        String calculateSql = "SELECT COUNT(*) as qtd, AVG(nota) as media FROM reviews WHERE id_filme = ?";
        String updateMovieSql = "UPDATE filmes SET nota = ?, qtd_avaliacoes = ? WHERE id = ?";

        double newAverage = 0.0;
        int newCount = 0;

        try (PreparedStatement pstmtCalc = conn.prepareStatement(calculateSql)) {
            pstmtCalc.setInt(1, movieId);
            try (ResultSet rs = pstmtCalc.executeQuery()) {
                if (rs.next()) {
                    newCount = rs.getInt("qtd");
                    newAverage = rs.getDouble("media");
                }
            }
        }

        try (PreparedStatement pstmtUpdate = conn.prepareStatement(updateMovieSql)) {
            pstmtUpdate.setDouble(1, newAverage);
            pstmtUpdate.setInt(2, newCount);
            pstmtUpdate.setInt(3, movieId);
            pstmtUpdate.executeUpdate();
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

    public Review findById(int id) throws SQLException {
        String sql = "SELECT * FROM reviews WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, id);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToReview(rs);
                }
            }
        }
        return null;
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
        review.setEditado(rs.getString("editado"));
        return review;
    }

    private void closeStatement(Statement stmt, String methodName) {
        if (stmt != null) {
            try { stmt.close(); } catch (SQLException e) {
                System.err.println("Erro fechar Statement " + methodName + ": " + e.getMessage());
            }
        }
    }

    private void closeResultSet(ResultSet rs, String methodName) {
        if (rs != null) {
            try { rs.close(); } catch (SQLException e) {
                System.err.println("Erro fechar ResultSet " + methodName + ": " + e.getMessage());
            }
        }
    }

    private void setAutoCommitTrue(Connection conn, String methodName) {
        if (conn != null) {
            try { conn.setAutoCommit(true); } catch (SQLException e) {
                System.err.println("Erro restaurar autoCommit " + methodName + ": " + e.getMessage());
            }
        }
    }
}