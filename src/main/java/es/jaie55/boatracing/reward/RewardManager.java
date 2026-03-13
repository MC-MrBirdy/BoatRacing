package es.jaie55.boatracing.reward;

import es.jaie55.boatracing.BoatRacingPlugin;
import es.jaie55.boatracing.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.*;

/**
 * Manages race rewards configured in config.yml under racing.rewards.
 * Supports per-position commands, messages, and broadcasts with placeholders.
 */
public class RewardManager {
    private final BoatRacingPlugin plugin;

    public RewardManager(BoatRacingPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("racing.rewards.enabled", false);
    }

    /**
     * Distribute rewards to all finishers in order.
     *
     * @param results  sorted list of (UUID, finishTime) pairs, position 1 first
     * @param trackName name of the track
     * @param totalLaps total laps in the race
     */
    public void giveRewards(List<Map.Entry<UUID, Long>> results, String trackName, int totalLaps) {
        if (!isEnabled()) return;
        ConfigurationSection posSection = plugin.getConfig().getConfigurationSection("racing.rewards.positions");
        if (posSection == null) return;

        int position = 1;
        for (Map.Entry<UUID, Long> entry : results) {
            UUID playerId = entry.getKey();
            long finishTimeMs = entry.getValue();
            Player player = Bukkit.getPlayer(playerId);
            if (player == null || !player.isOnline()) { position++; continue; }

            // Find the config section for this position, fall back to "default"
            ConfigurationSection reward = posSection.getConfigurationSection(String.valueOf(position));
            if (reward == null) {
                reward = posSection.getConfigurationSection("default");
            }
            if (reward == null) { position++; continue; }

            String playerName = player.getName();
            String timeFormatted = formatTime(finishTimeMs);

            // Execute console commands
            List<String> commands = reward.getStringList("commands");
            for (String cmd : commands) {
                String resolved = applyPlaceholders(cmd, playerName, position, timeFormatted, trackName, totalLaps);
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), resolved);
            }

            // Send personal messages
            List<String> messages = reward.getStringList("messages");
            for (String msg : messages) {
                String resolved = applyPlaceholders(msg, playerName, position, timeFormatted, trackName, totalLaps);
                player.sendMessage(Text.colorize(resolved));
            }

            // Broadcast messages to all online players
            List<String> broadcasts = reward.getStringList("broadcast");
            for (String bc : broadcasts) {
                String resolved = applyPlaceholders(bc, playerName, position, timeFormatted, trackName, totalLaps);
                String colorized = Text.colorize(resolved);
                for (Player online : Bukkit.getOnlinePlayers()) {
                    online.sendMessage(colorized);
                }
            }

            position++;
        }
    }

    private String applyPlaceholders(String template, String playerName, int position, String time, String track, int laps) {
        return template
                .replace("{player}", playerName)
                .replace("{position}", String.valueOf(position))
                .replace("{time}", time)
                .replace("{track}", track)
                .replace("{laps}", String.valueOf(laps));
    }

    private static String formatTime(long millis) {
        long sec = millis / 1000;
        long ms = millis % 1000;
        long m = sec / 60;
        long s = sec % 60;
        return String.format("%d:%02d.%03d", m, s, ms);
    }
}
