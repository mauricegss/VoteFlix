package database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseConnection {

    private static Connection connection = null;
    private static final String DB_URL = "jdbc:sqlite:voteflix.db";

    public static Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            try {
                Class.forName("org.sqlite.JDBC");
                connection = DriverManager.getConnection(DB_URL);
                enableForeignKeySupport(connection);

            } catch (ClassNotFoundException e) {
                throw new SQLException("Driver do SQLite não encontrado.", e);
            }
        }
        return connection;
    }

    private static void enableForeignKeySupport(Connection conn) throws SQLException {
        if (conn != null) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("PRAGMA foreign_keys = ON;");
            }
        }
    }

    public static void closeConnection() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            System.err.println("Erro ao fechar a conexão com o banco de dados: " + e.getMessage());
        }
    }
}