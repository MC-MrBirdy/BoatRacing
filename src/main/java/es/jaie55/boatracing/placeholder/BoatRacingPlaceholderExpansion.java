package es.jaie55.boatracing.placeholder;

import es.jaie55.boatracing.BoatRacingPlugin;
import es.jaie55.boatracing.race.RaceManager;
import es.jaie55.boatracing.team.Team;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

public class BoatRacingPlaceholderExpansion extends PlaceholderExpansion {
    private final BoatRacingPlugin plugin;
    private static final long TRACK_FILE_BEST_CACHE_TTL_MS = 3000L;
    private final Map<String, CachedTrackBest> trackFileBestCache = new HashMap<>();
    private final Map<String, CachedTrackTop> trackFileTopCache = new HashMap<>();

    private static final class CachedTrackBest {
        private final long loadedAtMs;
        private final Optional<Map.Entry<UUID, Long>> value;

        private CachedTrackBest(long loadedAtMs, Optional<Map.Entry<UUID, Long>> value) {
            this.loadedAtMs = loadedAtMs;
            this.value = value;
        }
    }

    private static final class CachedTrackTop {
        private final long loadedAtMs;
        private final List<Map.Entry<UUID, Long>> value;

        private CachedTrackTop(long loadedAtMs, List<Map.Entry<UUID, Long>> value) {
            this.loadedAtMs = loadedAtMs;
            this.value = value;
        }
    }

    public BoatRacingPlaceholderExpansion(BoatRacingPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "boatracing";
    }

