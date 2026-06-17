package es.jaie55.boatracing.util;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

final class JdbcDocumentStore implements DocumentStore {
    enum Dialect {
        SQLITE,
        MYSQL
    }

    private final java.util.logging.Logger logger;
    private final File dataFolder;
    private final String tableName;
    private final boolean migrateLegacyYaml;
    private final Dialect dialect;
    private final Connection connection;

    JdbcDocumentStore(
            java.util.logging.Logger logger,
            String jdbcUrl,
            String username,
            String password,
            String tableName,
            File dataFolder,
            boolean migrateLegacyYaml,
            Dialect dialect
    ) throws SQLException {
        this.logger = logger;
        this.dataFolder = dataFolder;
        this.tableName = sanitizeTableName(tableName);
        this.migrateLegacyYaml = migrateLegacyYaml;
        this.dialect = dialect == null ? Dialect.SQLITE : dialect;
        this.connection = DriverManager.getConnection(jdbcUrl, username, password);
        ensureSchema();
    }

    @Override
    public synchronized String read(String documentName) throws IOException {
        try {
            String content = readFromDatabase(documentName);
            if (content != null) return content;

            if (!migrateLegacyYaml) return null;
            Path legacyFile = dataFolder.toPath().resolve(documentName);
            if (!Files.exists(legacyFile)) return null;

            content = Files.readString(legacyFile, StandardCharsets.UTF_8);
            write(documentName, content);
            tryMoveLegacyFile(legacyFile);
            return content;
        } catch (SQLException e) {
            throw new IOException("Failed to read document '" + documentName + "' from database", e);
        }
    }

    @Override
    public synchronized void write(String documentName, String content) throws IOException {
        try {
            upsert(documentName, content == null ? "" : content);
        } catch (SQLException e) {
            throw new IOException("Failed to write document '" + documentName + "' to database", e);
        }
    }

    @Override
    public synchronized void close() throws IOException {
        try {
            connection.close();
        } catch (SQLException e) {
            throw new IOException("Failed to close database connection", e);
        }
    }

    private static String sanitizeTableName(String raw) {
        if (raw == null || raw.isBlank()) return "boatracing_data";
        String cleaned = raw.trim().replaceAll("[^A-Za-z0-9_]", "");
        return cleaned.isBlank() ? "boatracing_data" : cleaned;
    }

    private void ensureSchema() throws SQLException {
        String ddl;
        if (dialect == Dialect.MYSQL) {
            ddl = "CREATE TABLE IF NOT EXISTS `" + tableName + "` ("
                    + "document_key VARCHAR(255) NOT NULL PRIMARY KEY, "
                    + "content MEDIUMTEXT NOT NULL, "
                    + "updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP"
                    + ")";
        } else {
            ddl = "CREATE TABLE IF NOT EXISTS \"" + tableName + "\" ("
                    + "document_key TEXT PRIMARY KEY, "
                    + "content TEXT NOT NULL, "
                    + "updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP"
                    + ")";
        }

        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(ddl);
        }
    }

    private String readFromDatabase(String documentName) throws SQLException {
        String sql = "SELECT content FROM " + quotedTableName() + " WHERE document_key = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, documentName);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) return null;
                return resultSet.getString(1);
            }
        }
    }

    private void upsert(String documentName, String content) throws SQLException {
        String sql;
        if (dialect == Dialect.MYSQL) {
            sql = "INSERT INTO " + quotedTableName() + " (document_key, content, updated_at) VALUES (?, ?, CURRENT_TIMESTAMP) "
                    + "ON DUPLICATE KEY UPDATE content = VALUES(content), updated_at = CURRENT_TIMESTAMP";
        } else {
            sql = "INSERT INTO " + quotedTableName() + " (document_key, content, updated_at) VALUES (?, ?, CURRENT_TIMESTAMP) "
                    + "ON CONFLICT(document_key) DO UPDATE SET content = excluded.content, updated_at = CURRENT_TIMESTAMP";
        }

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, documentName);
            statement.setString(2, content);
            statement.executeUpdate();
        }
    }

    private String quotedTableName() {
        if (dialect == Dialect.MYSQL) {
            return "`" + tableName + "`";
        }
        return "\"" + tableName + "\"";
    }

    private void tryMoveLegacyFile(Path legacyFile) {
        Path migratedFile = legacyFile.resolveSibling(legacyFile.getFileName().toString() + ".migrated");
        try {
            Files.move(legacyFile, migratedFile, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            logger.fine("Imported legacy YAML document but could not rename it: " + e.getMessage());
        }
    }
}
