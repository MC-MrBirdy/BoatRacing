package es.jaie55.boatracing.util;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Persistent aggregate stats used by placeholders and holograms.
 */
public class StatsManager {
    private final es.jaie55.boatracing.BoatRacingPlugin plugin;
    private final File file;
    private YamlConfiguration cfg;

    private final Map<UUID, Integer> playerWins = new HashMap<>();
    private final Map<UUID, Integer> teamWins = new HashMap<>();
    private final Map<UUID, Long> playerBestRace = new HashMap<>();
    private final Map<UUID, Long> playerBestLap = new HashMap<>();

    public StatsManager(es.jaie55.boatracing.BoatRacingPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "stats.yml");
        ensureFile();
        reload();
    }

    public void reload() {
        cfg = YamlConfiguration.loadConfiguration(file);
        playerWins.clear();
        teamWins.clear();
        playerBestRace.clear();
        playerBestLap.clear();

        readIntMap("playerWins", playerWins);
        readIntMap("teamWins", teamWins);
        readLongMap("playerBestRace", playerBestRace);
        readLongMap("playerBestLap", playerBestLap);
    }

    public void save() {
        if (cfg == null) cfg = new YamlConfiguration();
        writeIntMap("playerWins", playerWins);
        writeIntMap("teamWins", teamWins);
        writeLongMap("playerBestRace", playerBestRace);
        writeLongMap("playerBestLap", playerBestLap);
        try {
            cfg.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save stats.yml: " + e.getMessage());
        }
    }

    public int getPlayerWins(UUID playerId) {
        return playerWins.getOrDefault(playerId, 0);
    }

    public int getTeamWins(UUID teamId) {
        return teamWins.getOrDefault(teamId, 0);
    }

    public Long getPlayerBestRace(UUID playerId) {
        return playerBestRace.get(playerId);
    }

    public Long getPlayerBestLap(UUID playerId) {
        return playerBestLap.get(playerId);
    }

    public void addPlayerWin(UUID playerId) {
        if (playerId == null) return;
        playerWins.merge(playerId, 1, Integer::sum);
        save();
    }

    public void addTeamWin(UUID teamId) {
        if (teamId == null) return;
        teamWins.merge(teamId, 1, Integer::sum);
        save();
    }

    public void updatePlayerBestRace(UUID playerId, long millis) {
        if (playerId == null || millis < 0) return;
        Long cur = playerBestRace.get(playerId);
        if (cur == null || millis < cur) {
            playerBestRace.put(playerId, millis);
            save();
        }
    }

    public void updatePlayerBestLap(UUID playerId, long millis) {
        if (playerId == null || millis < 0) return;
        Long cur = playerBestLap.get(playerId);
        if (cur == null || millis < cur) {
            playerBestLap.put(playerId, millis);
            save();
        }
    }

    public Optional<Map.Entry<UUID, Integer>> topPlayerByWins() {
        return playerWins.entrySet().stream().max(Map.Entry.comparingByValue());
    }

    public Optional<Map.Entry<UUID, Integer>> topTeamByWins() {
        return teamWins.entrySet().stream().max(Map.Entry.comparingByValue());
    }

    public Optional<Map.Entry<UUID, Long>> topPlayerByBestRace() {
        return playerBestRace.entrySet().stream().min(Map.Entry.comparingByValue());
    }

    public Optional<Map.Entry<UUID, Long>> topPlayerByBestLap() {
        return playerBestLap.entrySet().stream().min(Map.Entry.comparingByValue());
    }

    private void ensureFile() {
        if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().warning("Failed to create stats.yml: " + e.getMessage());
            }
        }
    }

    private void readIntMap(String path, Map<UUID, Integer> out) {
        ConfigurationSection sec = cfg.getConfigurationSection(path);
        if (sec == null) return;
        for (String key : sec.getKeys(false)) {
            try {
                UUID id = UUID.fromString(key);
                out.put(id, sec.getInt(key, 0));
            } catch (IllegalArgumentException ignored) {
                plugin.getLogger().finer("Ignoring invalid UUID in stats.yml at " + path + "." + key);
            }
        }
    }

    private void readLongMap(String path, Map<UUID, Long> out) {
        ConfigurationSection sec = cfg.getConfigurationSection(path);
        if (sec == null) return;
        for (String key : sec.getKeys(false)) {
            try {
                UUID id = UUID.fromString(key);
                out.put(id, sec.getLong(key, -1L));
            } catch (IllegalArgumentException ignored) {
                plugin.getLogger().finer("Ignoring invalid UUID in stats.yml at " + path + "." + key);
            }
        }
    }

    private void writeIntMap(String path, Map<UUID, Integer> in) {
        cfg.set(path, null);
        for (Map.Entry<UUID, Integer> e : in.entrySet()) {
            cfg.set(path + "." + e.getKey(), e.getValue());
        }
    }

    private void writeLongMap(String path, Map<UUID, Long> in) {
        cfg.set(path, null);
        for (Map.Entry<UUID, Long> e : in.entrySet()) {
            cfg.set(path + "." + e.getKey(), e.getValue());
        }
    }
}
