package com.procampo.db;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Responsável por inicializar o esquema relacional no SQLite e acionar a carga inicial de dados (Seed) se o banco estiver vazio.
 */
public class DatabaseInitializer {

    public static void initialize() {
        String createItemTable = """
                CREATE TABLE IF NOT EXISTS Item (
                    ID_Item INTEGER PRIMARY KEY AUTOINCREMENT,
                    Descricao TEXT NOT NULL UNIQUE,
                    Unidade_Medida TEXT NOT NULL,
                    Valor_Unitario REAL NOT NULL,
                    Categoria TEXT
                );
                """;

        String createModeloTable = """
                CREATE TABLE IF NOT EXISTS Modelo (
                    ID_Modelo INTEGER PRIMARY KEY AUTOINCREMENT,
                    Nome TEXT NOT NULL UNIQUE,
                    Ciclo_Produtivo TEXT,
                    Espacamento TEXT,
                    Produtividade TEXT,
                    Categoria TEXT
                );
                """;

        String createEtapaTable = """
                CREATE TABLE IF NOT EXISTS Etapa (
                    ID_Etapa INTEGER PRIMARY KEY AUTOINCREMENT,
                    ID_Modelo INTEGER NOT NULL,
                    Nome_Etapa TEXT NOT NULL,
                    Ordem_Exibicao INTEGER NOT NULL,
                    FOREIGN KEY (ID_Modelo) REFERENCES Modelo(ID_Modelo) ON DELETE CASCADE
                );
                """;

        String createComposicaoTable = """
                CREATE TABLE IF NOT EXISTS Composicao (
                    ID_Composicao INTEGER PRIMARY KEY AUTOINCREMENT,
                    ID_Etapa INTEGER NOT NULL,
                    ID_Item INTEGER NOT NULL,
                    Quantidade REAL NOT NULL,
                    FOREIGN KEY (ID_Etapa) REFERENCES Etapa(ID_Etapa) ON DELETE CASCADE,
                    FOREIGN KEY (ID_Item) REFERENCES Item(ID_Item) ON DELETE RESTRICT
                );
                """;

        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             Statement stmt = conn.createStatement()) {

            stmt.execute(createItemTable);
            stmt.execute(createModeloTable);
            stmt.execute(createEtapaTable);
            stmt.execute(createComposicaoTable);

            // Migrations: Adicionar coluna Categoria em Item, se não existir
            try (ResultSet rs = stmt.executeQuery("PRAGMA table_info(Item)")) {
                boolean hasCategoria = false;
                while (rs.next()) {
                    if ("Categoria".equalsIgnoreCase(rs.getString("name"))) {
                        hasCategoria = true;
                        break;
                    }
                }
                if (!hasCategoria) {
                    stmt.execute("ALTER TABLE Item ADD COLUMN Categoria TEXT");
                }
            }

            // Verifica se o banco está vazio para rodar a carga inicial da planilha Excel (Seed)
            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) AS total FROM Item")) {
                if (rs.next() && rs.getInt("total") == 0) {
                    System.out.println("Banco de dados inicializado vazio. Executando importação de dados da planilha de orçamento...");
                    DataSeedImporter.importSeedData(conn);
                }
            }

        } catch (SQLException e) {
            System.err.println("Erro ao inicializar o banco de dados SQLite: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        System.out.println("=== Inicializando Banco de Dados ProCampo ===");
        initialize();
        System.out.println("=== Inicialização e Carga Concluídas ===");
    }
}
