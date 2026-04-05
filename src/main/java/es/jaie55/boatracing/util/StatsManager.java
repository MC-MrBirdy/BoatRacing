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
    private final Map<UUID, Map<Integer, Integer>> playerPositions = new HashMap<>();
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
        playerPositions.clear();
        playerBestRace.clear();
        playerBestLap.clear();

        readIntMap("playerWins", playerWins);
        readIntMap("teamWins", teamWins);
        readPositionMap("playerPositions", playerPositions);
        readLongMap("playerBestRace", playerBestRace);
        readLongMap("playerBestLap", playerBestLap);

        // Legacy compatibility: if no explicit position map exists yet,
        // bootstrap 1st-place counts from historical player wins.
        if (playerPositions.isEmpty() && !playerWins.isEmpty()) {
            for (Map.Entry<UUID, Integer> e : playerWins.entrySet()) {
                int wins = e.getValue() == null ? 0 : e.getValue();
                if (wins <= 0) continue;
                Map<Integer, Integer> per = new HashMap<>();
                per.put(1, wins);
                playerPositions.put(e.getKey(), per);
            }
        }
    }

    public void save() {
        if (cfg == null) cfg = new YamlConfiguration();
        writeIntMap("playerWins", playerWins);
        writeIntMap("teamWins", teamWins);
        writePositionMap("playerPositions", playerPositions);
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

    public Map<Integer, Integer> getPlayerPositions(UUID playerId) {
        if (playerId == null) return Collections.emptyMap();

        Map<Integer, Integer> per = playerPositions.get(playerId);
        TreeMap<Integer, Integer> out = new TreeMap<>();
        if (per != null) {
            for (Map.Entry<Integer, Integer> e : per.entrySet()) {
                Integer pos = e.getKey();
                Integer count = e.getValue();
                if (pos == null || count == null || pos < 1 || count < 1) continue;
                out.put(pos, count);
            }
        }

        if (!out.isEmpty()) return Collections.unmodifiableMap(out);

        // Extra legacy fallback when position map is still empty for this player.
        int wins = playerWins.getOrDefault(playerId, 0);
        if (wins > 0) {
            out.put(1, wins);
            return Collections.unmodifiableMap(out);
        }
        return Collections.emptyMap();
    }

    public void recordPlayerPositions(List<UUID> orderedPlayersByFinish) {
        if (orderedPlayersByFinish == null || orderedPlayersByFinish.isEmpty()) return;

        boolean changed = false;
        int position = 1;
        for (UUID playerId : orderedPlayersByFinish) {
            if (playerId != null) {
                Map<Integer, Integer> per = playerPositions.computeIfAbsent(playerId, k -> new HashMap<>());
                per.merge(position, 1, Integer::sum);
                changed = true;
            }
            position++;
        }

        if (changed) save();
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

    private void readPositionMap(String path, Map<UUID, Map<Integer, Integer>> out) {
        ConfigurationSection root = cfg.getConfigurationSection(path);
        if (root == null) return;

        for (String playerKey : root.getKeys(false)) {
            UUID playerId;
            try {
                playerId = UUID.fromString(playerKey);
            } catch (IllegalArgumentException ignored) {
                plugin.getLogger().finer("Ignoring invalid UUID in stats.yml at " + path + "." + playerKey);
                continue;
            }

            ConfigurationSection perSec = root.getConfigurationSection(playerKey);
            if (perSec == null) continue;

            Map<Integer, Integer> per = new HashMap<>();
            for (String posKey : perSec.getKeys(false)) {
                int pos;
                try {
                    pos = Integer.parseInt(posKey);
                } catch (NumberFormatException ignored) {
                    continue;
                }
                if (pos < 1) continue;
                int count = perSec.getInt(posKey, 0);
                if (count > 0) per.put(pos, count);
            }
            if (!per.isEmpty()) out.put(playerId, per);
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

    private void writePositionMap(String path, Map<UUID, Map<Integer, Integer>> in) {
        cfg.set(path, null);
        for (Map.Entry<UUID, Map<Integer, Integer>> playerEntry : in.entrySet()) {
            UUID playerId = playerEntry.getKey();
            Map<Integer, Integer> per = playerEntry.getValue();
            if (playerId == null || per == null || per.isEmpty()) continue;

            for (Map.Entry<Integer, Integer> posEntry : per.entrySet()) {
                Integer pos = posEntry.getKey();
                Integer count = posEntry.getValue();
                if (pos == null || count == null || pos < 1 || count < 1) continue;
                cfg.set(path + "." + playerId + "." + pos, count);
            }
        }
    }
}
