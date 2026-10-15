package com.procampo.db;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Gerenciador de conexão com o banco de dados SQLite (RNF03, RNF04).
 * Suporta configuração de chaves estrangeiras (PRAGMA foreign_keys = ON) e
 * funcionalidades de Importação/Exportação do arquivo do banco de dados (RF10, RF11).
 */
public class DatabaseConnection {
    private static final String DEFAULT_DB_NAME = "orcamentos.db";
    private static File currentDbPath;
    private static DatabaseConnection instance;

    private DatabaseConnection() {
        // Inicializa o caminho do banco de dados no diretório atual de trabalho ou na home do usuário
        String userDir = System.getProperty("user.dir");
        currentDbPath = new File(userDir, DEFAULT_DB_NAME);
    }

    public static synchronized DatabaseConnection getInstance() {
        if (instance == null) {
            instance = new DatabaseConnection();
        }
        return instance;
    }

    public static File getCurrentDbPath() {
        if (instance == null) {
            getInstance();
        }
        return currentDbPath;
    }

    public static void setCurrentDbPath(File path) {
        currentDbPath = path;
    }

    /**
     * Obtém uma conexão ativa com o banco SQLite e garante que PRAGMA foreign_keys = ON esteja habilitado.
     */
    public Connection getConnection() throws SQLException {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            throw new SQLException("Driver SQLite não encontrado na pasta de dependências.", e);
        }

        String url = "jdbc:sqlite:" + currentDbPath.getAbsolutePath();
        Connection conn = DriverManager.getConnection(url);

        // ESSENCIAL para SQLite: ativar verificação de chaves estrangeiras (on delete restrict / cascade)
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("PRAGMA foreign_keys = ON;");
        }
        return conn;
    }

    /**
     * RF10 - Exportar Banco de Dados: Copia o arquivo .db atual para o diretório de destino escolhido pelo usuário.
     */
    public boolean exportDatabase(File destinationFile) throws IOException {
        if (currentDbPath == null || !currentDbPath.exists()) {
            throw new IOException("Arquivo de banco de dados atual não encontrado: " + currentDbPath);
        }
        Files.copy(currentDbPath.toPath(), destinationFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        return true;
    }

    /**
     * RF11 - Importar Banco de Dados: Substitui a base de dados atual por um arquivo .db / .sqlite externo selecionado pelo usuário.
     */
    public boolean importDatabase(File sourceFile) throws IOException {
        if (sourceFile == null || !sourceFile.exists()) {
            throw new IOException("Arquivo de banco de dados origem não encontrado: " + sourceFile);
        }
        Files.copy(sourceFile.toPath(), currentDbPath.toPath(), StandardCopyOption.REPLACE_EXISTING);
        return true;
    }
}
