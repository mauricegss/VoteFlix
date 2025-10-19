package database;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseInitializer {

    public static void createTables() {
        String createUserTableSql = "CREATE TABLE IF NOT EXISTS usuarios ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "nome TEXT NOT NULL UNIQUE,"
                + "senha TEXT NOT NULL"
                + ");";

        String createMovieTableSql = "CREATE TABLE IF NOT EXISTS filmes ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "titulo TEXT NOT NULL,"
                + "diretor TEXT NOT NULL,"
                + "ano TEXT NOT NULL,"
                + "generos TEXT NOT NULL,"
                + "sinopse TEXT,"
                + "nota REAL DEFAULT 0,"
                + "qtd_avaliacoes INTEGER DEFAULT 0,"
                + "UNIQUE(titulo, diretor, ano)"
                + ");";

        String createAdminUserSql = "INSERT OR IGNORE INTO usuarios (nome, senha) VALUES ('admin', 'admin');";

        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(createUserTableSql);
            stmt.execute(createMovieTableSql);
            stmt.execute(createAdminUserSql);

        } catch (SQLException e) {
            System.err.println("Erro ao inicializar o banco de dados: " + e.getMessage());
        }
    }
}