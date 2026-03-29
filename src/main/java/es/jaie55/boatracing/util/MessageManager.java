package es.jaie55.boatracing.util;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Loads and serves user-facing messages from messages_en.yml, messages_es.yml,
 * messages_zh_TW.yml, messages_ru.yml – or any custom file placed in the plugin folder.
 * Language is configured in config.yml via the 'language' setting (default: "en").
 */
public final class MessageManager {
    private final JavaPlugin plugin;
    private YamlConfiguration messages;

    public MessageManager(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        String lang = sanitizeLanguage(plugin.getConfig().getString("language", "en"));

        File file = resolveLanguageFile(lang);
        if (file == null && !"en".equalsIgnoreCase(lang)) {
            plugin.getLogger().warning("Language '" + lang + "' was not found. Falling back to 'en'.");
            lang = "en";
            file = resolveLanguageFile(lang);
        }
        if (file == null) {
            plugin.getLogger().severe("Could not load message bundle 'messages_en.yml'. Message keys will be shown as fallback.");
            messages = new YamlConfiguration();
            return;
        }

        String filename = file.getName();
        messages = YamlConfiguration.loadConfiguration(file);

        // Merge any new keys added in future updates
        try (InputStream defaults = plugin.getResource(filename)) {
            if (defaults != null) {
                YamlConfiguration def = YamlConfiguration.loadConfiguration(
                        new InputStreamReader(defaults, StandardCharsets.UTF_8));
                messages.addDefaults(def);
                messages.options().copyDefaults(true);
            }
        } catch (Exception ex) {
            plugin.getLogger().warning("Failed to merge default messages for '" + filename + "': " + ex.getMessage());
        }
    }

    private String sanitizeLanguage(String lang) {
        if (lang == null) return "en";
        String normalized = lang.trim();
        if (normalized.isEmpty()) return "en";
        if (!normalized.matches("[A-Za-z0-9_-]+")) {
            plugin.getLogger().warning("Invalid language code '" + normalized + "'. Falling back to 'en'.");
            return "en";
        }
        return normalized;
    }

    private File resolveLanguageFile(String lang) {
        String filename = "messages_" + lang + ".yml";
        File file = new File(plugin.getDataFolder(), filename);
        if (file.exists()) return file;

        try (InputStream bundled = plugin.getResource(filename)) {
            if (bundled == null) return null;
        } catch (Exception ignored) {
            return null;
        }

        plugin.saveResource(filename, false);
        return file.exists() ? file : null;
    }

    /**
     * Get a message with optional placeholder pairs.
     * Usage: {@code msg.get("race.track-not-found", "track", trackName)}
     * replaces every {@code {track}} in the template.
     *
     * @param key   dot-path into messages.yml
     * @param pairs alternating placeholder name / value: "ph1", val1, "ph2", val2 …
     * @return the resolved message (still contains &amp; colour codes)
     */
    public String get(String key, Object... pairs) {
        String msg = messages.getString(key);
        if (msg == null) return key; // fallback: show the key itself
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            msg = msg.replace("{" + pairs[i] + "}", String.valueOf(pairs[i + 1]));
        }
        return msg;
    }
}
