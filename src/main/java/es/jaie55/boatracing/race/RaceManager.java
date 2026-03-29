package es.jaie55.boatracing.race;

import es.jaie55.boatracing.BoatRacingPlugin;
import es.jaie55.boatracing.team.Team;
import es.jaie55.boatracing.track.Region;
import es.jaie55.boatracing.track.TrackConfig;
import es.jaie55.boatracing.util.SchedulerCompat;
import es.jaie55.boatracing.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import org.bukkit.scoreboard.Criteria;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.lang.reflect.Method;
import java.util.*;

public class RaceManager {
    public static class RaceState {
        public int lap = 0;
        public int nextCheckpoint = 0;
        public boolean finished = false;
        public long finishTime = -1L; // millis
        public long penaltyMillis = 0L;
        public long lastLapSplitMillis = 0L;
        public boolean wasInFinish = false;
        public boolean wasInPit = false;
        public boolean wasInCheckpoint = false; // for current next checkpoint only
    }

    public enum BackResult {
        SUCCESS,
        NO_LOCATION,
        EXPIRED
    }

    private final BoatRacingPlugin plugin;
    private final TrackConfig track;
    private boolean running = false;
    private boolean registering = false;
    private int totalLaps;
    private final Map<UUID, RaceState> states = new HashMap<>();
    private long startTime;
    private double pitPenaltySeconds;
    private double falseStartPenaltySeconds;
    private boolean enablePitPenalty;
    private boolean enableFalseStartPenalty;
    private long registrationSeconds;
    // Lights "all out" delay after all 5 are lit
    private double lightsOutDelaySeconds;
    // Additional random jitter [0..jitter] seconds added to lights out
    private double lightsOutJitterSeconds;
    private final LinkedHashSet<UUID> registered = new LinkedHashSet<>();
    private SchedulerCompat.TaskHandle registrationTask;
    private long registrationSessionId = 0L;
    private final Map<UUID, Long> preStartPenalties = new HashMap<>();
    // Mandatory pitstops per racer
    private int mandatoryPitstops;
    // UI toggles
    private boolean sbShowPos;
    private boolean sbShowLap;
    private boolean sbShowCP;
    private boolean sbShowPit;
    private boolean sbShowName;
    private boolean abShowLap;
    private boolean abShowCP;
    private boolean abShowTime;
    private boolean abShowPit;
    // Optional registration lobby
    private boolean lobbyEnabled;
    private boolean lobbyReturnOnLeave;
    private long backWindowMillis;
    private String lobbyWorld;
    private double lobbyX;
    private double lobbyY;
    private double lobbyZ;
    private float lobbyYaw;
    private float lobbyPitch;
    private final Map<UUID, Location> preLobbyLocations = new HashMap<>();
    private final Map<UUID, Long> preLobbyBackExpiresAt = new HashMap<>();
    private final Map<UUID, SchedulerCompat.TaskHandle> backExpiryTasks = new HashMap<>();
    // Race-spawned boats/rafts to ensure deterministic cleanup on finish/cancel.
    private final Set<UUID> raceVehicleIds = new HashSet<>();
    private final Map<UUID, UUID> raceVehicleByPlayer = new HashMap<>();
    // Participants locked in vehicle during pre-race lights countdown.
    private final Set<UUID> countdownLockedParticipants = new HashSet<>();

    // Scoreboard handling
    private SchedulerCompat.TaskHandle scoreboardTask;
    private final Map<UUID, Scoreboard> scoreboards = new HashMap<>();
    private final Map<UUID, Scoreboard> previousScoreboards = new HashMap<>();
    private final Set<UUID> sidebarDisabledPlayers = new HashSet<>();
    private final Set<UUID> overlayPlayers = new HashSet<>();
    private final Map<UUID, Objective> previousSidebarObjectives = new HashMap<>();
    private final Set<UUID> simpleScoreHiddenByBoatRacing = new HashSet<>();
    // Per-player pitstop count for current lap
    private final Map<UUID, Integer> pitCount = new HashMap<>();
    // Sector leader times per lap: lap -> (checkpointIndex -> first time/ms)
    private final Map<Integer, Map<Integer, Long>> sectorLeaderTimes = new HashMap<>();
    // Leader finish time per lap
    private final Map<Integer, Long> lapLeaderFinishTimes = new HashMap<>();

    public RaceManager(BoatRacingPlugin plugin, TrackConfig track) {
        this.plugin = plugin;
        this.track = track;
        loadSettings();
    }

    /**
     * Reload all racing settings from global config, applying per-track overrides if present.
     * Called on construction and whenever a track is loaded before a race.
     */
    public void loadSettings() {
        org.bukkit.configuration.file.FileConfiguration cfg = plugin.getConfig();
        this.totalLaps = track.getRacingInt("laps", cfg.getInt("racing.laps", 3));
        this.pitPenaltySeconds = track.getRacingDouble("pit-penalty-seconds", cfg.getDouble("racing.pit-penalty-seconds", 5.0));
        this.falseStartPenaltySeconds = track.getRacingDouble("false-start-penalty-seconds", cfg.getDouble("racing.false-start-penalty-seconds", 3.0));
        this.enablePitPenalty = track.getRacingBoolean("enable-pit-penalty", cfg.getBoolean("racing.enable-pit-penalty", true));
        this.enableFalseStartPenalty = track.getRacingBoolean("enable-false-start-penalty", cfg.getBoolean("racing.enable-false-start-penalty", true));
        this.registrationSeconds = track.getRacingLong("registration-seconds", cfg.getLong("racing.registration-seconds", 300L));
        this.lightsOutDelaySeconds = Math.max(0.0, track.getRacingDouble("lights-out-delay-seconds", cfg.getDouble("racing.lights-out-delay-seconds", 1.0)));
        this.lightsOutJitterSeconds = Math.max(0.0, track.getRacingDouble("lights-out-jitter-seconds", cfg.getDouble("racing.lights-out-jitter-seconds", 0.0)));
        this.mandatoryPitstops = Math.max(0, track.getRacingInt("mandatory-pitstops", cfg.getInt("racing.mandatory-pitstops", 0)));
        // UI toggles (defaults true)
        this.sbShowPos = cfg.getBoolean("racing.ui.scoreboard.show-position", true);
        this.sbShowLap = cfg.getBoolean("racing.ui.scoreboard.show-lap", true);
        this.sbShowCP = cfg.getBoolean("racing.ui.scoreboard.show-checkpoints", true);
        this.sbShowPit = cfg.getBoolean("racing.ui.scoreboard.show-pitstops", true);
        this.sbShowName = cfg.getBoolean("racing.ui.scoreboard.show-name", true);
        this.abShowLap = cfg.getBoolean("racing.ui.actionbar.show-lap", true);
        this.abShowCP = cfg.getBoolean("racing.ui.actionbar.show-checkpoints", true);
        this.abShowTime = cfg.getBoolean("racing.ui.actionbar.show-time", true);
        this.abShowPit = cfg.getBoolean("racing.ui.actionbar.show-pitstops", true);
        this.lobbyEnabled = cfg.getBoolean("racing.lobby.enabled", false);
        this.lobbyReturnOnLeave = cfg.getBoolean("racing.lobby.return-on-leave", true);
        long configuredBackWindowSeconds = Math.max(1L, cfg.getLong("racing.lobby.back-window-seconds", 180L));
        this.backWindowMillis = configuredBackWindowSeconds * 1000L;
        this.lobbyWorld = cfg.getString("racing.lobby.world", "world");
        this.lobbyX = cfg.getDouble("racing.lobby.x", 0.0);
        this.lobbyY = cfg.getDouble("racing.lobby.y", 80.0);
        this.lobbyZ = cfg.getDouble("racing.lobby.z", 0.0);
        this.lobbyYaw = (float) cfg.getDouble("racing.lobby.yaw", 0.0);
        this.lobbyPitch = (float) cfg.getDouble("racing.lobby.pitch", 0.0);
    }

    // Allow updating mandatory pitstops at runtime from Setup
    public void setMandatoryPitstops(int n) { this.mandatoryPitstops = Math.max(0, n); }

    public boolean isRunning() { return running; }
    public boolean isRegistering() { return registering; }
    public int getTotalLaps() { return totalLaps; }
    public void setTotalLaps(int laps) { this.totalLaps = Math.max(1, laps); }
    public TrackConfig getTrack() { return track; }
    public Set<UUID> getRegistered() { return Collections.unmodifiableSet(registered); }
    public Collection<UUID> getParticipants() { return states.keySet(); }
    public boolean isParticipant(UUID playerId) { return playerId != null && states.containsKey(playerId); }
    public boolean shouldPreventVehicleExit(UUID playerId) {
        if (playerId == null) return false;
        if (running && states.containsKey(playerId)) return true;
        return countdownLockedParticipants.contains(playerId);
    }
    public int getMandatoryPitstops() { return mandatoryPitstops; }

    // --- Live read-only stats for placeholders ---
    public long getLiveTimeMillis(UUID playerId) {
        RaceState st = states.get(playerId);
        if (st == null) return -1L;
        return timeFor(st);
    }

    public int getLiveLap(UUID playerId) {
        RaceState st = states.get(playerId);
        return st == null ? 0 : st.lap;
    }

