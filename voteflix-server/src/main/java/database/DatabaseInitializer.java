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
                + "FOREIGN KEY (id_filme) REFERENCES filmes(id) ON DELETE CASCADE,"
                + "FOREIGN KEY (id_usuario) REFERENCES usuarios(id) ON DELETE CASCADE,"
                + "UNIQUE(id_filme, id_usuario)"
                + ");";

        String createAdminUserSql = "INSERT OR IGNORE INTO usuarios (nome, senha) VALUES ('admin', 'admin');";

        String checkColumnSql = "PRAGMA table_info(reviews);";

        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(createUserTableSql);
            stmt.execute(createMovieTableSql);

            boolean columnExists = false;
            try (ResultSet rs = stmt.executeQuery(checkColumnSql)) {
                while (rs.next()) {
                    if ("id_usuario".equalsIgnoreCase(rs.getString("name"))) {
                        columnExists = true;
                        break;
                    }
                }
            } catch (SQLException e) {
                if (!e.getMessage().contains("no such table: reviews")) {
                    System.err.println("Erro ao verificar colunas da tabela reviews: " + e.getMessage());
                }
            }

            if (!columnExists) {
                stmt.execute(createReviewTableSql);
                System.out.println("Tabela 'reviews' criada ou verificada.");
            } else {
                System.out.println("Tabela 'reviews' já existe com a coluna 'id_usuario'.");
            }


            stmt.execute(createAdminUserSql);

        } catch (SQLException e) {
            System.err.println("Erro ao inicializar o banco de dados: " + e.getMessage());
        }
    }
}