package es.jaie55.boatracing.util;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Persistent practice-only stats per track and player.
 * Stored in practice-stats.yml to keep race stats separated from competitive data.
 */
public class PracticeStatsManager {
    public static final class PracticeUpdate {
        private final long valueMillis;
        private final Long previousBestMillis;
        private final long bestMillis;

        private PracticeUpdate(long valueMillis, Long previousBestMillis, long bestMillis) {
            this.valueMillis = valueMillis;
            this.previousBestMillis = previousBestMillis;
            this.bestMillis = bestMillis;
        }

        public long getValueMillis() {
            return valueMillis;
        }

        public Long getPreviousBestMillis() {
            return previousBestMillis;
        }

        public long getBestMillis() {
            return bestMillis;
        }

        public boolean isFirstRecord() {
            return previousBestMillis == null;
        }

        public boolean isImproved() {
            return previousBestMillis == null || valueMillis < previousBestMillis;
        }

        public long getImprovementMillis() {
            if (previousBestMillis == null) return 0L;
            return Math.max(0L, previousBestMillis - valueMillis);
        }

        public long getGapToBestMillis() {
            return Math.max(0L, valueMillis - bestMillis);
        }
    }

    private static final class PlayerPracticeStats {
        private Long bestLapMillis;
        private Long lastLapMillis;
        private Long bestRunMillis;
        private Long lastRunMillis;
        private final Map<Integer, Long> bestSectorMillis = new HashMap<>();
        private final Map<Integer, Long> lastSectorMillis = new HashMap<>();
    }

    private final es.jaie55.boatracing.BoatRacingPlugin plugin;
    private final File file;
    private YamlConfiguration cfg;

    // trackToken -> (playerId -> stats)
    private final Map<String, Map<UUID, PlayerPracticeStats>> data = new HashMap<>();