    public int getLiveCheckpoint(UUID playerId) {
        RaceState st = states.get(playerId);
        return st == null ? 0 : st.nextCheckpoint;
    }

    public int getLivePitstops(UUID playerId) {
        return pitCount.getOrDefault(playerId, 0);
    }

    public boolean isLiveFinished(UUID playerId) {
        RaceState st = states.get(playerId);
        return st != null && st.finished;
    }

    public int getLivePosition(UUID playerId) {
        RaceState target = states.get(playerId);
        if (target == null) return 0;
        List<Map.Entry<UUID, RaceState>> list = new ArrayList<>(states.entrySet());
        list.sort(Comparator.comparingLong(e -> timeFor(e.getValue())));
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).getKey().equals(playerId)) return i + 1;
        }
        return 0;
    }

    // --- Race lifecycle ---
    public void startRace(Collection<Player> participants) {
        if (track.getFinish() == null) throw new IllegalStateException("Finish region is not set");
        // Starting a race must always close any registration window/timer first.
        closeRegistrationWindow();
        clearCountdownLock();
        running = true;
        startTime = System.currentTimeMillis();
        states.clear();
    sectorLeaderTimes.clear();
    lapLeaderFinishTimes.clear();
        for (Player p : participants) {
            if (trySimpleScoreHide(p)) {
                simpleScoreHiddenByBoatRacing.add(p.getUniqueId());
            }
            RaceState st = new RaceState();
            Long pre = enableFalseStartPenalty ? preStartPenalties.remove(p.getUniqueId()) : null;
            if (pre != null && pre > 0) st.penaltyMillis += pre;
            st.lastLapSplitMillis = 0L;
            states.put(p.getUniqueId(), st);
            setupPlayerBoard(p);
            pitCount.put(p.getUniqueId(), 0);
        }
        for (Player p : participantsAndAdmins(participants)) {
            p.sendMessage(color(plugin.pref() + plugin.msg().get("race.started", "laps", String.valueOf(totalLaps))));
        }
        startScoreboard();
    }

    public void stopRace(boolean announce) {
        running = false;
        clearCountdownLock();
        if (announce) announceResults();
        cleanupRaceVehicles();
        sendParticipantsToLobbyAfterRace();
        stopScoreboard();
    }

    public void reset() {
        running = false;
        closeRegistrationWindow();
        clearCountdownLock();
        cleanupRaceVehicles();
        states.clear();
        startTime = 0L;
        registered.clear();
        preStartPenalties.clear();
        cancelAllBackExpiryTasks();
        preLobbyLocations.clear();
        preLobbyBackExpiresAt.clear();
        stopScoreboard();
    }

    // Called on player movement to update crossing events
    public void tickPlayer(Player p, Location to) {
        if (!running) return;
        RaceState st = states.get(p.getUniqueId());
        if (st == null || st.finished) return;

        // Only process in the same world as track
        Region finish = track.getFinish();
        if (finish == null || finish.world() == null) return;
        if (!to.getWorld().getName().equals(finish.getWorldName())) return;

        // Checkpoint progression
        if (st.nextCheckpoint < track.getCheckpoints().size()) {
            Region next = track.getCheckpoints().get(st.nextCheckpoint);
            boolean inside = next.getBox().contains(to.toVector()) && to.getWorld().getName().equals(next.getWorldName());
            if (inside && !st.wasInCheckpoint) {
                st.wasInCheckpoint = true;
                st.nextCheckpoint++;
                p.sendMessage(color(plugin.pref() + plugin.msg().get("race.checkpoint-reached", "num", String.valueOf(st.nextCheckpoint), "total", String.valueOf(track.getCheckpoints().size()))));
                p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, 1.4f);
                // Sector gap broadcast
                int lapNumber = st.lap + 1; // current lap number (1-based)
                int cpIndex = st.nextCheckpoint; // already incremented above
                long nowMs = timeFor(st);
                Map<Integer, Long> lapMap = sectorLeaderTimes.computeIfAbsent(lapNumber, k -> new HashMap<>());
                if (!lapMap.containsKey(cpIndex)) {
                    lapMap.put(cpIndex, nowMs);
                } else {
                    long gap = Math.max(0L, nowMs - lapMap.get(cpIndex));
                    String msg = plugin.msg().get("race.sector-gap", "cp", String.valueOf(cpIndex), "lap", String.valueOf(lapNumber), "player", p.getName(), "gap", formatSeconds(gap));
                    for (Player r : participantsAndAdmins(statesToPlayers())) r.sendMessage(color(msg));
                }
            } else if (!inside && st.wasInCheckpoint) {
                st.wasInCheckpoint = false;
            }
        }

        // Pit area(s): default and per-team. Count pitstops and apply optional penalty.
        boolean insidePit = false;
        Region defaultPit = track.getPitlane();
        if (defaultPit != null && defaultPit.world() != null) {
            insidePit |= defaultPit.getBox().contains(to.toVector()) && to.getWorld().getName().equals(defaultPit.getWorldName());
        }
        // Team-specific pit
        java.util.UUID teamId = plugin.getTeamManager().getTeamByMember(p.getUniqueId()).map(es.jaie55.boatracing.team.Team::getId).orElse(null);
        Region teamPit = teamId != null ? track.getTeamPit(teamId) : null;
        if (teamPit != null && teamPit.world() != null) {
            insidePit |= teamPit.getBox().contains(to.toVector()) && to.getWorld().getName().equals(teamPit.getWorldName());
        }
        if (defaultPit != null || teamPit != null) {
            boolean wasInPit = st.wasInPit;
            boolean entering = insidePit && !wasInPit;
            boolean exiting = !insidePit && wasInPit;
            if (enablePitPenalty && entering) {
                long add = (long) Math.round(pitPenaltySeconds * 1000.0);
                st.penaltyMillis += add;
                p.sendMessage(color(plugin.pref() + plugin.msg().get("race.pit-penalty", "time", formatSeconds(add))));
                p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_BASS, 0.7f, 0.8f);
            }
            // Update state
            st.wasInPit = insidePit;
            // Count on exit edge
            if (exiting) {
                pitCount.merge(p.getUniqueId(), 1, Integer::sum);
                p.sendMessage(color(plugin.pref() + plugin.msg().get("race.pit-completed", "count", String.valueOf(pitCount.getOrDefault(p.getUniqueId(), 0)))));
            }
        }

        // Finish (or pit as finish) crossing only if all checkpoints collected this lap
        boolean insideFinish = finish.getBox().contains(to.toVector());
        boolean insideFinishOrPit = insideFinish || insidePit;
        if (insideFinishOrPit && !st.wasInFinish) {
            st.wasInFinish = true;
            boolean pitRequirementMet = true;
            if (mandatoryPitstops > 0) {
                int done = pitCount.getOrDefault(p.getUniqueId(), 0);
                pitRequirementMet = done >= mandatoryPitstops;
                if (!pitRequirementMet && insideFinish) {
                    p.sendMessage(color(plugin.pref() + plugin.msg().get("race.pit-requirement-not-met", "required", String.valueOf(mandatoryPitstops), "done", String.valueOf(done))));
                    p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_BASS, 0.7f, 0.7f);
                }
            }
            if (st.nextCheckpoint >= track.getCheckpoints().size() && pitRequirementMet) {
                // Lap finish time before mutating state
                int lapCompleted = st.lap + 1;
                long lapFinishMs = (System.currentTimeMillis() - startTime) + st.penaltyMillis;
                long lapDurationMs = Math.max(0L, lapFinishMs - st.lastLapSplitMillis);
                st.lastLapSplitMillis = lapFinishMs;
                st.lap++;
                st.nextCheckpoint = 0;
                st.wasInCheckpoint = false;
                if (plugin.getStatsManager() != null && lapDurationMs > 0) {
                    plugin.getStatsManager().updatePlayerBestLap(p.getUniqueId(), lapDurationMs);
                }
                // Do not reset pit counter; mandatory pitstops apply for the whole race
                if (st.lap >= totalLaps) {
                    st.finished = true;
                    long now = System.currentTimeMillis();
                    st.finishTime = (now - startTime) + st.penaltyMillis;
                    try { track.updateBestTime(p.getUniqueId(), st.finishTime); } catch (Exception ignored) { var inst = BoatRacingPlugin.getInstance(); if (inst != null) inst.getLogger().finer("updateBestTime failed: " + ignored.getMessage()); }
                    if (plugin.getStatsManager() != null) {
                        plugin.getStatsManager().updatePlayerBestRace(p.getUniqueId(), st.finishTime);
                    }
                    // Winner reference and gap to winner (same line)
                    Long winnerMs = lapLeaderFinishTimes.get(lapCompleted);
                    if (winnerMs == null) {
                        lapLeaderFinishTimes.put(lapCompleted, lapFinishMs);
                    }
                    String gapSuffix = "";
                    if (winnerMs != null) {
                        long gapF = Math.max(0L, lapFinishMs - winnerMs);
                        gapSuffix = " &7(+" + formatSeconds(gapF) + " to winner)";
                    }
                    // Announce the finisher's total time to all race participants and admins (English)
                    String finMsg = plugin.msg().get("race.player-finished", "player", p.getName(), "time", formatSeconds(st.finishTime), "gap", gapSuffix);
                    for (Player r : participantsAndAdmins(statesToPlayers())) r.sendMessage(color(finMsg));
                    p.playSound(p.getLocation(), org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.3f);
                    checkAllFinished();
                } else {
                    // Lap finish broadcast (always). First finisher sets baseline; others show +gap to leader
                    Long leaderLapMs = lapLeaderFinishTimes.get(lapCompleted);
                    if (leaderLapMs == null) {
                        lapLeaderFinishTimes.put(lapCompleted, lapFinishMs);
                    }
                    String gapLapSuffix = "";
                    if (leaderLapMs != null) {
                        long gap = Math.max(0L, lapFinishMs - leaderLapMs);
                        gapLapSuffix = " &7(+" + formatSeconds(gap) + " to leader)";
                    }
                    String lapMsg = plugin.msg().get("race.lap-finished", "num", String.valueOf(lapCompleted), "player", p.getName(), "gap", gapLapSuffix);
                    for (Player r : participantsAndAdmins(statesToPlayers())) r.sendMessage(color(lapMsg));
                    p.sendMessage(color(plugin.pref() + plugin.msg().get("race.lap-completed", "lap", String.valueOf(st.lap), "total", String.valueOf(totalLaps))));
                    p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, 1.2f);
                }
            } else {
                // Tried to finish without checkpoints or missing mandatory pitstops
                if (st.nextCheckpoint < track.getCheckpoints().size()) {
                    int remain = track.getCheckpoints().size() - st.nextCheckpoint;
                    p.sendMessage(color(plugin.pref() + plugin.msg().get("race.checkpoints-incomplete", "remain", String.valueOf(remain))));
                } else if (!pitRequirementMet && !insideFinish) {
                    // If failing pit requirement while using pit lane as finish, show a hint only when not already shown above
                    int done = pitCount.getOrDefault(p.getUniqueId(), 0);
                    p.sendMessage(color(plugin.pref() + plugin.msg().get("race.pit-requirement-not-met", "required", String.valueOf(mandatoryPitstops), "done", String.valueOf(done))));
                }
                p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.6f);
            }
        } else if (!insideFinishOrPit && st.wasInFinish) {
            st.wasInFinish = false;
        }
    }

    private void checkAllFinished() {
        for (RaceState s : states.values()) if (!s.finished) return;
        stopRace(true);
    }

    public void announceResults() {
        if (states.isEmpty()) return;
        List<Map.Entry<UUID, RaceState>> list = new ArrayList<>(states.entrySet());
        list.sort(Comparator.comparingLong(e -> timeFor(e.getValue())));

        // Persist winner stats for placeholders/holograms
        if (!list.isEmpty() && plugin.getStatsManager() != null) {
            UUID winner = list.get(0).getKey();
            plugin.getStatsManager().addPlayerWin(winner);
            plugin.getTeamManager().getTeamByMember(winner).ifPresent(t -> plugin.getStatsManager().addTeamWin(t.getId()));
        }

        for (Player p : Bukkit.getOnlinePlayers()) p.sendMessage(color(plugin.msg().get("race.results.header")));
        int pos = 1;
        for (Map.Entry<UUID, RaceState> e : list) {
            UUID id = e.getKey();
            Player online = Bukkit.getPlayer(id);
            String name;
            if (online != null) {
                name = safeRenderName(online);
            } else {
                name = Optional.ofNullable(Bukkit.getOfflinePlayer(id).getName()).orElse(id.toString().substring(0,8));
            }
            if (name == null || name.isEmpty()) name = id.toString().substring(0,8);

            RaceState s = e.getValue();
            long t = timeFor(s);

            String penaltyStr = s.penaltyMillis > 0 ? " &7(+" + formatSeconds(s.penaltyMillis) + " penalty)" : "";
            String msgKey = switch (pos) {
                case 1 -> "race.results.first";
                case 2 -> "race.results.second";
                case 3 -> "race.results.third";
                default -> "race.results.other";
            };
            String line = plugin.msg().get(msgKey, "pos", String.valueOf(pos), "player", name, "time", formatSeconds(t), "penalty", penaltyStr);

            pos++;
            for (Player p : Bukkit.getOnlinePlayers()) p.sendMessage(color(line));
        }

        // Distribute rewards
        try {
            es.jaie55.boatracing.reward.RewardManager rm = plugin.getRewardManager();
            if (rm != null && rm.isEnabled()) {
                String trackName = Optional.ofNullable(plugin.getTrackLibrary())
                        .map(es.jaie55.boatracing.track.TrackLibrary::getCurrent).orElse(plugin.msg().get("general.unsaved"));
                List<Map.Entry<UUID, Long>> results = new ArrayList<>();
                for (Map.Entry<UUID, RaceState> e : list) {
                    results.add(new AbstractMap.SimpleEntry<>(e.getKey(), timeFor(e.getValue())));
                }
                rm.giveRewards(results, trackName, totalLaps);
            }
        } catch (Exception ex) {
            plugin.getLogger().warning("Failed to distribute rewards: " + ex.getMessage());
        }
    }

    private long timeFor(RaceState s) {
        if (s.finishTime >= 0) return s.finishTime;
        return (System.currentTimeMillis() - startTime) + s.penaltyMillis;
    }

    private static String formatSeconds(long millis) {
        long sec = millis / 1000;
        long ms = millis % 1000;
        long m = sec / 60; long s = sec % 60;
        return String.format("%d:%02d.%03d", m, s, ms);
    }

    private static String color(String s) { return es.jaie55.boatracing.util.Text.colorize(s); }

    // --- Registration ---
    public boolean openRegistration(int laps, Long secondsOverride) {
        if (running || registering) return false;
        stopRegistrationTimer();
        setTotalLaps(laps);
        registered.clear();
        registering = true;
        final long sessionId = ++registrationSessionId;
        long seconds = secondsOverride != null ? secondsOverride : registrationSeconds;
        long endAt = System.currentTimeMillis() + (seconds * 1000L);

        String trackName = Optional.ofNullable(plugin.getTrackLibrary())
                .map(es.jaie55.boatracing.track.TrackLibrary::getCurrent)
                .orElse(plugin.msg().get("general.unsaved"));
        String cmd = "/boatracing race join " + trackName;

        String line = plugin.msg().get("race.registration.announce",
            "track", trackName,
            "laps", String.valueOf(totalLaps),
            "cmd", cmd,
            "label", "boatracing");

        broadcast(color(line));
        broadcast(color(plugin.msg().get("race.registration.closes-in", "seconds", String.valueOf(seconds))));

        registrationTask = SchedulerCompat.runTimer(plugin, new Runnable() {
            long lastAnnounced = seconds;
            @Override public void run() {
                if (!registering || sessionId != registrationSessionId) {
                    return;
                }
                long remain = Math.max(0, (endAt - System.currentTimeMillis()) / 1000L);
                if (remain <= 0) {
                    closeRegistrationWindow();
                    startFromRegistered();
                    return;
                }
                if (shouldAnnounce(remain, lastAnnounced)) {
                    lastAnnounced = remain;
                    broadcast(color(plugin.msg().get("race.registration.countdown", "remain", String.valueOf(remain), "count", String.valueOf(registered.size()))));
                }
            }
        }, 20L, 20L);
        return true;
    }

    private boolean shouldAnnounce(long remain, long last) {
        if (remain >= 60) return (remain % 60) == 0; // every minute
        if (remain == 30 || remain == 20 || remain == 10) return true;
        return remain <= 5; // 5,4,3,2,1
    }

    public boolean join(Player p) {
        if (!registering || running) return false;
        boolean added = registered.add(p.getUniqueId());
        if (added) {
            capturePreLobbyLocation(p);
        }
        teleportToLobbyIfEnabled(p);
        p.sendMessage(color(plugin.pref() + plugin.msg().get("race.registration.joined", "laps", String.valueOf(totalLaps))));
        p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.3f);
        broadcast(color(plugin.msg().get("race.registration.player-joined", "player", p.getName(), "count", String.valueOf(registered.size()))));
        return true;
    }

    public boolean leave(Player p) {
        if (!registering || running) return false;
        boolean removed = registered.remove(p.getUniqueId());
        if (removed) {
            restoreFromLobbyIfNeeded(p, true);
            p.sendMessage(color(plugin.pref() + plugin.msg().get("race.registration.left")));
            p.playSound(p.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 0.8f, 1.1f);
            broadcast(color(plugin.msg().get("race.registration.player-left", "player", p.getName(), "count", String.valueOf(registered.size()))));
        }
        return removed;
    }

    public boolean forceStart() {
        if (running) return false;
        closeRegistrationWindow();
        return startFromRegistered();
    }

    public void closeRegistrationWindow() {
        registering = false;
        stopRegistrationTimer();
    }

    private boolean startFromRegistered() {
        if (running) return false;
        List<Player> participants = new ArrayList<>();
        for (UUID id : registered) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && p.isOnline()) participants.add(p);
        }
        if (participants.isEmpty()) {
            restoreLobbyForRegistered(true);
            cancelAllBackExpiryTasks();
            preLobbyLocations.clear();
            preLobbyBackExpiresAt.clear();
            broadcast(color(plugin.msg().get("race.cancelled-no-participants")));
            return false;
        }
        List<Player> placed = placeAtStartsWithBoats(participants);
        if (placed.isEmpty()) {
            restoreLobbyForRegistered(true);
            cancelAllBackExpiryTasks();
            preLobbyLocations.clear();
            preLobbyBackExpiresAt.clear();
            broadcast(color(plugin.msg().get("race.cancelled-no-slots")));
            return false;
        }
        if (placed.size() < participants.size()) broadcast(color(plugin.msg().get("race.some-not-placed")));
        // Keep pre-lobby locations so players can use /boatracing race back after the race.
        startRaceWithCountdown(placed);
        return true;
    }

    public boolean cancelRace() {
        if (!running) return false;
        running = false;
        closeRegistrationWindow();
        clearCountdownLock();
        cleanupRaceVehicles();
        sendParticipantsToLobbyAfterRace();
        states.clear();
        broadcast(color(plugin.msg().get("race.cancelled-general")));
        stopScoreboard();
        return true;
    }

    public BackResult returnToSavedLocation(Player p) {
        if (p == null || !p.isOnline()) return BackResult.NO_LOCATION;
        UUID playerId = p.getUniqueId();
        Location prev = preLobbyLocations.get(playerId);
        if (prev == null || prev.getWorld() == null) {
            preLobbyBackExpiresAt.remove(playerId);
            cancelBackExpiryTask(playerId);
            return BackResult.NO_LOCATION;
        }
        Long expiresAt = preLobbyBackExpiresAt.get(playerId);
        if (expiresAt != null && System.currentTimeMillis() > expiresAt) {
            preLobbyLocations.remove(playerId);
            preLobbyBackExpiresAt.remove(playerId);
            cancelBackExpiryTask(playerId);
            return BackResult.EXPIRED;
        }
        preLobbyLocations.remove(playerId);
        preLobbyBackExpiresAt.remove(playerId);
        cancelBackExpiryTask(playerId);
        if (p.isInsideVehicle()) {
            org.bukkit.entity.Entity vehicle = p.getVehicle();
            p.leaveVehicle();
            if (vehicle != null) vehicle.remove();
        }
        p.teleport(prev);
        p.sendMessage(color(plugin.pref() + plugin.msg().get("race.registration.lobby-returned")));
        return BackResult.SUCCESS;
    }

    public boolean cancelRegistration(boolean announce) {
        if (!registering) return false;
        closeRegistrationWindow();
        clearCountdownLock();
        int count = registered.size();
        restoreLobbyForRegistered(true);
        registered.clear();
        cancelAllBackExpiryTasks();
        preLobbyLocations.clear();
        preLobbyBackExpiresAt.clear();
        if (announce) broadcast(color(plugin.msg().get("race.registration.cancelled", "count", String.valueOf(count))));
        return true;
    }

    private void stopRegistrationTimer() {
        registrationSessionId++;
        if (registrationTask != null) {
            try { registrationTask.cancel(); } catch (Exception ignored) { plugin.getLogger().finer("Failed to cancel registration timer: " + ignored.getMessage()); }
            registrationTask = null;
        }
    }

    private Location getLobbyLocation() {
        if (!lobbyEnabled) return null;
        if (lobbyWorld == null || lobbyWorld.isEmpty()) return null;
        org.bukkit.World world = Bukkit.getWorld(lobbyWorld);
        if (world == null) return null;
        return new Location(world, lobbyX, lobbyY, lobbyZ, lobbyYaw, lobbyPitch);
    }

    private void capturePreLobbyLocation(Player p) {
        if (p == null || !p.isOnline()) return;
        preLobbyLocations.put(p.getUniqueId(), p.getLocation().clone());
        preLobbyBackExpiresAt.remove(p.getUniqueId());
        cancelBackExpiryTask(p.getUniqueId());
    }

    private void teleportToLobbyIfEnabled(Player p) {
        if (p == null || !p.isOnline()) return;
        Location lobby = getLobbyLocation();
        if (lobby == null) return;
        if (p.isInsideVehicle()) {
            org.bukkit.entity.Entity vehicle = p.getVehicle();
            p.leaveVehicle();
            if (vehicle != null) vehicle.remove();
        }
        p.teleport(lobby);
        p.sendMessage(color(plugin.pref() + plugin.msg().get("race.registration.lobby-teleported")));
    }

    private void sendParticipantsToLobbyAfterRace() {
        Location lobby = getLobbyLocation();
        if (lobby == null) return;
        final String backCommand = "/boatracing race back";
        long backWindowSeconds = Math.max(1L, backWindowMillis / 1000L);
        long backWindowMinutes = Math.max(1L, (backWindowSeconds + 59L) / 60L);
        for (UUID id : new ArrayList<>(states.keySet())) {
            Player p = Bukkit.getPlayer(id);
            if (p == null || !p.isOnline()) continue;
            teleportToLobbyIfEnabled(p);
            Location saved = preLobbyLocations.get(id);
            if (saved == null || saved.getWorld() == null) continue;
            long expiresAt = System.currentTimeMillis() + backWindowMillis;
            preLobbyBackExpiresAt.put(id, expiresAt);
            scheduleBackExpiryNotice(id, expiresAt);
            p.sendMessage(color(plugin.pref() + plugin.msg().get("race.registration.lobby-waiting-returned")));
            p.sendMessage(color(plugin.pref() + plugin.msg().get(
                    "race.registration.lobby-back-window",
                    "minutes", String.valueOf(backWindowMinutes),
                    "seconds", String.valueOf(backWindowSeconds))));
            p.sendMessage(Text.cmd(plugin.msg().get("race.registration.lobby-back-click"), backCommand));
        }
    }

    private void scheduleBackExpiryNotice(UUID playerId, long expiresAt) {
        cancelBackExpiryTask(playerId);
        long remainingMillis = Math.max(1L, expiresAt - System.currentTimeMillis());
        long delayTicks = Math.max(1L, (remainingMillis + 49L) / 50L);
        SchedulerCompat.TaskHandle handle = SchedulerCompat.runLater(plugin, () -> {
            Long currentExpiresAt = preLobbyBackExpiresAt.get(playerId);
            if (!Objects.equals(currentExpiresAt, expiresAt)) return;
            long now = System.currentTimeMillis();
            if (now < expiresAt) {
                scheduleBackExpiryNotice(playerId, expiresAt);
                return;
            }

            preLobbyBackExpiresAt.remove(playerId);
            preLobbyLocations.remove(playerId);
            backExpiryTasks.remove(playerId);

            Player p = Bukkit.getPlayer(playerId);
            if (p != null && p.isOnline()) {
                p.sendMessage(color(plugin.pref() + plugin.msg().get("race.registration.lobby-back-expired")));
                p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.6f);
            }
        }, delayTicks);
        backExpiryTasks.put(playerId, handle);
    }

    private void cancelBackExpiryTask(UUID playerId) {
        SchedulerCompat.TaskHandle handle = backExpiryTasks.remove(playerId);
        if (handle != null) {
            try { handle.cancel(); } catch (Exception ignored) { plugin.getLogger().finer("Failed to cancel back expiry task: " + ignored.getMessage()); }
        }
    }

    private void cancelAllBackExpiryTasks() {
        for (UUID playerId : new ArrayList<>(backExpiryTasks.keySet())) {
            cancelBackExpiryTask(playerId);
        }
    }

    private void restoreFromLobbyIfNeeded(Player p, boolean onlyWhenLeaving) {
        if (p == null || !p.isOnline()) return;
        if (onlyWhenLeaving && !lobbyReturnOnLeave) return;
        UUID playerId = p.getUniqueId();
        Location prev = preLobbyLocations.remove(playerId);
        preLobbyBackExpiresAt.remove(playerId);
        cancelBackExpiryTask(playerId);
        if (prev != null && prev.getWorld() != null) {
            p.teleport(prev);
            p.sendMessage(color(plugin.pref() + plugin.msg().get("race.registration.lobby-returned")));
        }
    }

    private void restoreLobbyForRegistered(boolean onlyWhenLeaving) {
        if (onlyWhenLeaving && !lobbyReturnOnLeave) return;
        for (UUID id : new ArrayList<>(registered)) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && p.isOnline()) {
                Location prev = preLobbyLocations.remove(id);
                preLobbyBackExpiresAt.remove(id);
                cancelBackExpiryTask(id);
                if (prev != null && prev.getWorld() != null) {
                    p.teleport(prev);
                    p.sendMessage(color(plugin.pref() + plugin.msg().get("race.registration.lobby-returned")));
                }
            }
        }
    }

    public List<Player> placeAtStartsWithBoats(List<Player> participants) {
        List<Player> placed = new ArrayList<>();
        List<TrackConfig.StartSlot> starts = track.getStarts();
        if (starts.isEmpty()) return placed;
        // Defensive: remove any stale race boats before spawning a new grid.
        cleanupRaceVehicles();

        // 1) Build slot assignment map (slotIndex -> player)
        java.util.Map<Integer, Player> slotToPlayer = new java.util.HashMap<>();
        java.util.Set<UUID> unassigned = new java.util.LinkedHashSet<>();
        for (Player p : participants) {
            Integer custom = track.getCustomStartSlot(p.getUniqueId());
            if (custom != null && custom >= 0 && custom < starts.size() && !slotToPlayer.containsKey(custom)) {
                slotToPlayer.put(custom, p);
            } else {
                unassigned.add(p.getUniqueId());
            }
        }
        // 2) Order unassigned by best known time (ascending), players without time go last preserving registration order
        java.util.List<Player> unassignedPlayers = new java.util.ArrayList<>();
        for (UUID id : unassigned) { Player p = Bukkit.getPlayer(id); if (p != null) unassignedPlayers.add(p); }
        unassignedPlayers.sort((a,b) -> {
            Long ta = track.getBestTime(a.getUniqueId());
            Long tb = track.getBestTime(b.getUniqueId());
            if (ta == null && tb == null) return 0;
            if (ta == null) return 1; // a after b
            if (tb == null) return -1; // a before b
            return Long.compare(ta, tb);
        });
        // 3) Fill remaining free slots in natural order
        java.util.List<Integer> freeSlots = new java.util.ArrayList<>();
        for (int i = 0; i < starts.size(); i++) if (!slotToPlayer.containsKey(i)) freeSlots.add(i);
        java.util.Iterator<Player> it = unassignedPlayers.iterator();
        for (int idx : freeSlots) {
            if (!it.hasNext()) break;
            slotToPlayer.put(idx, it.next());
        }

        // 4) Place players into their assigned slots sequentially by slot index
        java.util.List<Integer> finalSlots = new java.util.ArrayList<>(slotToPlayer.keySet());
        java.util.Collections.sort(finalSlots);
        for (int i : finalSlots) {
            if (i < 0 || i >= starts.size()) continue;
            Player p = slotToPlayer.get(i);
            if (p == null) continue;
            var s = starts.get(i);
            org.bukkit.World w = Bukkit.getWorld(s.world);
            if (w == null) continue;
            Location loc = new Location(w, s.x, s.y, s.z, (float) s.yaw, 0.0f);
            if (p.isInsideVehicle()) p.leaveVehicle();
            p.teleport(loc);
            for (org.bukkit.entity.Entity nearby : w.getNearbyEntities(loc, 1.2, 1.2, 1.2)) {
                String type = nearby.getType().name();
                if (type.endsWith("BOAT") || type.endsWith("RAFT")) nearby.remove();
            }
            String selectedBoat = plugin.getTeamManager()
                    .getTeamByMember(p.getUniqueId())
                    .map((Team t) -> t.getBoatType(p.getUniqueId()))
                    .orElse(Material.OAK_BOAT.name());
            String normalizedBoat = normalizeBoatMaterialName(selectedBoat);
            boolean chest = normalizedBoat.contains("_CHEST_");
            org.bukkit.entity.Vehicle spawned = spawnRaceVehicle(w, loc, normalizedBoat, chest);
            if (spawned == null) {
                plugin.getLogger().warning("Could not spawn race boat for " + p.getName() + "; skipping start slot #" + (i + 1) + ".");
                continue;
            }
            // Apply selected boat/raft model immediately and with delayed retries.
            // This complements material-specific entity spawn on mixed API versions.
            applyBoatVariantReliably(spawned, normalizedBoat);
            registerRaceVehicle(p.getUniqueId(), spawned);

            if (!spawned.addPassenger(p)) {
                SchedulerCompat.runNow(plugin, () -> {
                    if (spawned.isValid()) spawned.addPassenger(p);
                });
            }
            placed.add(p);
        }
        return placed;
    }

    private org.bukkit.entity.Vehicle spawnRaceVehicle(org.bukkit.World world, Location location, String preferredTypeName, boolean chestFallback) {
        java.util.LinkedHashSet<String> candidates = new java.util.LinkedHashSet<>();
        if (preferredTypeName != null && !preferredTypeName.isBlank()) {
            candidates.add(preferredTypeName.toUpperCase(Locale.ROOT));
        }
        candidates.add(chestFallback ? "CHEST_BOAT" : "BOAT");

        for (String candidate : candidates) {
            try {
                org.bukkit.entity.EntityType type = org.bukkit.entity.EntityType.valueOf(candidate);
                org.bukkit.entity.Entity entity = world.spawnEntity(location, type);
                if (entity instanceof org.bukkit.entity.Vehicle vehicle) {
                    return vehicle;
                }
                plugin.getLogger().finer("Spawned non-vehicle entity for boat candidate " + candidate + ": " + entity.getType().name());
            } catch (IllegalArgumentException ignored) {
                // Candidate not available on this server/API version.
            } catch (Exception ex) {
                plugin.getLogger().finer("Failed to spawn race vehicle candidate " + candidate + ": " + ex.getMessage());
            }
        }
        return null;
    }

    private static String normalizeBoatMaterialName(String raw) {
        if (raw == null || raw.isBlank()) return Material.OAK_BOAT.name();
        String upper = raw.trim().toUpperCase(Locale.ROOT);

        if (upper.equals("BOAT")) return "OAK_BOAT";
        if (upper.equals("CHEST_BOAT")) return "OAK_CHEST_BOAT";
        if (upper.equals("RAFT") || upper.equals("BAMBOO_BOAT")) return "BAMBOO_RAFT";
        if (upper.equals("CHEST_RAFT") || upper.equals("BAMBOO_CHEST_BOAT")) return "BAMBOO_CHEST_RAFT";

        if (upper.endsWith("_BOAT") || upper.endsWith("_CHEST_BOAT") || upper.endsWith("_RAFT") || upper.endsWith("_CHEST_RAFT")) {
            return upper;
        }

        if (upper.startsWith("BAMBOO")) {
            return upper.contains("CHEST") ? "BAMBOO_CHEST_RAFT" : "BAMBOO_RAFT";
        }

        return upper + "_BOAT";
    }

    private void applyBoatVariantReliably(org.bukkit.entity.Vehicle vehicle, String materialName) {
        if (vehicle == null || materialName == null || materialName.isBlank()) return;
        applyBoatVariantUniversal(vehicle, materialName);
        SchedulerCompat.runLater(plugin, () -> {
            if (vehicle.isValid()) applyBoatVariantUniversal(vehicle, materialName);
        }, 1L);
        SchedulerCompat.runLater(plugin, () -> {
            if (vehicle.isValid()) applyBoatVariantUniversal(vehicle, materialName);
        }, 10L);
    }

    private void registerRaceVehicle(UUID playerId, org.bukkit.entity.Vehicle vehicle) {
        if (vehicle == null) return;
        UUID vehicleId = vehicle.getUniqueId();
        raceVehicleIds.add(vehicleId);
        if (playerId != null) raceVehicleByPlayer.put(playerId, vehicleId);
    }

    private void cleanupRaceVehicles() {
        if (raceVehicleIds.isEmpty() && raceVehicleByPlayer.isEmpty()) return;

        // First dismount participants still riding tracked race vehicles.
        for (UUID playerId : new ArrayList<>(raceVehicleByPlayer.keySet())) {
            Player p = Bukkit.getPlayer(playerId);
            if (p != null && p.isOnline() && p.isInsideVehicle()) {
                org.bukkit.entity.Entity vehicle = p.getVehicle();
                p.leaveVehicle();
                if (vehicle != null) {
                    String type = vehicle.getType().name();
                    if (type.endsWith("BOAT") || type.endsWith("RAFT")) {
                        vehicle.remove();
                    }
                }
            }
        }

        // Then remove any remaining tracked race vehicles by entity id.
        for (UUID vehicleId : new ArrayList<>(raceVehicleIds)) {
            org.bukkit.entity.Entity vehicle = Bukkit.getEntity(vehicleId);
            if (vehicle != null && vehicle.isValid()) {
                String type = vehicle.getType().name();
                if (type.endsWith("BOAT") || type.endsWith("RAFT")) {
                    vehicle.remove();
                }
            }
        }

        raceVehicleIds.clear();
        raceVehicleByPlayer.clear();
    }

    // Try to set wood/raft variant on Boat or ChestBoat by reflecting setVariant or setBoatType
    @SuppressWarnings({"unchecked","rawtypes"})
    private static void applyBoatVariantUniversal(Object boatEntity, String materialName) {
        if (boatEntity == null || materialName == null) return;
        String upper = materialName.toUpperCase(Locale.ROOT);
        String base = upper
                .replace("_CHEST_BOAT", "")
                .replace("_BOAT", "")
                .replace("_CHEST_RAFT", "")
                .replace("_RAFT", "");
        // Normalize raft tokens to BAMBOO
        if (base.isEmpty() || base.equals("RAFT") || upper.contains("BAMBOO")) {
            base = "BAMBOO";
        }
        Class<?> cls = boatEntity.getClass();
        // 1) setVariant(Enum)
    try {
            java.lang.reflect.Method m = null;
            for (java.lang.reflect.Method mm : cls.getMethods()) {
                if (mm.getName().equals("setVariant") && mm.getParameterCount() == 1 && mm.getParameterTypes()[0].isEnum()) { m = mm; break; }
            }
            if (m != null) {
                Class<?> enumType = m.getParameterTypes()[0];
                Enum variant = Enum.valueOf((Class) enumType.asSubclass(Enum.class), base);
                m.invoke(boatEntity, variant);
                return;
            }
    } catch (Exception ignored) { var inst = BoatRacingPlugin.getInstance(); if (inst != null) inst.getLogger().finer("setVariant invocation failed: " + ignored.getMessage()); }
        // 2) setBoatType(Enum)
    try {
            java.lang.reflect.Method m = null;
            for (java.lang.reflect.Method mm : cls.getMethods()) {
                if (mm.getName().equals("setBoatType") && mm.getParameterCount() == 1 && mm.getParameterTypes()[0].isEnum()) { m = mm; break; }
            }
            if (m != null) {
                Class<?> enumType = m.getParameterTypes()[0];
                Enum type = null;
                try { type = Enum.valueOf((Class) enumType.asSubclass(Enum.class), base); } catch (IllegalArgumentException ex) { /* map unknown to OAK */ }
                if (type == null) {
                    try { type = Enum.valueOf((Class) enumType.asSubclass(Enum.class), "OAK"); } catch (Exception ignored2) { var inst = BoatRacingPlugin.getInstance(); if (inst != null) inst.getLogger().finer("Failed to map boat enum to OAK: " + ignored2.getMessage()); }
                }
                if (type != null) m.invoke(boatEntity, type);
            }
    } catch (Exception ignored) { var inst = BoatRacingPlugin.getInstance(); if (inst != null) inst.getLogger().finer("setBoatType invocation failed: " + ignored.getMessage()); }
    }

    // --- Countdown with 5 start lights and false-start check ---
    public void startRaceWithCountdown(List<Player> participants) {
        List<TrackConfig.LightPos> ls = track.getLights();
        if (ls.size() != 5) {
            clearCountdownLock();
            startRace(participants);
            return;
        }
        setCountdownLock(participants);

        // Prepare per-player origin and forward vectors
        Map<UUID, Location> origins = new HashMap<>();
        Map<UUID, org.bukkit.util.Vector> forwards = new HashMap<>();
        Set<UUID> penalized = new HashSet<>();
        for (Player p : participants) {
            Location loc = p.getLocation().clone();
            origins.put(p.getUniqueId(), loc);
            double rad = Math.toRadians(loc.getYaw());
            org.bukkit.util.Vector fwd = new org.bukkit.util.Vector(-Math.sin(rad), 0, Math.cos(rad));
            if (fwd.lengthSquared() > 0) fwd.normalize();
            forwards.put(p.getUniqueId(), fwd);
        }

        // Ensure all lamps start unlit
        for (var lp : ls) {
            org.bukkit.block.Block b = lp.getBlock();
            if (b != null && b.getType() == org.bukkit.Material.REDSTONE_LAMP) {
                org.bukkit.block.data.BlockData bd = b.getBlockData();
                if (bd instanceof org.bukkit.block.data.Lightable lightable) {
                    if (lightable.isLit()) { lightable.setLit(false); b.setBlockData(lightable, false); }
                }
            }
        }

        final int[] idx = {0};
        final SchedulerCompat.TaskHandle[] taskRef = new SchedulerCompat.TaskHandle[1];
    final SchedulerCompat.TaskHandle[] checkTaskRef = new SchedulerCompat.TaskHandle[1];

        // False start checker (every 2 ticks) until GO
    if (enableFalseStartPenalty) {
            checkTaskRef[0] = SchedulerCompat.runTimer(plugin, () -> {
                if (idx[0] >= ls.size()) return; // stop after GO
                for (Player p : participants) {
                    if (penalized.contains(p.getUniqueId()) || !p.isOnline()) continue;
                    Location origin = origins.get(p.getUniqueId());
                    org.bukkit.util.Vector fwd = forwards.get(p.getUniqueId());
                    if (origin == null || fwd == null) continue;
                    Location now = p.getLocation();
                    if (!now.getWorld().equals(origin.getWorld())) continue;
                    org.bukkit.util.Vector delta = now.toVector().subtract(origin.toVector());
                    double forwardDist = delta.dot(fwd);
                    if (forwardDist > 0.25) {
                        penalized.add(p.getUniqueId());
                        long add = (long) Math.round(falseStartPenaltySeconds * 1000.0);
                        preStartPenalties.merge(p.getUniqueId(), add, Long::sum);
                        p.sendMessage(color(plugin.pref() + plugin.msg().get("race.false-start", "time", formatSeconds(add))));
                        broadcast(color(plugin.msg().get("race.false-start-broadcast", "player", p.getName(), "time", formatSeconds(add))));
                        p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.8f);
                    }
                }
            }, 1L, 2L);
        }

        // Lights countdown (1 per second)
        taskRef[0] = SchedulerCompat.runTimer(plugin, () -> {
            if (idx[0] >= ls.size()) {
                if (taskRef[0] != null) taskRef[0].cancel();
                // Keep false-start checker running until actual GO
                double jitter = lightsOutJitterSeconds > 0.0 ? Math.random() * lightsOutJitterSeconds : 0.0;
                long delayTicks = Math.max(0L, Math.round((lightsOutDelaySeconds + jitter) * 20.0));
                SchedulerCompat.runLater(plugin, () -> {
                    // Now turn all lights off and GO
                    for (var lp2 : ls) {
                        org.bukkit.block.Block b2 = lp2.getBlock();
                        if (b2 != null && b2.getType() == org.bukkit.Material.REDSTONE_LAMP) {
                            org.bukkit.block.data.BlockData bd2 = b2.getBlockData();
                            if (bd2 instanceof org.bukkit.block.data.Lightable lightable2) {
                                if (lightable2.isLit()) { lightable2.setLit(false); b2.setBlockData(lightable2, false); }
                            }
                        }
                    }
                    if (checkTaskRef[0] != null) checkTaskRef[0].cancel();
                    clearCountdownLock();
                    Collection<Player> recipsGo = participantsAndAdmins(participants);
                    for (Player p : recipsGo) p.sendMessage(color(plugin.msg().get("race.go")));
                    for (Player p : recipsGo) p.playSound(p.getLocation(), org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.2f);
                    startRace(participants);
                }, delayTicks);
                return;
            }
            var lp = ls.get(idx[0]);
            org.bukkit.block.Block b = lp.getBlock();
            if (b != null && b.getType() == org.bukkit.Material.REDSTONE_LAMP) {
                org.bukkit.block.data.BlockData bd = b.getBlockData();
                if (bd instanceof org.bukkit.block.data.Lightable lightable) {
                    lightable.setLit(true);
                    b.setBlockData(lightable, false);
                }
            }
            int remaining = ls.size() - idx[0];
            Collection<Player> recips = participantsAndAdmins(participants);
            for (Player p : recips) p.sendMessage(color(plugin.msg().get("race.starting-countdown", "count", String.valueOf(remaining))));
            for (Player p : recips) p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_HAT, 0.9f, 1.6f);
            idx[0]++;
        }, 0L, 20L);
    }

    private void setCountdownLock(Collection<Player> participants) {
        countdownLockedParticipants.clear();
        for (Player p : participants) {
            if (p != null) countdownLockedParticipants.add(p.getUniqueId());
        }
    }

    private void clearCountdownLock() {
        countdownLockedParticipants.clear();
    }

    // --- Scoreboard (sidebar) showing Lap, Checkpoints, and Time ---
    private void setupPlayerBoard(Player p) {
        try {
            ScoreboardManager sm = Bukkit.getScoreboardManager();
            if (sm == null) return;
            UUID pid = p.getUniqueId();
            Scoreboard current = p.getScoreboard();
            Scoreboard main = sm.getMainScoreboard();

            // Hard compatibility mode: if player is already on an external/custom board,
            // do not touch sidebar at all (ActionBar-only during race).
            boolean externalBoard = current != null && current != main && !isBoatRacingBoard(current);
            if (externalBoard) {
                previousScoreboards.putIfAbsent(pid, current);
                sidebarDisabledPlayers.add(pid);
                scoreboards.remove(pid);
                overlayPlayers.remove(pid);
                previousSidebarObjectives.remove(pid);
                return;
            }

            sidebarDisabledPlayers.remove(pid);
            // Preserve the player's active board so external plugins (e.g. SimpleScore) can be restored.
            // Never overwrite with our own board if setup gets called again during the race.
            if (current != null && !isBoatRacingBoard(current)) {
                previousScoreboards.put(pid, current);
            }
            Scoreboard sb = null;
            boolean canOverlay = current != null && current != main && !isBoatRacingBoard(current);
            if (canOverlay && current != null) {
                sb = current;
                overlayPlayers.add(pid);
                previousSidebarObjectives.putIfAbsent(pid, current.getObjective(DisplaySlot.SIDEBAR));
            } else {
                sb = sm.getNewScoreboard();
                if (sb == null) return;
                overlayPlayers.remove(pid);
                previousSidebarObjectives.remove(pid);
            }
            String objName = "boatracing"; // constant name to play nice with external plugins (e.g., TAB)
            Objective obj = sb.getObjective(objName);
            if (obj == null) {
                obj = sb.registerNewObjective(
                    objName,
                    Criteria.DUMMY,
                    Component.text("BoatRacing", NamedTextColor.GOLD)
                );
            }
            obj.setDisplaySlot(DisplaySlot.SIDEBAR);
            // Prepare lines: spacer + header separator + up to 10 rows => 12 entries total
            String[] entries = new String[]{"\u00A70","\u00A71","\u00A72","\u00A73","\u00A74","\u00A75","\u00A76","\u00A77","\u00A78","\u00A79","\u00A7a","\u00A7b"};
            for (int i = 0; i < entries.length; i++) {
                String teamName = "br_ln_" + (i+1);
                org.bukkit.scoreboard.Team t = sb.getTeam(teamName); if (t == null) t = sb.registerNewTeam(teamName);
                t.addEntry(entries[i]);
                obj.getScore(entries[i]).setScore(entries.length - i);
            }
            if (!canOverlay) {
                p.setScoreboard(sb);
            }
            scoreboards.put(pid, sb);
    } catch (Exception ignored) { BoatRacingPlugin.getInstance().getLogger().finer("setupPlayerBoard failed: " + ignored.getMessage()); }
    }

    private void startScoreboard() {
        if (scoreboardTask != null) return;
        scoreboardTask = SchedulerCompat.runTimer(plugin, () -> {
            // Build global ordering once per tick
            List<Map.Entry<UUID, RaceState>> ordered = new ArrayList<>(states.entrySet());
            ordered.sort((a, b) -> {
                RaceState sa = a.getValue();
                RaceState sb = b.getValue();
                if (sa.finished && sb.finished) return Long.compare(sa.finishTime, sb.finishTime);
                if (sa.finished != sb.finished) return sa.finished ? -1 : 1;
                if (sa.lap != sb.lap) return Integer.compare(sb.lap, sa.lap);
                if (sa.nextCheckpoint != sb.nextCheckpoint) return Integer.compare(sb.nextCheckpoint, sa.nextCheckpoint);
                return Long.compare(timeFor(sa), timeFor(sb));
            });

            int totalCPs = track.getCheckpoints().size();

            // Update each viewer independently (to highlight own name)
            for (UUID viewerId : new ArrayList<>(states.keySet())) {
                Player viewer = Bukkit.getPlayer(viewerId);
                RaceState viewerState = states.get(viewerId);
                if (viewer == null || viewerState == null) continue;
                Scoreboard sb = scoreboards.get(viewerId);
                if (sb == null && !sidebarDisabledPlayers.contains(viewerId)) {
                    setupPlayerBoard(viewer);
                    sb = scoreboards.get(viewerId);
                }

                if (sb != null) {
                    // Build lines for this viewer
                    java.util.List<Component> lines = new java.util.ArrayList<>();
                    // Spacer line to create space between title and first row
                    lines.add(Component.text(" "));

                    int maxRows = 10;
                    int rowCount = Math.min(maxRows, ordered.size());

                    // Build content segments without centering/padding (1.0.8 requirement)
                    String[] nameShown = new String[rowCount];
                    boolean[] isViewerRow = new boolean[rowCount];
                    for (int i = 0; i < rowCount; i++) {
                        Map.Entry<UUID, RaceState> e = ordered.get(i);
                        UUID pid = e.getKey();
                        // Name
                        String baseName;
                        Player online = Bukkit.getPlayer(pid);
                        if (online != null) {
                            baseName = safeRenderName(online);
                        } else {
                            baseName = Optional.ofNullable(Bukkit.getOfflinePlayer(pid).getName()).orElse(pid.toString().substring(0, 8));
                        }
                        if (baseName == null) baseName = pid.toString().substring(0, 8);
                        String shown = baseName.length() > 12 ? baseName.substring(0, 12) + "..." : baseName;
                        nameShown[i] = shown;
                        boolean isViewer = pid.equals(viewerId);
                        isViewerRow[i] = isViewer;
                    }

                    for (int i = 0; i < rowCount; i++) {
                        Map.Entry<UUID, RaceState> e = ordered.get(i);
                        RaceState st = e.getValue();
                        String shown = nameShown[i];
                        boolean isViewer = isViewerRow[i];

                        String rankStr = (i + 1) + ". ";
                        NamedTextColor rankColor = switch (i) {
                            case 0 -> NamedTextColor.GOLD;
                            case 1 -> NamedTextColor.GRAY; // silver-ish
                            case 2 -> NamedTextColor.DARK_RED; // bronze-ish alternative
                            default -> NamedTextColor.DARK_GRAY;
                        };
                        // No centering/padding
                        Component leftPad = Component.empty();
                        Component line;
                        if (st.finished) {
                            line = leftPad;
                            if (sbShowPos) line = line.append(Component.text(rankStr, rankColor));
                            // finished: show FIN + time as lap/time segment if desired
                            Component finSeg = Component.text("FIN ", NamedTextColor.GREEN)
                                    .append(Component.text(formatSeconds(st.finishTime), NamedTextColor.WHITE));
                            // If neither lap nor CP are shown, we still show FIN/time always
                            line = line.append(finSeg);
                            if (sbShowName) {
                                line = line.append(Component.text(" - ", NamedTextColor.DARK_GRAY))
                                           .append(Component.text(shown, isViewer ? NamedTextColor.GREEN : NamedTextColor.WHITE));
                            }
                        } else {
                            Component lapC = Component.text("L ", NamedTextColor.YELLOW)
                                    .append(Component.text(st.lap + 1, NamedTextColor.WHITE))
                                    .append(Component.text("/", NamedTextColor.DARK_GRAY))
                                    .append(Component.text(totalLaps, NamedTextColor.WHITE));
                            Component cpC = Component.empty();
                            if (totalCPs > 0) {
                                cpC = Component.text(" CP ", NamedTextColor.YELLOW)
                                        .append(Component.text(st.nextCheckpoint, NamedTextColor.WHITE))
                                        .append(Component.text("/", NamedTextColor.DARK_GRAY))
                                        .append(Component.text(totalCPs, NamedTextColor.WHITE));
                            }
                            // Pitstops done/required, shown only if requirement > 0
                            Component pitC = Component.empty();
                            if (mandatoryPitstops > 0) {
                                int done = pitCount.getOrDefault(e.getKey(), 0);
                                pitC = Component.text(" PIT ", NamedTextColor.YELLOW)
                                        .append(Component.text(done, NamedTextColor.WHITE))
                                        .append(Component.text("/", NamedTextColor.DARK_GRAY))
                                        .append(Component.text(mandatoryPitstops, NamedTextColor.WHITE));
                            }
                            line = leftPad;
                            if (sbShowPos) line = line.append(Component.text(rankStr, rankColor));
                            if (sbShowLap) line = line.append(lapC);
                            if (sbShowCP && totalCPs > 0) line = line.append(cpC);
                            if (sbShowPit && mandatoryPitstops > 0) line = line.append(pitC);
                            if (sbShowName) line = line.append(Component.text(" - ", NamedTextColor.DARK_GRAY))
                                                    .append(Component.text(shown, isViewer ? NamedTextColor.GREEN : NamedTextColor.WHITE));
                        }
                        lines.add(line);
                    }

                    // Push lines into scoreboard teams: br_ln_1..12
                    for (int i = 0; i < 12; i++) {
                        String teamName = "br_ln_" + (i+1);
                        org.bukkit.scoreboard.Team t = sb.getTeam(teamName);
                        if (t == null) continue;
                        Component content = i < lines.size() ? lines.get(i) : Component.text("", NamedTextColor.BLACK);
                        try { t.prefix(content); } catch (Exception ignored) { plugin.getLogger().finer("Failed to set scoreboard team prefix: " + ignored.getMessage()); }
                    }
                }

                // No internal number hiding; leave formatting to external plugins if desired

                // Send ActionBar with personal stats (omit CP when none). Update frequently for smooth ms
                int doneCP = Math.min(viewerState.nextCheckpoint, totalCPs);
                Component ab = Component.text("");
                boolean firstSeg = true;
                if (abShowLap) {
                    Component seg = Component.text("Lap ", NamedTextColor.YELLOW)
                            .append(Component.text(viewerState.lap + "/" + totalLaps, NamedTextColor.WHITE));
                    ab = ab.append(seg);
                    firstSeg = false;
                }
                if (abShowCP && totalCPs > 0) {
                    if (!firstSeg) ab = ab.append(Component.text("  ", NamedTextColor.DARK_GRAY));
                    Component seg = Component.text("CP ", NamedTextColor.YELLOW)
                            .append(Component.text(doneCP + "/" + totalCPs, NamedTextColor.WHITE));
                    ab = ab.append(seg);
                    firstSeg = false;
                }
                if (abShowPit && mandatoryPitstops > 0) {
                    if (!firstSeg) ab = ab.append(Component.text("  ", NamedTextColor.DARK_GRAY));
                    int done = pitCount.getOrDefault(viewerId, 0);
                    Component seg = Component.text("Pit ", NamedTextColor.YELLOW)
                            .append(Component.text(done + "/" + mandatoryPitstops, NamedTextColor.WHITE));
                    ab = ab.append(seg);
                    firstSeg = false;
                }
                if (abShowTime) {
                    if (!firstSeg) ab = ab.append(Component.text("  ", NamedTextColor.DARK_GRAY));
                    Component seg = Component.text("Time ", NamedTextColor.YELLOW)
                            .append(Component.text(formatSeconds(timeFor(viewerState)), NamedTextColor.WHITE));
                    ab = ab.append(seg);
                }
                try { viewer.sendActionBar(ab); } catch (Exception ignored) { plugin.getLogger().finer("sendActionBar failed for " + viewer.getName() + ": " + ignored.getMessage()); }
            }
        }, 0L, 2L);
    }

    // Produce a readable player name. Prefer displayName but strip rank prefixes
    // and fall back to the account username. Preserve leading '.' for Bedrock.
    private String safeRenderName(Player online) {
        String account = online.getName();
        String raw;
        try {
            raw = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(online.displayName());
        } catch (Exception t) {
            // If displayName serialization fails, fall back to the account username.
            raw = account;
        }
        if (raw == null || raw.isEmpty()) raw = account;

        // Try to find the account name inside the raw display name (case-insensitive) and use from there
        String rawLower = raw.toLowerCase(java.util.Locale.ROOT);
        String accLower = account != null ? account.toLowerCase(java.util.Locale.ROOT) : "";
        String base;
        int idx = !accLower.isEmpty() ? rawLower.indexOf(accLower) : -1;
        if (idx >= 0) {
            base = raw.substring(idx).trim();
        } else {
            base = stripLeadingWrappers(raw).trim();
        }
    if (base == null || base.isEmpty()) base = account != null ? account : "";
    // Ensure leading '.' is visible for Bedrock (account name starts with '.')
    if (account != null && account.startsWith(".") && (base.isEmpty() || !base.startsWith("."))) {
            base = "." + base;
        }
    return base != null ? base : "";
    }

    // Remove leading bracketed wrappers like [Rank], (Rank), {Rank}, <Rank>, repeated
    private static String stripLeadingWrappers(String s) {
        String out = s;
        boolean changed = true;
        while (changed) {
            changed = false;
            String trimmed = out.trim();
            String next = trimmed.replaceFirst("^(?:\\[[^\\]]*\\]|\\([^)]*\\)|\\{[^}]*\\}|<[^>]*>)+\\s*", "");
            if (!next.equals(trimmed)) { out = next; changed = true; }
        }
        return out;
    }

    private void stopScoreboard() {
        if (scoreboardTask != null) {
            try { scoreboardTask.cancel(); } catch (Exception ignored) { plugin.getLogger().finer("Failed to cancel scoreboard task: " + ignored.getMessage()); }
            scoreboardTask = null;
        }
        java.util.Set<UUID> ids = new java.util.LinkedHashSet<>(scoreboards.keySet());
        ids.addAll(previousScoreboards.keySet());
        ids.addAll(sidebarDisabledPlayers);
        ids.addAll(overlayPlayers);
        ids.addAll(previousSidebarObjectives.keySet());
        for (UUID id : ids) {
            Player pl = Bukkit.getPlayer(id);
            if (pl != null) {
                Scoreboard our = scoreboards.get(id);
                Scoreboard prev = previousScoreboards.get(id);
                try {
                    if (overlayPlayers.contains(id)) {
                        restoreOverlaySidebar(pl, our, previousSidebarObjectives.get(id));
                        continue;
                    }
                    Scoreboard current = pl.getScoreboard();
                    boolean usingOurBoard = (our != null && current == our) || isBoatRacingBoard(current);
                    if (usingOurBoard) {
                        restorePreviousScoreboard(pl, prev);
                    }
                } catch (Exception ignored) {
                    plugin.getLogger().finer("Failed to restore previous scoreboard for " + pl.getName() + ": " + ignored.getMessage());
                }
            }
        }
        scoreboards.clear();
        previousScoreboards.clear();
        sidebarDisabledPlayers.clear();
        overlayPlayers.clear();
        previousSidebarObjectives.clear();
        restoreSimpleScoreForAll();
    }

    private void restoreSimpleScoreForAll() {
        if (simpleScoreHiddenByBoatRacing.isEmpty()) return;
        for (UUID id : new ArrayList<>(simpleScoreHiddenByBoatRacing)) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && p.isOnline()) {
                trySimpleScoreShow(p);
            }
        }
        simpleScoreHiddenByBoatRacing.clear();
    }

    private boolean trySimpleScoreHide(Player player) {
        return trySimpleScoreToggle(player, true);
    }

    private boolean trySimpleScoreShow(Player player) {
        return trySimpleScoreToggle(player, false);
    }

    private boolean trySimpleScoreToggle(Player player, boolean hide) {
        if (player == null || !player.isOnline()) return false;
        if (Bukkit.getPluginManager().getPlugin("SimpleScore") == null) return false;
        try {
            Class<?> managerClass = Class.forName("com.r4g3baby.simplescore.api.Manager");
            Method getInstance = managerClass.getMethod("getInstance");
            Object manager = getInstance.invoke(null);
            if (manager == null) return false;

            Method getViewer = managerClass.getMethod("getViewer", UUID.class);
            Object viewer = getViewer.invoke(manager, player.getUniqueId());
            if (viewer == null) return false;

            Method getPlatform = managerClass.getMethod("getPlatform");
            Object platform = getPlatform.invoke(manager);
            if (platform == null) return false;

            Method getProvider = platform.getClass().getMethod("getProvider");
            Object provider = getProvider.invoke(platform);
            if (provider == null) return false;

            String methodName = hide ? "hideScoreboard" : "showScoreboard";
            Method toggle = viewer.getClass().getMethod(methodName, provider.getClass());
            Object result = toggle.invoke(viewer, provider);
            if (result instanceof Boolean b) return b;
            return true;
        } catch (Throwable ignored) {
            plugin.getLogger().finer("SimpleScore hook " + (hide ? "hide" : "show") + " failed for " + player.getName() + ": " + ignored.getMessage());
            return false;
        }
    }

    private void restoreOverlaySidebar(Player player, Scoreboard board, Objective previousSidebar) {
        if (player == null || !player.isOnline() || board == null) return;
        try {
            Objective ours = board.getObjective("boatracing");
            if (previousSidebar != null) {
                previousSidebar.setDisplaySlot(DisplaySlot.SIDEBAR);
            } else if (ours != null && ours.getDisplaySlot() == DisplaySlot.SIDEBAR) {
                ours.setDisplaySlot(null);
            }
            if (ours != null) {
                try { ours.unregister(); } catch (Exception ignored) { plugin.getLogger().finer("Failed to unregister temporary objective: " + ignored.getMessage()); }
            }
        } catch (Exception ignored) {
            plugin.getLogger().finer("Failed to restore overlay sidebar for " + player.getName() + ": " + ignored.getMessage());
        }
    }

    private void restorePreviousScoreboard(Player player, Scoreboard previous) {
        ScoreboardManager sm = Bukkit.getScoreboardManager();
        if (sm == null) return;
        Scoreboard fallback = sm.getMainScoreboard();
        Scoreboard target = previous != null ? previous : fallback;
        if (target != null && isBoatRacingBoard(target)) target = fallback;
        if (target == null) return;

        // First break ownership by returning to the main board, then restore target.
        try { player.setScoreboard(fallback); } catch (Exception ignored) { plugin.getLogger().finer("Scoreboard fallback handoff failed for " + player.getName() + ": " + ignored.getMessage()); }

        restoreScoreboardAttempt(player, target, 1L);
        restoreScoreboardAttempt(player, target, 20L);
        restoreScoreboardAttempt(player, target, 60L);
        restoreScoreboardAttempt(player, target, 120L);
        restoreScoreboardAttempt(player, target, 200L);
    }

    private void restoreScoreboardAttempt(Player player, Scoreboard target, long delayTicks) {
        SchedulerCompat.runLater(plugin, () -> {
            if (player == null || !player.isOnline()) return;
            ScoreboardManager sm = Bukkit.getScoreboardManager();
            if (sm == null) return;
            Scoreboard current = player.getScoreboard();
            // If another plugin already restored a custom board, do not override it.
            boolean safeToApply = isBoatRacingBoard(current) || current == sm.getMainScoreboard() || current == null;
            if (!safeToApply || current == target) return;
            try {
                player.setScoreboard(target);
            } catch (Exception ignored) {
                plugin.getLogger().finer("Delayed scoreboard restore (" + delayTicks + "t) failed for " + player.getName() + ": " + ignored.getMessage());
            }
        }, Math.max(1L, delayTicks));
    }

    private boolean isBoatRacingBoard(Scoreboard sb) {
        if (sb == null) return false;
        try {
            Objective obj = sb.getObjective("boatracing");
            return obj != null && obj.getDisplaySlot() == DisplaySlot.SIDEBAR;
        } catch (Exception ignored) {
            return false;
        }
    }

    // Number hiding helpers removed by request

    private void broadcast(String msg) {
        for (Player p : Bukkit.getOnlinePlayers()) p.sendMessage(msg);
    }

    // removed unused sendTo helper

    private boolean isAdmin(Player p) {
        return p.hasPermission("boatracing.race.admin") || p.hasPermission("boatracing.setup");
    }

    private Collection<Player> participantsAndAdmins(Collection<Player> participants) {
        LinkedHashSet<Player> set = new LinkedHashSet<>(participants);
        for (Player op : Bukkit.getOnlinePlayers()) {
            if (isAdmin(op)) set.add(op);
        }
        return set;
    }

    // Helper: current participants from states map
    private Collection<Player> statesToPlayers() {
        LinkedHashSet<Player> set = new LinkedHashSet<>();
        for (UUID id : states.keySet()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && p.isOnline()) set.add(p);
        }
        return set;
    }
}
