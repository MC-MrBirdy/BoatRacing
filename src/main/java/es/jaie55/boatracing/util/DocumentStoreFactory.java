package es.jaie55.boatracing.util;

import es.jaie55.boatracing.BoatRacingPlugin;

import org.bukkit.configuration.ConfigurationSection;

import java.io.File;
import java.sql.SQLException;

public final class DocumentStoreFactory {
    private DocumentStoreFactory() {
    }

    public static DocumentStore create(BoatRacingPlugin plugin) {
        ConfigurationSection database = plugin.getConfig().getConfigurationSection("database");
        String mode = resolveMode(database);
        if ("YAML".equalsIgnoreCase(mode)) {
            return new YamlFileDocumentStore(plugin.getDataFolder());
        }

        boolean migrateLegacyYaml = database == null || database.getBoolean("migrate-legacy-yaml", true);
        String tableName = database != null ? database.getString("table", "boatracing_data") : "boatracing_data";

        try {
            if ("MYSQL".equalsIgnoreCase(mode)) {
                String host = database != null ? database.getString("mysql.host", "localhost") : "localhost";
                int port = database != null ? database.getInt("mysql.port", 3306) : 3306;
                String databaseName = database != null ? database.getString("mysql.database", "boatracing") : "boatracing";
                String username = database != null ? database.getString("mysql.username", "root") : "root";
                String password = database != null ? database.getString("mysql.password", "") : "";
                String parameters = database != null ? database.getString("mysql.parameters", "") : "";
                if (parameters == null || parameters.isBlank()) {
                    parameters = "useUnicode=true&characterEncoding=utf8&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
                }
                String jdbcUrl = "jdbc:mysql://" + host + ":" + port + "/" + databaseName + "?" + parameters;
                return new JdbcDocumentStore(plugin.getLogger(), jdbcUrl, username, password, tableName, plugin.getDataFolder(), migrateLegacyYaml, JdbcDocumentStore.Dialect.MYSQL);
            }

            File sqliteFile = new File(plugin.getDataFolder(), database != null ? database.getString("sqlite.file", "boatracing.db") : "boatracing.db");
            String jdbcUrl = "jdbc:sqlite:" + sqliteFile.getAbsolutePath().replace('\\', '/');
            return new JdbcDocumentStore(plugin.getLogger(), jdbcUrl, "", "", tableName, plugin.getDataFolder(), migrateLegacyYaml, JdbcDocumentStore.Dialect.SQLITE);
        } catch (SQLException e) {
            plugin.getLogger().warning("Database storage unavailable, falling back to local YAML files: " + e.getMessage());
            return new YamlFileDocumentStore(plugin.getDataFolder());
        }
    }

    private static String resolveMode(ConfigurationSection database) {
        if (database == null) return "SQLITE";

        String mode = database.getString("mode", "");
        if (mode != null && !mode.isBlank()) {
            return mode.trim().toUpperCase(java.util.Locale.ROOT);
        }

        boolean enabled = database.getBoolean("enabled", true);
        if (!enabled) return "YAML";
        String legacyType = database.getString("type", "SQLITE");
        return legacyType == null || legacyType.isBlank()
                ? "SQLITE"
                : legacyType.trim().toUpperCase(java.util.Locale.ROOT);
    }
}