    @Override
    public String getAuthor() {
        return String.join(", ", plugin.getDescription().getAuthors());
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        if (params == null || params.isEmpty()) return "";

        String key = params.toLowerCase(Locale.ROOT);
        final String trackRaceRunningPrefix = "track_race_running_";
        final String trackRaceRunningCompatPrefix = "track_racerunning_";
        final String trackRaceRegisteringPrefix = "track_race_registering_";
        final String trackRaceRegisteringCompatPrefix = "track_raceregistering_";
        final String trackRaceStatusPrefix = "track_race_status_";

        if (key.equals("teams_count")) return String.valueOf(plugin.getTeamManager().getTeams().size());
        if (key.equals("teams_list")) return plugin.getTeamManager().getTeams().stream().map(Team::getName).sorted(String.CASE_INSENSITIVE_ORDER).collect(Collectors.joining(", "));

        if (key.equals("top_player_wins_name")) return plugin.getStatsManager().topPlayerByWins().map(e -> safePlayerName(e.getKey())).orElse("-");
        if (key.equals("top_player_wins")) return plugin.getStatsManager().topPlayerByWins().map(e -> String.valueOf(e.getValue())).orElse("0");
        if (key.equals("top_team_wins_name")) return plugin.getStatsManager().topTeamByWins().map(e -> plugin.getTeamManager().getTeams().stream().filter(t -> t.getId().equals(e.getKey())).findFirst().map(Team::getName).orElse("-")).orElse("-");
        if (key.equals("top_team_wins")) return plugin.getStatsManager().topTeamByWins().map(e -> String.valueOf(e.getValue())).orElse("0");
        if (key.equals("top_player_best_race_name")) return plugin.getStatsManager().topPlayerByBestRace().map(e -> safePlayerName(e.getKey())).orElse("-");
        if (key.equals("top_player_best_race")) return plugin.getStatsManager().topPlayerByBestRace().map(e -> formatMillis(e.getValue())).orElse("-");
        if (key.equals("top_player_best_lap_name")) return plugin.getStatsManager().topPlayerByBestLap().map(e -> safePlayerName(e.getKey())).orElse("-");
        if (key.equals("top_player_best_lap")) return plugin.getStatsManager().topPlayerByBestLap().map(e -> formatMillis(e.getValue())).orElse("-");

        if (key.equals("track_name")) {
            String current = plugin.getTrackLibrary() != null ? plugin.getTrackLibrary().getCurrent() : null;
            return current != null ? current : "unsaved";
        }
        if (key.equals("track_best_player")) return trackBestEntry().map(e -> safePlayerName(e.getKey())).orElse("-");
        if (key.equals("track_best_time")) return trackBestEntry().map(e -> formatMillis(e.getValue())).orElse("-");
        if (key.startsWith("track_best_player_")) {
            String token = params.substring("track_best_player_".length());
            return trackBestEntryForTrackToken(token).map(e -> safePlayerName(e.getKey())).orElse("-");
        }
        if (key.startsWith("track_best_time_ms_")) {
            String token = params.substring("track_best_time_ms_".length());
            return trackBestEntryForTrackToken(token).map(e -> String.valueOf(e.getValue())).orElse("-1");
        }
        if (key.startsWith("track_best_time_")) {
            String token = params.substring("track_best_time_".length());
            return trackBestEntryForTrackToken(token).map(e -> formatMillis(e.getValue())).orElse("-");
        }
        String trackTopResolved = resolveTrackTopPlaceholder(key, params);
        if (trackTopResolved != null) return trackTopResolved;
        if (key.startsWith(trackRaceRunningPrefix)) {
            String token = params.substring(trackRaceRunningPrefix.length());
            RaceManager rm = getRaceManagerForTrackToken(token);
            return String.valueOf(rm != null && rm.isRunning());
        }
        if (key.startsWith(trackRaceRunningCompatPrefix)) {
            String token = params.substring(trackRaceRunningCompatPrefix.length());
            RaceManager rm = getRaceManagerForTrackToken(token);
            return String.valueOf(rm != null && rm.isRunning());
        }
        if (key.startsWith(trackRaceRegisteringPrefix)) {
            String token = params.substring(trackRaceRegisteringPrefix.length());
            RaceManager rm = getRaceManagerForTrackToken(token);
            return String.valueOf(rm != null && rm.isRegistering());
        }
        if (key.startsWith(trackRaceRegisteringCompatPrefix)) {
            String token = params.substring(trackRaceRegisteringCompatPrefix.length());
            RaceManager rm = getRaceManagerForTrackToken(token);
            return String.valueOf(rm != null && rm.isRegistering());
        }
        if (key.startsWith(trackRaceStatusPrefix)) {
            String token = params.substring(trackRaceStatusPrefix.length());
            RaceManager rm = getRaceManagerForTrackToken(token);
            if (rm == null) return "idle";
            if (rm.isRunning()) return "running";
            if (rm.isRegistering()) return "registering";
            return "idle";
        }

        // Targeted player placeholders (by name/uuid), useful for static NPC/holograms:
        // %boatracing_player_wins_<player>%
        // %boatracing_player_best_race_<player>%
        // %boatracing_player_best_lap_<player>%
        // %boatracing_player_track_best_<player>%
        // %boatracing_player_team_name_<player>%
        // %boatracing_player_team_wins_<player>%
        // %boatracing_player_number_<player>%
        // %boatracing_player_boat_<player>%
        if (key.startsWith("player_name_")) {
            UUID target = resolvePlayerId(params.substring("player_name_".length()));
            return target == null ? "-" : safePlayerName(target);
        }
        if (key.startsWith("player_wins_")) {
            UUID target = resolvePlayerId(params.substring("player_wins_".length()));
            return target == null ? "0" : String.valueOf(plugin.getStatsManager().getPlayerWins(target));
        }
        if (key.startsWith("player_best_race_")) {
            UUID target = resolvePlayerId(params.substring("player_best_race_".length()));
            if (target == null) return "-";
            Long v = plugin.getStatsManager().getPlayerBestRace(target);
            return v == null ? "-" : formatMillis(v);
        }
        if (key.startsWith("player_best_race_ms_")) {
            UUID target = resolvePlayerId(params.substring("player_best_race_ms_".length()));
            if (target == null) return "-1";
            Long v = plugin.getStatsManager().getPlayerBestRace(target);
            return v == null ? "-1" : String.valueOf(v);
        }
        if (key.startsWith("player_best_lap_")) {
            UUID target = resolvePlayerId(params.substring("player_best_lap_".length()));
            if (target == null) return "-";
            Long v = plugin.getStatsManager().getPlayerBestLap(target);
            return v == null ? "-" : formatMillis(v);
        }
        if (key.startsWith("player_best_lap_ms_")) {
            UUID target = resolvePlayerId(params.substring("player_best_lap_ms_".length()));
            if (target == null) return "-1";
            Long v = plugin.getStatsManager().getPlayerBestLap(target);
            return v == null ? "-1" : String.valueOf(v);
        }
        if (key.startsWith("player_track_best_")) {
            UUID target = resolvePlayerId(params.substring("player_track_best_".length()));
            if (target == null) return "-";
            Long v = plugin.getTrackConfig().getBestTime(target);
            return v == null ? "-" : formatMillis(v);
        }
        if (key.startsWith("player_track_best_ms_")) {
            UUID target = resolvePlayerId(params.substring("player_track_best_ms_".length()));
            if (target == null) return "-1";
            Long v = plugin.getTrackConfig().getBestTime(target);
            return v == null ? "-1" : String.valueOf(v);
        }
        if (key.startsWith("player_team_name_")) {
            UUID target = resolvePlayerId(params.substring("player_team_name_".length()));
            if (target == null) return "-";
            Team t = plugin.getTeamManager().getTeamByMember(target).orElse(null);
            return t == null ? "-" : t.getName();
        }
        if (key.startsWith("player_team_id_")) {
            UUID target = resolvePlayerId(params.substring("player_team_id_".length()));
            if (target == null) return "-";
            Team t = plugin.getTeamManager().getTeamByMember(target).orElse(null);
            return t == null ? "-" : t.getId().toString();
        }
        if (key.startsWith("player_team_color_")) {
            UUID target = resolvePlayerId(params.substring("player_team_color_".length()));
            if (target == null) return "-";
            Team t = plugin.getTeamManager().getTeamByMember(target).orElse(null);
            return t == null ? "-" : t.getColor().name();
        }
        if (key.startsWith("player_team_leader_name_")) {
            UUID target = resolvePlayerId(params.substring("player_team_leader_name_".length()));
            if (target == null) return "-";
            Team t = plugin.getTeamManager().getTeamByMember(target).orElse(null);
            return t == null || t.getLeader() == null ? "-" : safePlayerName(t.getLeader());
        }
        if (key.startsWith("player_team_leader_id_")) {
            UUID target = resolvePlayerId(params.substring("player_team_leader_id_".length()));
            if (target == null) return "-";
            Team t = plugin.getTeamManager().getTeamByMember(target).orElse(null);
            return t == null || t.getLeader() == null ? "-" : t.getLeader().toString();
        }
        if (key.startsWith("player_team_wins_")) {
            UUID target = resolvePlayerId(params.substring("player_team_wins_".length()));
            if (target == null) return "0";
            Team t = plugin.getTeamManager().getTeamByMember(target).orElse(null);
            return t == null ? "0" : String.valueOf(plugin.getStatsManager().getTeamWins(t.getId()));
        }
        if (key.startsWith("player_number_")) {
            UUID target = resolvePlayerId(params.substring("player_number_".length()));
            if (target == null) return "0";
            Team t = plugin.getTeamManager().getTeamByMember(target).orElse(null);
            return t == null ? "0" : String.valueOf(t.getRacerNumber(target));
        }
        if (key.startsWith("player_boat_")) {
            UUID target = resolvePlayerId(params.substring("player_boat_".length()));
            if (target == null) return "-";
            Team t = plugin.getTeamManager().getTeamByMember(target).orElse(null);
            return t == null ? "-" : t.getBoatType(target);
        }

        if (key.startsWith("team_players_")) {
            String token = params.substring("team_players_".length());
            Team t = resolveTeam(token);
            if (t == null) return "";
            return t.getMembers().stream().map(this::safePlayerName).collect(Collectors.joining(", "));
        }
        if (key.startsWith("team_player_count_")) {
            String token = params.substring("team_player_count_".length());
            Team t = resolveTeam(token);
            return t == null ? "0" : String.valueOf(t.getMembers().size());
        }
        if (key.startsWith("team_leader_name_")) {
            String token = params.substring("team_leader_name_".length());
            Team t = resolveTeam(token);
            return t == null || t.getLeader() == null ? "-" : safePlayerName(t.getLeader());
        }
        if (key.startsWith("team_leader_id_")) {
            String token = params.substring("team_leader_id_".length());
            Team t = resolveTeam(token);
            return t == null || t.getLeader() == null ? "-" : t.getLeader().toString();
        }
        if (key.startsWith("team_wins_")) {
            String token = params.substring("team_wins_".length());
            Team t = resolveTeam(token);
            return t == null ? "0" : String.valueOf(plugin.getStatsManager().getTeamWins(t.getId()));
        }

        if (player == null) return "";
        UUID pid = player.getUniqueId();
        Team team = plugin.getTeamManager().getTeamByMember(pid).orElse(null);
        RaceManager playerRace = plugin.getRaceManagerForPlayer(pid);

        if (key.equals("player_name")) return safePlayerName(pid);
        if (key.equals("player_team_name")) return team != null ? team.getName() : "-";
        if (key.equals("player_team_id")) return team != null ? team.getId().toString() : "-";
        if (key.equals("player_team_color")) return team != null ? team.getColor().name() : "-";
        if (key.equals("player_team_leader_name")) return team != null && team.getLeader() != null ? safePlayerName(team.getLeader()) : "-";
        if (key.equals("player_team_leader_id")) return team != null && team.getLeader() != null ? team.getLeader().toString() : "-";
        if (key.equals("player_team_players")) return team != null ? team.getMembers().stream().map(this::safePlayerName).collect(Collectors.joining(", ")) : "";
        if (key.equals("player_team_player_count")) return team != null ? String.valueOf(team.getMembers().size()) : "0";
        if (key.equals("player_number")) return team != null ? String.valueOf(team.getRacerNumber(pid)) : "0";
        if (key.equals("player_boat")) return team != null ? team.getBoatType(pid) : "-";

        if (key.equals("player_wins")) return String.valueOf(plugin.getStatsManager().getPlayerWins(pid));
        if (key.equals("player_team_wins")) return team != null ? String.valueOf(plugin.getStatsManager().getTeamWins(team.getId())) : "0";

        if (key.equals("player_best_race")) {
            Long v = plugin.getStatsManager().getPlayerBestRace(pid);
            return v == null ? "-" : formatMillis(v);
        }
        if (key.equals("player_best_race_ms")) {
            Long v = plugin.getStatsManager().getPlayerBestRace(pid);
            return v == null ? "-1" : String.valueOf(v);
        }
        if (key.equals("player_best_lap")) {
            Long v = plugin.getStatsManager().getPlayerBestLap(pid);
            return v == null ? "-" : formatMillis(v);
        }
        if (key.equals("player_best_lap_ms")) {
            Long v = plugin.getStatsManager().getPlayerBestLap(pid);
            return v == null ? "-1" : String.valueOf(v);
        }

        if (key.equals("player_track_best")) {
            Long v = plugin.getTrackConfig().getBestTime(pid);
            return v == null ? "-" : formatMillis(v);
        }
        if (key.equals("player_track_best_ms")) {
            Long v = plugin.getTrackConfig().getBestTime(pid);
            return v == null ? "-1" : String.valueOf(v);
        }

        if (key.equals("player_race_running")) return String.valueOf(playerRace != null && playerRace.isRunning() && playerRace.isParticipant(pid));
        if (key.equals("player_race_registering")) return String.valueOf(playerRace != null && playerRace.isRegistering() && playerRace.getRegistered().contains(pid));

        long liveMs = playerRace != null ? playerRace.getLiveTimeMillis(pid) : -1L;
        if (key.equals("player_current_time")) return formatMillis(liveMs);
        if (key.equals("player_current_time_ms")) return String.valueOf(liveMs);
        if (key.equals("player_current_lap")) return String.valueOf(playerRace != null ? playerRace.getLiveLap(pid) : 0);
        if (key.equals("player_current_checkpoint")) return String.valueOf(playerRace != null ? playerRace.getLiveCheckpoint(pid) : 0);
        if (key.equals("player_current_position")) return String.valueOf(playerRace != null ? playerRace.getLivePosition(pid) : 0);
        if (key.equals("player_current_pitstops")) return String.valueOf(playerRace != null ? playerRace.getLivePitstops(pid) : 0);
        if (key.equals("player_finished")) return String.valueOf(playerRace != null && playerRace.isLiveFinished(pid));

        return null;
    }

