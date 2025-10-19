package dao;

import database.DatabaseConnection;
import model.Movie;
import java.sql.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MovieDAO {

    public void createMovie(Movie movie) throws SQLException {
        String sql = "INSERT INTO filmes(titulo, diretor, ano, generos, sinopse) VALUES(?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, movie.getTitulo());
            pstmt.setString(2, movie.getDiretor());
            pstmt.setString(3, movie.getAno());
            pstmt.setString(4, String.join(",", movie.getGeneros()));
            pstmt.setString(5, movie.getSinopse());
            pstmt.executeUpdate();
        }
    }

    public List<Movie> listMovies() throws SQLException {
        List<Movie> movies = new ArrayList<>();
        String sql = "SELECT * FROM filmes";
        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                Movie movie = new Movie();
                movie.setId(rs.getInt("id"));
                movie.setTitulo(rs.getString("titulo"));
                movie.setDiretor(rs.getString("diretor"));
                movie.setAno(rs.getString("ano"));
                movie.setGeneros(Arrays.asList(rs.getString("generos").split(",")));
                movie.setSinopse(rs.getString("sinopse"));
                movie.setNota(rs.getDouble("nota"));
                movie.setQtdAvaliacoes(rs.getInt("qtd_avaliacoes"));
                movies.add(movie);
            }
        }
        return movies;
    }

    public Movie findMovieById(int id) throws SQLException {
        String sql = "SELECT * FROM filmes WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, id);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                Movie movie = new Movie();
                movie.setId(rs.getInt("id"));
                movie.setTitulo(rs.getString("titulo"));
                movie.setDiretor(rs.getString("diretor"));
                movie.setAno(rs.getString("ano"));
                movie.setGeneros(Arrays.asList(rs.getString("generos").split(",")));
                movie.setSinopse(rs.getString("sinopse"));
                movie.setNota(rs.getDouble("nota"));
                movie.setQtdAvaliacoes(rs.getInt("qtd_avaliacoes"));
                return movie;
            }
        }
        return null;
    }


    public boolean updateMovie(Movie movie) throws SQLException {
        String sql = "UPDATE filmes SET titulo = ?, diretor = ?, ano = ?, generos = ?, sinopse = ? WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, movie.getTitulo());
            pstmt.setString(2, movie.getDiretor());
            pstmt.setString(3, movie.getAno());
            pstmt.setString(4, String.join(",", movie.getGeneros()));
            pstmt.setString(5, movie.getSinopse());
            pstmt.setInt(6, movie.getId());
            return pstmt.executeUpdate() > 0;
        }
    }

    public boolean deleteMovie(int id) throws SQLException {
        String sql = "DELETE FROM filmes WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, id);
            return pstmt.executeUpdate() > 0;
        }
    }
}