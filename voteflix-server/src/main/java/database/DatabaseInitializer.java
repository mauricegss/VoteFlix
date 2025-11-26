package database;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.ResultSet;

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

        String createReviewTableSql = "CREATE TABLE IF NOT EXISTS reviews ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "id_filme INTEGER NOT NULL,"
                + "id_usuario INTEGER NOT NULL,"
                + "nome_usuario TEXT NOT NULL,"
                + "nota INTEGER NOT NULL CHECK(nota >= 1 AND nota <= 5),"
                + "titulo TEXT,"
                + "descricao TEXT,"
                + "data TEXT,"
                + "editado TEXT DEFAULT 'false',"
                + "FOREIGN KEY (id_filme) REFERENCES filmes(id) ON DELETE CASCADE,"
                + "FOREIGN KEY (id_usuario) REFERENCES usuarios(id) ON DELETE CASCADE,"
                + "UNIQUE(id_filme, id_usuario)"
                + ");";

        String createAdminUserSql = "INSERT OR IGNORE INTO usuarios (nome, senha) VALUES ('admin', 'admin');";

        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(createUserTableSql);
            stmt.execute(createMovieTableSql);
            stmt.execute(createReviewTableSql);

            // Migração de dados para tabelas existentes
            checkAndAddColumnToReviews(stmt, "id_usuario", "INTEGER NOT NULL DEFAULT 0");
            checkAndAddColumnToReviews(stmt, "editado", "TEXT DEFAULT 'false'");

            stmt.execute(createAdminUserSql);

        } catch (SQLException e) {
            System.err.println("Erro ao inicializar o banco de dados: " + e.getMessage());
        }
    }

    // Refatorado: Específico para tabela 'reviews' para evitar warning de parâmetro
    private static void checkAndAddColumnToReviews(Statement stmt, String columnName, String columnType) {
        String tableName = "reviews";
        String checkColumnSql = "PRAGMA table_info(" + tableName + ");";
        boolean columnExists = false;
        try (ResultSet rs = stmt.executeQuery(checkColumnSql)) {
            while (rs.next()) {
                if (columnName.equalsIgnoreCase(rs.getString("name"))) {
                    columnExists = true;
                    break;
                }
            }
        } catch (SQLException e) {
            System.err.println("Erro ao verificar coluna " + columnName + ": " + e.getMessage());
        }

        if (!columnExists) {
            try {
                stmt.execute("ALTER TABLE " + tableName + " ADD COLUMN " + columnName + " " + columnType);
                System.out.println("Coluna '" + columnName + "' adicionada à tabela '" + tableName + "'.");
            } catch (SQLException e) {
                System.err.println("Erro ao adicionar coluna " + columnName + ": " + e.getMessage());
            }
        }
    }
}