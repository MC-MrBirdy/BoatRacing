package es.jaie55.boatracing.placeholder;

import es.jaie55.boatracing.BoatRacingPlugin;
import es.jaie55.boatracing.team.Team;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

public class BoatRacingPlaceholderExpansion extends PlaceholderExpansion {
    private final BoatRacingPlugin plugin;

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
        if (key.startsWith(trackRaceRunningPrefix)) {
            String token = params.substring(trackRaceRunningPrefix.length());
            return String.valueOf(isRequestedTrackActive(token) && plugin.getRaceManager().isRunning());
        }
        if (key.startsWith(trackRaceRunningCompatPrefix)) {
            String token = params.substring(trackRaceRunningCompatPrefix.length());
            return String.valueOf(isRequestedTrackActive(token) && plugin.getRaceManager().isRunning());
        }
        if (key.startsWith(trackRaceRegisteringPrefix)) {
            String token = params.substring(trackRaceRegisteringPrefix.length());
            return String.valueOf(isRequestedTrackActive(token) && plugin.getRaceManager().isRegistering());
        }
        if (key.startsWith(trackRaceRegisteringCompatPrefix)) {
            String token = params.substring(trackRaceRegisteringCompatPrefix.length());
            return String.valueOf(isRequestedTrackActive(token) && plugin.getRaceManager().isRegistering());
        }
        if (key.startsWith(trackRaceStatusPrefix)) {
            String token = params.substring(trackRaceStatusPrefix.length());
            if (!isRequestedTrackActive(token)) return "idle";
            if (plugin.getRaceManager().isRunning()) return "running";
            if (plugin.getRaceManager().isRegistering()) return "registering";
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

        if (key.equals("player_race_running")) return String.valueOf(plugin.getRaceManager().isRunning());
        if (key.equals("player_race_registering")) return String.valueOf(plugin.getRaceManager().isRegistering());
        if (key.equals("player_current_time")) return formatMillis(plugin.getRaceManager().getLiveTimeMillis(pid));
        if (key.equals("player_current_time_ms")) return String.valueOf(plugin.getRaceManager().getLiveTimeMillis(pid));
        if (key.equals("player_current_lap")) return String.valueOf(plugin.getRaceManager().getLiveLap(pid));
        if (key.equals("player_current_checkpoint")) return String.valueOf(plugin.getRaceManager().getLiveCheckpoint(pid));
        if (key.equals("player_current_position")) return String.valueOf(plugin.getRaceManager().getLivePosition(pid));
        if (key.equals("player_current_pitstops")) return String.valueOf(plugin.getRaceManager().getLivePitstops(pid));
        if (key.equals("player_finished")) return String.valueOf(plugin.getRaceManager().isLiveFinished(pid));

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

    private boolean isRequestedTrackActive(String token) {
        String requested = normalizeTrackToken(token);
        if (requested.isEmpty()) return false;
        String current = plugin.getTrackLibrary() != null ? plugin.getTrackLibrary().getCurrent() : null;
        if (current == null || current.isBlank()) current = "unsaved";
        return normalizeTrackToken(current).equalsIgnoreCase(requested);
    }

    private static String normalizeTrackToken(String value) {
        if (value == null) return "";
        return value.trim().replace(' ', '_');
    }

    private Optional<Map.Entry<UUID, Long>> trackBestEntry() {
        return plugin.getTrackConfig().getBestTimes().entrySet().stream()
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
