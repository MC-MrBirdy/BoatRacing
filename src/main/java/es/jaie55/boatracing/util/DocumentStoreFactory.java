package es.jaie55.boatracing.util;

import es.jaie55.boatracing.BoatRacingPlugin;

import org.bukkit.configuration.ConfigurationSection;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;

public final class DocumentStoreFactory {
    private static boolean jdbcLoaded;

    private DocumentStoreFactory() {
    }

    public static DocumentStore create(BoatRacingPlugin plugin) {
        ConfigurationSection database = plugin.getConfig().getConfigurationSection("database");
        String mode = resolveMode(database);
        if ("YAML".equalsIgnoreCase(mode)) {
            return new YamlFileDocumentStore(plugin.getDataFolder());
        }

        if (!jdbcLoaded) {
            loadJdbcDrivers(plugin);
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

    private static synchronized void loadJdbcDrivers(BoatRacingPlugin plugin) {
        if (jdbcLoaded) return;
        jdbcLoaded = true;

        File libDir = new File(plugin.getDataFolder(), "lib");
        if (!libDir.exists()) libDir.mkdirs();

        // Ensure required JDBC driver JARs are present; download if missing.
        // SHA-256 checksums for integrity verification.
        ensureJdbcJar(plugin, libDir, "sqlite-jdbc-3.46.1.0.jar",
                "https://repo1.maven.org/maven2/org/xerial/sqlite-jdbc/3.46.1.0/sqlite-jdbc-3.46.1.0.jar",
                "bcdGTjgDZI0/8YpzWbq2rfB5/NhJWxiZH29e3Lisbjs=");
        ensureJdbcJar(plugin, libDir, "slf4j-api-2.0.16.jar",
                "https://repo1.maven.org/maven2/org/slf4j/slf4j-api/2.0.16/slf4j-api-2.0.16.jar",
                "oSV43eG6AL2bgW04iguHmSjQC6s8g8JA9wE79BlsV5o=");
        ensureJdbcJar(plugin, libDir, "slf4j-simple-2.0.16.jar",
                "https://repo1.maven.org/maven2/org/slf4j/slf4j-simple/2.0.16/slf4j-simple-2.0.16.jar",
                "7/wyAYZYvqCdHgjH0QYMytRsCGlg9YPQfdf/6cEXKkc=");
        ensureJdbcJar(plugin, libDir, "mysql-connector-j-8.4.0.jar",
                "https://repo1.maven.org/maven2/com/mysql/mysql-connector-j/8.4.0/mysql-connector-j-8.4.0.jar",
                "13lih30BB3fP+ZcBXakO5onw9Lt2hINA4UiPK4MzKvU=");

        File[] jars = libDir.listFiles((dir, name) -> name.endsWith(".jar"));
        if (jars == null || jars.length == 0) {
            plugin.getLogger().warning("No JDBC drivers found in " + libDir.getAbsolutePath() + "; database modes SQLITE/MYSQL will not be available. Set database.mode to YAML or place the driver JARs in the lib folder.");
            return;
        }

        try {
            URL[] urls = new URL[jars.length];
            for (int i = 0; i < jars.length; i++) {
                urls[i] = jars[i].toURI().toURL();
            }
            URLClassLoader loader = new URLClassLoader(urls, ClassLoader.getPlatformClassLoader());

            for (File jar : jars) {
                String name = jar.getName().toLowerCase();
                String driverClass = name.contains("sqlite") ? "org.sqlite.JDBC" :
                        name.contains("mysql") ? "com.mysql.cj.jdbc.Driver" : null;
                if (driverClass == null) continue;

                try {
                    Class<?> dc = loader.loadClass(driverClass);
                    Driver driver = (Driver) dc.getDeclaredConstructor().newInstance();
                    DriverManager.registerDriver(new DriverDelegate(driver));
                    plugin.getLogger().info("Loaded JDBC driver: " + driverClass);
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to load JDBC driver " + driverClass + ": " + e.getMessage());
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to set up JDBC classpath: " + e.getMessage());
        }
    }

    private static void ensureJdbcJar(BoatRacingPlugin plugin, File libDir, String fileName, String mavenUrl, String expectedChecksum) {
        File target = new File(libDir, fileName);

        if (target.exists()) {
            if (checksumMatches(target, expectedChecksum)) return;
            plugin.getLogger().warning("JDBC driver " + fileName + " has invalid checksum; re-downloading.");
            target.delete();
        }

        plugin.getLogger().info("Downloading JDBC driver: " + fileName + " ...");
        try {
            URL url = new URL(mavenUrl);
            try (InputStream in = url.openStream()) {
                Files.copy(in, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            if (checksumMatches(target, expectedChecksum)) {
                plugin.getLogger().info("Downloaded and verified JDBC driver: " + fileName);
            } else {
                plugin.getLogger().warning("JDBC driver " + fileName + " checksum mismatch after download; driver will not be available.");
                target.delete();
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to download JDBC driver " + fileName + ": " + e.getMessage());
        }
    }

    private static boolean checksumMatches(File file, String expectedBase64) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(Files.readAllBytes(file.toPath()));
            return java.util.Base64.getEncoder().encodeToString(hash).equals(expectedBase64);
        } catch (NoSuchAlgorithmException | IOException e) {
            return false;
        }
    }

    private static class DriverDelegate implements Driver {
        private final Driver delegate;

        DriverDelegate(Driver delegate) {
            this.delegate = delegate;
        }

        @Override
        public java.sql.Connection connect(String url, java.util.Properties info) throws SQLException {
            return delegate.connect(url, info);
        }

        @Override
        public boolean acceptsURL(String url) throws SQLException {
            return delegate.acceptsURL(url);
        }

        @Override
        public DriverPropertyInfo[] getPropertyInfo(String url, java.util.Properties info) throws SQLException {
            return delegate.getPropertyInfo(url, info);
        }

        @Override
        public int getMajorVersion() {
            return delegate.getMajorVersion();
        }

        @Override
        public int getMinorVersion() {
            return delegate.getMinorVersion();
        }

        @Override
        public boolean jdbcCompliant() {
            return delegate.jdbcCompliant();
        }

        @Override
        public java.util.logging.Logger getParentLogger() throws java.sql.SQLFeatureNotSupportedException {
            return delegate.getParentLogger();
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