    private Team resolveTeam(String token) {
        if (token == null || token.isBlank()) return null;
        String raw = token.trim();
        String underscored = raw.replace('_', ' ');
        return plugin.getTeamManager().getTeams().stream()
                .filter(t -> t.getName().equalsIgnoreCase(raw) || t.getName().equalsIgnoreCase(underscored) || t.getName().replace(' ', '_').equalsIgnoreCase(raw))
                .findFirst().orElse(null);
    }

    private UUID resolvePlayerId(String token) {
        if (token == null || token.isBlank()) return null;
        String raw = token.trim();
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ignored) {
            // not a UUID
        }
        Player online = Bukkit.getPlayerExact(raw);
        if (online != null) return online.getUniqueId();
        String withSpaces = raw.replace('_', ' ');
        for (OfflinePlayer op : Bukkit.getOfflinePlayers()) {
            String n = op.getName();
            if (n == null) continue;
            if (n.equalsIgnoreCase(raw) || n.equalsIgnoreCase(withSpaces)) {
                return op.getUniqueId();
            }
        }
        return null;
    }

    private RaceManager getRaceManagerForTrackToken(String token) {
        String requested = normalizeTrackToken(token);
        if (requested.isEmpty()) return null;

        for (RaceManager rm : plugin.getAllRaceManagers()) {
            if (rm == null) continue;
            if (normalizeTrackToken(rm.getTrackName()).equalsIgnoreCase(requested)) return rm;
        }

        // Backward compatibility for placeholders explicitly targeting the in-memory unsaved track.
        if (requested.equalsIgnoreCase("unsaved")) return plugin.getRaceManager();
        return null;
    }

    private static String normalizeTrackToken(String value) {
        if (value == null) return "";
        return value.trim().replace(' ', '_');
    }

    private Optional<Map.Entry<UUID, Long>> trackBestEntry() {
        return trackBestEntry(plugin.getTrackConfig());
    }

    private String resolveTrackTopPlaceholder(String key, String params) {
        for (int rank = 1; rank <= 3; rank++) {
            String playerPrefix = "track_top_" + rank + "_player_";
            if (key.startsWith(playerPrefix)) {
                String token = params.substring(playerPrefix.length());
                return trackTopEntryForTrackToken(token, rank).map(e -> safePlayerName(e.getKey())).orElse("-");
            }

            String timeMsPrefix = "track_top_" + rank + "_time_ms_";
            if (key.startsWith(timeMsPrefix)) {
                String token = params.substring(timeMsPrefix.length());
                return trackTopEntryForTrackToken(token, rank).map(e -> String.valueOf(e.getValue())).orElse("-1");
            }

            String timePrefix = "track_top_" + rank + "_time_";
            if (key.startsWith(timePrefix)) {
                String token = params.substring(timePrefix.length());
                return trackTopEntryForTrackToken(token, rank).map(e -> formatMillis(e.getValue())).orElse("-");
            }
        }
        return null;
    }

    private Optional<Map.Entry<UUID, Long>> trackBestEntryForTrackToken(String token) {
        String requested = normalizeTrackToken(token);
        if (requested.isEmpty()) return Optional.empty();

        String current = plugin.getTrackLibrary() != null ? plugin.getTrackLibrary().getCurrent() : null;
        if (requested.equalsIgnoreCase("unsaved") || (current != null && normalizeTrackToken(current).equalsIgnoreCase(requested))) {
            return trackBestEntry(plugin.getTrackConfig());
        }

        RaceManager rm = plugin.getRaceManagerByTrack(requested);
        if (rm != null) return trackBestEntry(rm.getTrack());

        if (plugin.getTrackLibrary() == null || !plugin.getTrackLibrary().exists(requested)) return Optional.empty();

        String cacheKey = requested.toLowerCase(Locale.ROOT);
        long now = System.currentTimeMillis();
        CachedTrackBest cached = trackFileBestCache.get(cacheKey);
        if (cached != null && (now - cached.loadedAtMs) <= TRACK_FILE_BEST_CACHE_TTL_MS) return cached.value;

        Optional<Map.Entry<UUID, Long>> loaded = loadTrackBestEntryFromFile(requested);
        trackFileBestCache.put(cacheKey, new CachedTrackBest(now, loaded));
        return loaded;
    }

    private Optional<Map.Entry<UUID, Long>> trackTopEntryForTrackToken(String token, int rank) {
        if (rank < 1) return Optional.empty();
        List<Map.Entry<UUID, Long>> top = trackTopEntriesForTrackToken(token, rank);
        return top.size() >= rank ? Optional.of(top.get(rank - 1)) : Optional.empty();
    }

    private List<Map.Entry<UUID, Long>> trackTopEntriesForTrackToken(String token, int limit) {
        if (limit <= 0) return Collections.emptyList();
        String requested = normalizeTrackToken(token);
        if (requested.isEmpty()) return Collections.emptyList();

        String current = plugin.getTrackLibrary() != null ? plugin.getTrackLibrary().getCurrent() : null;
        if (requested.equalsIgnoreCase("unsaved") || (current != null && normalizeTrackToken(current).equalsIgnoreCase(requested))) {
            return trackTopEntries(plugin.getTrackConfig(), limit);
        }

        RaceManager rm = plugin.getRaceManagerByTrack(requested);
        if (rm != null) return trackTopEntries(rm.getTrack(), limit);

        if (plugin.getTrackLibrary() == null || !plugin.getTrackLibrary().exists(requested)) return Collections.emptyList();

        String cacheKey = requested.toLowerCase(Locale.ROOT);
        long now = System.currentTimeMillis();
        CachedTrackTop cached = trackFileTopCache.get(cacheKey);
        if (cached != null && (now - cached.loadedAtMs) <= TRACK_FILE_BEST_CACHE_TTL_MS) {
            return cached.value.stream().limit(limit).collect(Collectors.toList());
        }

        List<Map.Entry<UUID, Long>> loaded = loadTrackTopEntriesFromFile(requested, 3);
        trackFileTopCache.put(cacheKey, new CachedTrackTop(now, loaded));
        return loaded.stream().limit(limit).collect(Collectors.toList());
    }

    private Optional<Map.Entry<UUID, Long>> loadTrackBestEntryFromFile(String trackToken) {
        java.io.File trackFile = new java.io.File(new java.io.File(plugin.getDataFolder(), "tracks"), trackToken + ".yml");
        if (!trackFile.exists()) return Optional.empty();

        es.jaie55.boatracing.track.TrackConfig cfg = new es.jaie55.boatracing.track.TrackConfig(plugin.getDataFolder());
        cfg.setBackingFile(trackFile);
        return trackBestEntry(cfg);
    }

    private List<Map.Entry<UUID, Long>> loadTrackTopEntriesFromFile(String trackToken, int limit) {
        java.io.File trackFile = new java.io.File(new java.io.File(plugin.getDataFolder(), "tracks"), trackToken + ".yml");
        if (!trackFile.exists()) return Collections.emptyList();

        es.jaie55.boatracing.track.TrackConfig cfg = new es.jaie55.boatracing.track.TrackConfig(plugin.getDataFolder());
        cfg.setBackingFile(trackFile);
        return trackTopEntries(cfg, limit);
    }

    private Optional<Map.Entry<UUID, Long>> trackBestEntry(es.jaie55.boatracing.track.TrackConfig trackConfig) {
        if (trackConfig == null) return Optional.empty();
        return trackConfig.getBestTimes().entrySet().stream()
                .map(e -> {
                    try {
                        return Map.entry(UUID.fromString(e.getKey()), e.getValue());
                    } catch (IllegalArgumentException ignored) {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .min(Map.Entry.comparingByValue());
    }

    private List<Map.Entry<UUID, Long>> trackTopEntries(es.jaie55.boatracing.track.TrackConfig trackConfig, int limit) {
        if (trackConfig == null || limit <= 0) return Collections.emptyList();
        return trackConfig.getBestTimes().entrySet().stream()
                .map(e -> {
                    try {
                        return Map.entry(UUID.fromString(e.getKey()), e.getValue());
                    } catch (IllegalArgumentException ignored) {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .sorted(Map.Entry.comparingByValue())
                .limit(limit)
                .collect(Collectors.toList());
    }

    private String safePlayerName(UUID id) {
        Player online = Bukkit.getPlayer(id);
        if (online != null) return online.getName();
        OfflinePlayer op = Bukkit.getOfflinePlayer(id);
        return op.getName() != null ? op.getName() : id.toString().substring(0, 8);
    }

    private static String formatMillis(long millis) {
        if (millis < 0L) return "-";
        long sec = millis / 1000;
        long ms = millis % 1000;
        long min = sec / 60;
        long s = sec % 60;
        return String.format("%d:%02d.%03d", min, s, ms);
    }
}
