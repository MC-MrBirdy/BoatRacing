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
    private static final java.util.Set<String> BUNDLED = java.util.Set.of("en", "es", "zh_TW", "ru");

    private final JavaPlugin plugin;
    private YamlConfiguration messages;

    public MessageManager(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        // Determine language from config; default to "en"
        String lang = plugin.getConfig().getString("language", "en");
        if (!BUNDLED.contains(lang)) lang = "en";
        
        String filename = "messages_" + lang + ".yml";
        File file = new File(plugin.getDataFolder(), filename);
        if (!file.exists()) {
            plugin.saveResource(filename, false);
        }
        messages = YamlConfiguration.loadConfiguration(file);
        // Merge any new keys added in future updates
        InputStream defaults = plugin.getResource(filename);
        if (defaults != null) {
            YamlConfiguration def = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(defaults, StandardCharsets.UTF_8));
            messages.addDefaults(def);
        }
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