    public PracticeStatsManager(es.jaie55.boatracing.BoatRacingPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "practice-stats.yml");
        ensureFile();
        reload();
    }

    public synchronized void reload() {
        cfg = YamlConfiguration.loadConfiguration(file);
        data.clear();

        ConfigurationSection tracks = cfg.getConfigurationSection("tracks");
        if (tracks == null) return;

        for (String trackToken : tracks.getKeys(false)) {
            ConfigurationSection playersSec = tracks.getConfigurationSection(trackToken + ".players");
            if (playersSec == null) continue;

            Map<UUID, PlayerPracticeStats> perTrack = new HashMap<>();
            for (String uuidKey : playersSec.getKeys(false)) {
                UUID playerId;
                try {
                    playerId = UUID.fromString(uuidKey);
                } catch (IllegalArgumentException ignored) {
                    plugin.getLogger().finer("Ignoring invalid UUID in practice-stats.yml: " + uuidKey);
                    continue;
                }

                ConfigurationSection ps = playersSec.getConfigurationSection(uuidKey);
                if (ps == null) continue;

                PlayerPracticeStats stats = new PlayerPracticeStats();
                if (ps.contains("bestLapMillis")) stats.bestLapMillis = readNonNegative(ps.get("bestLapMillis"));
                if (ps.contains("lastLapMillis")) stats.lastLapMillis = readNonNegative(ps.get("lastLapMillis"));
                if (ps.contains("bestRunMillis")) stats.bestRunMillis = readNonNegative(ps.get("bestRunMillis"));
                if (ps.contains("lastRunMillis")) stats.lastRunMillis = readNonNegative(ps.get("lastRunMillis"));

                ConfigurationSection bestSectors = ps.getConfigurationSection("bestSectorMillis");
                if (bestSectors != null) {
                    readSectorMap(bestSectors, stats.bestSectorMillis);
                }

                ConfigurationSection lastSectors = ps.getConfigurationSection("lastSectorMillis");
                if (lastSectors != null) {
                    readSectorMap(lastSectors, stats.lastSectorMillis);
                }

                perTrack.put(playerId, stats);
            }

            data.put(normalizeTrackToken(trackToken), perTrack);
        }
    }

    public synchronized void save() {
        if (cfg == null) cfg = new YamlConfiguration();

        cfg.set("tracks", null);
        for (Map.Entry<String, Map<UUID, PlayerPracticeStats>> trackEntry : data.entrySet()) {
            String trackToken = trackEntry.getKey();
            for (Map.Entry<UUID, PlayerPracticeStats> playerEntry : trackEntry.getValue().entrySet()) {
                UUID playerId = playerEntry.getKey();
                PlayerPracticeStats stats = playerEntry.getValue();
                String base = "tracks." + trackToken + ".players." + playerId;

                cfg.set(base + ".bestLapMillis", stats.bestLapMillis);
                cfg.set(base + ".lastLapMillis", stats.lastLapMillis);
                cfg.set(base + ".bestRunMillis", stats.bestRunMillis);
                cfg.set(base + ".lastRunMillis", stats.lastRunMillis);

                cfg.set(base + ".bestSectorMillis", null);
                for (Map.Entry<Integer, Long> sec : stats.bestSectorMillis.entrySet()) {
                    cfg.set(base + ".bestSectorMillis." + sec.getKey(), sec.getValue());
                }

                cfg.set(base + ".lastSectorMillis", null);
                for (Map.Entry<Integer, Long> sec : stats.lastSectorMillis.entrySet()) {
                    cfg.set(base + ".lastSectorMillis." + sec.getKey(), sec.getValue());
                }
            }
        }

        try {
            cfg.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save practice-stats.yml: " + e.getMessage());
        }
    }

    public synchronized PracticeUpdate recordLap(UUID playerId, String trackToken, long lapMillis) {
        if (playerId == null || lapMillis < 0L) {
            return new PracticeUpdate(Math.max(0L, lapMillis), null, Math.max(0L, lapMillis));
        }
        PlayerPracticeStats stats = ensurePlayerStats(playerId, trackToken);
        Long previousBest = stats.bestLapMillis;
        stats.lastLapMillis = lapMillis;
        if (previousBest == null || lapMillis < previousBest) {
            stats.bestLapMillis = lapMillis;
        }
        long best = stats.bestLapMillis != null ? stats.bestLapMillis : lapMillis;
        save();
        return new PracticeUpdate(lapMillis, previousBest, best);
    }

    public synchronized PracticeUpdate recordRun(UUID playerId, String trackToken, long runMillis) {
        if (playerId == null || runMillis < 0L) {
            return new PracticeUpdate(Math.max(0L, runMillis), null, Math.max(0L, runMillis));
        }
        PlayerPracticeStats stats = ensurePlayerStats(playerId, trackToken);
        Long previousBest = stats.bestRunMillis;
        stats.lastRunMillis = runMillis;
        if (previousBest == null || runMillis < previousBest) {
            stats.bestRunMillis = runMillis;
        }
        long best = stats.bestRunMillis != null ? stats.bestRunMillis : runMillis;
        save();
        return new PracticeUpdate(runMillis, previousBest, best);
    }

    public synchronized PracticeUpdate recordSector(UUID playerId, String trackToken, int sectionIndex, long sectorMillis) {
        if (playerId == null || sectionIndex <= 0 || sectorMillis < 0L) {
            return new PracticeUpdate(Math.max(0L, sectorMillis), null, Math.max(0L, sectorMillis));
        }
        PlayerPracticeStats stats = ensurePlayerStats(playerId, trackToken);
        Long previousBest = stats.bestSectorMillis.get(sectionIndex);
        stats.lastSectorMillis.put(sectionIndex, sectorMillis);
        if (previousBest == null || sectorMillis < previousBest) {
            stats.bestSectorMillis.put(sectionIndex, sectorMillis);
        }
        Long bestObj = stats.bestSectorMillis.get(sectionIndex);
        long best = bestObj != null ? bestObj : sectorMillis;
        save();
        return new PracticeUpdate(sectorMillis, previousBest, best);
    }

    public synchronized Long getBestLap(UUID playerId, String trackToken) {
        PlayerPracticeStats stats = getPlayerStats(playerId, trackToken);
        return stats != null ? stats.bestLapMillis : null;
    }

    public synchronized Long getLastLap(UUID playerId, String trackToken) {
        PlayerPracticeStats stats = getPlayerStats(playerId, trackToken);
        return stats != null ? stats.lastLapMillis : null;
    }

    public synchronized Long getBestRun(UUID playerId, String trackToken) {
        PlayerPracticeStats stats = getPlayerStats(playerId, trackToken);
        return stats != null ? stats.bestRunMillis : null;
    }

    public synchronized Long getLastRun(UUID playerId, String trackToken) {
        PlayerPracticeStats stats = getPlayerStats(playerId, trackToken);
        return stats != null ? stats.lastRunMillis : null;
    }

    public synchronized Long getBestSector(UUID playerId, String trackToken, int sectionIndex) {
        if (sectionIndex <= 0) return null;
        PlayerPracticeStats stats = getPlayerStats(playerId, trackToken);
        return stats != null ? stats.bestSectorMillis.get(sectionIndex) : null;
    }

    public synchronized Long getLastSector(UUID playerId, String trackToken, int sectionIndex) {
        if (sectionIndex <= 0) return null;
        PlayerPracticeStats stats = getPlayerStats(playerId, trackToken);
        return stats != null ? stats.lastSectorMillis.get(sectionIndex) : null;
    }

    private PlayerPracticeStats ensurePlayerStats(UUID playerId, String trackToken) {
        String normalizedTrack = normalizeTrackToken(trackToken);
        Map<UUID, PlayerPracticeStats> perTrack = data.computeIfAbsent(normalizedTrack, k -> new HashMap<>());
        return perTrack.computeIfAbsent(playerId, k -> new PlayerPracticeStats());
    }

    private PlayerPracticeStats getPlayerStats(UUID playerId, String trackToken) {
        if (playerId == null) return null;
        Map<UUID, PlayerPracticeStats> perTrack = data.get(normalizeTrackToken(trackToken));
        if (perTrack == null) return null;
        return perTrack.get(playerId);
    }

    private void ensureFile() {
        if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().warning("Failed to create practice-stats.yml: " + e.getMessage());
            }
        }
    }

    private static String normalizeTrackToken(String token) {
        if (token == null || token.isBlank()) return "unsaved";
        return token.trim().replace(' ', '_').toLowerCase(Locale.ROOT);
    }

    private static Long readNonNegative(Object raw) {
        if (!(raw instanceof Number n)) return null;
        long value = n.longValue();
        return value >= 0L ? value : null;
    }

    private static void readSectorMap(ConfigurationSection sec, Map<Integer, Long> out) {
        for (String key : sec.getKeys(false)) {
            int idx;
            try {
                idx = Integer.parseInt(key);
            } catch (NumberFormatException ignored) {
                continue;
            }
            if (idx <= 0) continue;
            long value = sec.getLong(key, -1L);
            if (value >= 0L) out.put(idx, value);
        }
    }
}
