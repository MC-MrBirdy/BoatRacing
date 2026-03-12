package es.jaie55.boatracing;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
// No TabExecutor needed: JavaPlugin already handles CommandExecutor and TabCompleter when overriding methods
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import es.jaie55.boatracing.team.TeamManager;
import es.jaie55.boatracing.ui.TeamGUI;
import es.jaie55.boatracing.util.Text;
import es.jaie55.boatracing.update.UpdateChecker;
import es.jaie55.boatracing.update.UpdateNotifier;
import es.jaie55.boatracing.track.TrackConfig;
import es.jaie55.boatracing.track.TrackLibrary;
import es.jaie55.boatracing.track.Region;
import es.jaie55.boatracing.track.SelectionUtils;
import es.jaie55.boatracing.race.RaceManager;
import es.jaie55.boatracing.reward.RewardManager;
import es.jaie55.boatracing.setup.SetupWizard;
import es.jaie55.boatracing.util.MessageManager;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

public class BoatRacingPlugin extends JavaPlugin {
    private static BoatRacingPlugin instance;
    private TeamManager teamManager;
    private TeamGUI teamGUI;
    private es.jaie55.boatracing.ui.AdminGUI adminGUI;
    private es.jaie55.boatracing.ui.AdminRaceGUI adminRaceGUI;
    private String prefix;
    private UpdateChecker updateChecker;
    private TrackConfig trackConfig;
    private TrackLibrary trackLibrary;
    private RaceManager raceManager;
    private RewardManager rewardManager;
    private SetupWizard setupWizard;
    private MessageManager messageManager;
    private es.jaie55.boatracing.ui.AdminTracksGUI tracksGUI;
    // Last latest-version announced in console due to 5-minute silent checks (to avoid duplicate prints)
    private volatile String lastConsoleAnnouncedVersion = null;
    // Track pending disband confirmations per player
    private final java.util.Set<java.util.UUID> pendingDisband = new java.util.HashSet<>();
    private final java.util.Map<java.util.UUID, java.util.UUID> pendingTransfer = new java.util.HashMap<>();
    private final java.util.Map<java.util.UUID, java.util.UUID> pendingKick = new java.util.HashMap<>();

    public static BoatRacingPlugin getInstance() { return instance; }
    public TeamManager getTeamManager() { return teamManager; }
    public String pref() { return prefix; }
    public es.jaie55.boatracing.ui.AdminGUI getAdminGUI() { return adminGUI; }
    public es.jaie55.boatracing.ui.AdminRaceGUI getAdminRaceGUI() { return adminRaceGUI; }
    public es.jaie55.boatracing.ui.TeamGUI getTeamGUI() { return teamGUI; }
    public RaceManager getRaceManager() { return raceManager; }
    public RewardManager getRewardManager() { return rewardManager; }
    public TrackConfig getTrackConfig() { return trackConfig; }
    public TrackLibrary getTrackLibrary() { return trackLibrary; }
    public es.jaie55.boatracing.ui.AdminTracksGUI getTracksGUI() { return tracksGUI; }
    public MessageManager msg() { return messageManager; }
    /** Whether the player can manage races on the currently loaded track (admin perms OR player-start flag). */
    public boolean canManageRace(Player p) {
        if (p.hasPermission("boatracing.race.admin") || p.hasPermission("boatracing.setup")) return true;
        boolean global = getConfig().getBoolean("player-actions.allow-player-race-start", false);
        return trackConfig.getRacingBoolean("allow-player-start", global);
    }
    

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        // Ensure new default keys are merged into existing config.yml on updates
        try {
            mergeConfigDefaults();
        } catch (Exception t) {
            // Log the problem but continue startup; specific exception types can be handled
            // if needed in the future.
            getLogger().warning("Failed to merge default config values: " + t.getMessage());
        }
        this.prefix = Text.colorize(getConfig().getString("prefix", "&6[BoatRacing] "));
        this.messageManager = new MessageManager(this);
        this.teamManager = new TeamManager(this);
    this.teamGUI = new TeamGUI(this);
    this.adminGUI = new es.jaie55.boatracing.ui.AdminGUI(this);
    this.adminRaceGUI = new es.jaie55.boatracing.ui.AdminRaceGUI(this);
    this.trackConfig = new TrackConfig(getDataFolder());
    this.trackLibrary = new TrackLibrary(this, getDataFolder(), trackConfig);
    this.raceManager = new RaceManager(this, trackConfig);
    this.rewardManager = new RewardManager(this);
    this.setupWizard = new SetupWizard(this);
    this.tracksGUI = new es.jaie55.boatracing.ui.AdminTracksGUI(this, trackLibrary);
    Bukkit.getPluginManager().registerEvents(teamGUI, this);
    Bukkit.getPluginManager().registerEvents(adminGUI, this);
    Bukkit.getPluginManager().registerEvents(tracksGUI, this);
    Bukkit.getPluginManager().registerEvents(adminRaceGUI, this);
    
    es.jaie55.boatracing.track.SelectionManager.init(this);
    Bukkit.getPluginManager().registerEvents(new es.jaie55.boatracing.track.WandListener(this), this);
    // Movement listener for race tracking (skip if player hasn't changed block — massive perf gain)
        Bukkit.getPluginManager().registerEvents(new org.bukkit.event.Listener() {
            @org.bukkit.event.EventHandler
            public void onMove(org.bukkit.event.player.PlayerMoveEvent e) {
                if (raceManager == null || !raceManager.isRunning()) return;
                if (e.getTo() == null) return;
                org.bukkit.Location from = e.getFrom();
                org.bukkit.Location to = e.getTo();
                if (from.getBlockX() == to.getBlockX()
                        && from.getBlockY() == to.getBlockY()
                        && from.getBlockZ() == to.getBlockZ()) return;
                raceManager.tickPlayer(e.getPlayer(), to);
            }
        }, this);
    
    try {
            boolean metricsEnabled = getConfig().getBoolean("bstats.enabled", true);
            if (metricsEnabled) {
                final int pluginId = 26881; // fixed bStats plugin id
                new org.bstats.bukkit.Metrics(this, pluginId);
                getLogger().info("Starting Metrics. Opt-out using the global bStats config.");
            }
        } catch (Exception t) {
            getLogger().warning("Failed to initialize bStats metrics: " + t.getMessage());
        }

    // ViaVersion integration and internal scoreboard number hiding removed by request

    // Updates
    if (getConfig().getBoolean("updates.enabled", true)) {
            String currentVersion = getDescription().getVersion();
            updateChecker = new UpdateChecker(this, "boatracing", currentVersion);
            updateChecker.checkAsync();
            // Post-result console notice (delayed)
            Bukkit.getScheduler().runTaskLater(this, () -> {
                if (updateChecker.isChecked() && updateChecker.isOutdated()) {
                    int behind = updateChecker.getBehindCount();
                    String current = currentVersion;
                    String latest = updateChecker.getLatestVersion() != null ? updateChecker.getLatestVersion() : "latest";
                    if (getConfig().getBoolean("updates.console-warn", true)) {
                        Bukkit.getLogger().warning("[" + getName() + "] An update is available. You are " + behind + " version(s) out of date.");
                        Bukkit.getLogger().warning("[" + getName() + "] You are running " + current + ", the latest version is " + latest + ".");
                        Bukkit.getLogger().warning("[" + getName() + "] Update at " + updateChecker.getLatestUrl());
                        // Record which latest was announced
                        lastConsoleAnnouncedVersion = updateChecker.getLatestVersion();
                    }
                }
            }, 20L * 5); // ~5s after enable
            // In-game notifications for admins
            if (getConfig().getBoolean("updates.notify-admins", true)) {
                Bukkit.getPluginManager().registerEvents(new UpdateNotifier(this, updateChecker, prefix), this);
            }
            // Periodic silent update checks every 5 minutes. If a NEW update is first detected here,
            // print a console WARN immediately (single time per version); hourly reminders handle repetition.
            long period = 20L * 60L * 5L; // 5 minutes
            Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> {
                    try {
                        if (!getConfig().getBoolean("updates.enabled", true)) return;
                        updateChecker.checkAsync();
                        // Evaluate result shortly after on the main thread to avoid race conditions
                        Bukkit.getScheduler().runTaskLater(this, () -> {
                            if (!getConfig().getBoolean("updates.enabled", true)) return;
                            if (!getConfig().getBoolean("updates.console-warn", true)) return;
                            if (updateChecker.isChecked() && updateChecker.isOutdated()) {
                                String latest = updateChecker.getLatestVersion();
                                if (latest != null && (lastConsoleAnnouncedVersion == null || !latest.equals(lastConsoleAnnouncedVersion))) {
                                    int behind = updateChecker.getBehindCount();
                                    String current = getDescription().getVersion();
                                    Bukkit.getLogger().warning("[" + getName() + "] An update is available. You are " + behind + " version(s) out of date.");
                                    Bukkit.getLogger().warning("[" + getName() + "] You are running " + current + ", the latest version is " + latest + ".");
                                    Bukkit.getLogger().warning("[" + getName() + "] Update at " + updateChecker.getLatestUrl());
                                    lastConsoleAnnouncedVersion = latest; // avoid duplicate console prints for the same version here
                                }
                            }
                        }, 20L * 8L);
                    } catch (Exception ignored) { getLogger().finer("Periodic update check failed: " + ignored.getMessage()); }
            }, period, period);
            // Console reminder every hour:
            // - Warn immediately if we already know we're outdated
            // - Trigger a fresh async check and then warn once when the result arrives (with retries to cover latency)
            // Hourly console reminder aligned to the top of each local hour (00:00, 01:00, 02:00, ...)
            java.time.ZonedDateTime now = java.time.ZonedDateTime.now();
            java.time.ZonedDateTime nextHour = now.withMinute(0).withSecond(0).withNano(0).plusHours(1);
            long delayTicks = Math.max(1L, java.time.Duration.between(now, nextHour).toMillis() / 50L);
            long hourly = 20L * 60L * 60L; // 1 hour
            Bukkit.getScheduler().runTaskTimer(this, () -> {
                if (!getConfig().getBoolean("updates.enabled", true)) return;
                if (!getConfig().getBoolean("updates.console-warn", true)) return;
                try { updateChecker.checkAsync(); } catch (Exception ignored) { getLogger().finer("Update check failed: " + ignored.getMessage()); }
                Bukkit.getScheduler().runTaskLater(this, () -> {
                    if (!getConfig().getBoolean("updates.enabled", true)) return;
                    if (!getConfig().getBoolean("updates.console-warn", true)) return;
                    if (updateChecker.isChecked() && updateChecker.isOutdated()) {
                        int behind = updateChecker.getBehindCount();
                        String current = getDescription().getVersion();
                        String latest = updateChecker.getLatestVersion() != null ? updateChecker.getLatestVersion() : "latest";
                        Bukkit.getLogger().warning("[" + getName() + "] An update is available. You are " + behind + " version(s) out of date.");
                        Bukkit.getLogger().warning("[" + getName() + "] You are running " + current + ", the latest version is " + latest + ".");
                        Bukkit.getLogger().warning("[" + getName() + "] Update at " + updateChecker.getLatestUrl());
                    }
                }, 20L * 10L);
            }, delayTicks, hourly);
        }

    if (getCommand("boatracing") != null) {
            getCommand("boatracing").setExecutor(this);
            getCommand("boatracing").setTabCompleter(this);
        }
    getLogger().info("BoatRacing enabled");
    }

    // Merge default config.yml values into the existing config without overwriting user changes
    private void mergeConfigDefaults() {
        InputStream is = getResource("config.yml");
        if (is == null) return;
        YamlConfiguration def = YamlConfiguration.loadConfiguration(new InputStreamReader(is, StandardCharsets.UTF_8));
        FileConfiguration cfg = getConfig();
        cfg.addDefaults(def);
        cfg.options().copyDefaults(true);
        saveConfig();
    }


    // Resolve an OfflinePlayer without remote lookups: prefer online, then cache, or UUID literal
    private org.bukkit.OfflinePlayer resolveOffline(String token) {
        if (token == null || token.isEmpty()) return null;
        // 1) Exact online match
        org.bukkit.entity.Player online = Bukkit.getPlayerExact(token);
        if (online != null) return online;
        // 2) Try UUID literal
        try {
            java.util.UUID uid = java.util.UUID.fromString(token);
            return Bukkit.getOfflinePlayer(uid);
        } catch (IllegalArgumentException ignored) {}
        // 3) Try offline cache entries by name (case-insensitive)
        for (org.bukkit.OfflinePlayer op : Bukkit.getOfflinePlayers()) {
            if (op.getName() != null && op.getName().equalsIgnoreCase(token)) return op;
        }
        // Not found locally
        return null;
    }

    @Override
    public void onDisable() {
        if (teamManager != null) teamManager.save();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(Text.colorize(prefix + msg().get("general.players-only")));
            return true;
        }
        Player p = (Player) sender;
        if (command.getName().equalsIgnoreCase("boatracing")) {
            if (args.length == 0) {
                p.sendMessage(Text.colorize(prefix + msg().get("race.usage.main", "label", label)));
                return true;
            }
            // /boatracing version
            if (args[0].equalsIgnoreCase("version")) {
                if (!p.hasPermission("boatracing.version")) {
                    p.sendMessage(Text.colorize(prefix + msg().get("general.no-permission")));
                    p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.6f);
                    return true;
                }
                String current = getDescription().getVersion();
                java.util.List<String> authors = getDescription().getAuthors();
                p.sendMessage(Text.colorize(prefix + msg().get("plugin.version", "name", getName(), "version", current)));
                if (!authors.isEmpty()) {
                    p.sendMessage(Text.colorize(prefix + msg().get("plugin.authors", "authors", String.join(", ", authors))));
                }
                

                boolean updatesEnabled = getConfig().getBoolean("updates.enabled", true);
                if (!updatesEnabled) {
                    p.sendMessage(Text.colorize(prefix + msg().get("update.disabled")));
                    return true;
                }

                // Ensure we have a checker and run one if needed
                if (updateChecker == null) {
                    updateChecker = new UpdateChecker(this, "boatracing", current);
                }
                if (!updateChecker.isChecked()) {
                    p.sendMessage(Text.colorize(prefix + msg().get("update.checking")));
                    updateChecker.checkAsync();
                    // Poll a couple of times to deliver result to the user shortly after
                    Bukkit.getScheduler().runTaskLater(this, () -> sendUpdateStatus(p), 40L);
                    Bukkit.getScheduler().runTaskLater(this, () -> sendUpdateStatus(p), 100L);
                    return true;
                }
                // Already have a result
                sendUpdateStatus(p);
                return true;
            }
            if (args[0].equalsIgnoreCase("reload")) {
                if (!p.hasPermission("boatracing.reload")) {
                    p.sendMessage(Text.colorize(prefix + msg().get("general.no-permission")));
                    p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.6f);
                    return true;
                }
                // Persist current state, reload config and data
                if (teamManager != null) teamManager.save();
                reloadConfig();
                // After reload, also merge any new defaults into config.yml
                try { mergeConfigDefaults(); } catch (Exception ignored) { getLogger().finer("mergeConfigDefaults() during reload failed: " + ignored.getMessage()); }
                this.prefix = Text.colorize(getConfig().getString("prefix", "&6[BoatRacing] "));
                this.messageManager.reload();
                // Recreate team manager to re-read data and settings
                this.teamManager = new TeamManager(this);
                // ViaVersion integration removed; nothing to re-apply
                p.sendMessage(Text.colorize(prefix + msg().get("plugin.reloaded")));
                p.playSound(p.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 0.9f, 1.1f);
                return true;
            }
            // /boatracing race
            if (args[0].equalsIgnoreCase("race")) {
                if (args.length == 1 || args[1].equalsIgnoreCase("help")) {
                    p.sendMessage(Text.colorize(prefix + msg().get("race.help.header")));
                    p.sendMessage(Text.colorize(msg().get("race.help.join", "label", label)));
                    p.sendMessage(Text.colorize(msg().get("race.help.leave", "label", label)));
                    p.sendMessage(Text.colorize(msg().get("race.help.status", "label", label)));
                    if (p.hasPermission("boatracing.race.admin") || p.hasPermission("boatracing.setup")
                            || getConfig().getBoolean("player-actions.allow-player-race-start", false)) {
                        p.sendMessage(Text.colorize(msg().get("race.help.admin", "label", label)));
                    }
                    return true;
                }
                switch (args[1].toLowerCase()) {
                    case "open" -> {
                        if (args.length < 3) { p.sendMessage(Text.colorize(prefix + msg().get("race.usage.open", "label", label))); return true; }
                        String tname = args[2];
                        if (!trackLibrary.exists(tname)) { p.sendMessage(Text.colorize(prefix + msg().get("race.track-not-found", "track", tname))); return true; }
                        if (!trackLibrary.select(tname)) { p.sendMessage(Text.colorize(prefix + msg().get("race.track-load-failed", "track", tname))); return true; }
                        if (!canManageRace(p)) {
                            p.sendMessage(Text.colorize(prefix + msg().get("general.no-permission")));
                            p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.6f);
                            return true;
                        }
                        if (!trackConfig.isReady()) {
                            p.sendMessage(Text.colorize(prefix + msg().get("race.track-not-ready", "requirements", String.join(", ", trackConfig.missingRequirements()))));
                            p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.6f);
                            return true;
                        }
                        raceManager.loadSettings();
                        int laps = raceManager.getTotalLaps();
                        boolean ok = raceManager.openRegistration(laps, null);
                        if (!ok) p.sendMessage(Text.colorize(prefix + msg().get("race.cannot-open-registration")));
                        return true;
                    }
                    case "join" -> {
                        if (args.length < 3) { p.sendMessage(Text.colorize(prefix + msg().get("race.usage.join", "label", label))); return true; }
                        String tname = args[2];
                        if (!trackLibrary.exists(tname)) { p.sendMessage(Text.colorize(prefix + msg().get("race.track-not-found", "track", tname))); return true; }
                        if (!trackLibrary.select(tname)) { p.sendMessage(Text.colorize(prefix + msg().get("race.track-load-failed", "track", tname))); return true; }
                        if (!trackConfig.isReady()) {
                            p.sendMessage(Text.colorize(prefix + msg().get("race.track-not-ready", "requirements", String.join(", ", trackConfig.missingRequirements()))));
                            return true;
                        }
                        // Must be in a team
                        if (teamManager.getTeamByMember(p.getUniqueId()).isEmpty()) {
                            p.sendMessage(Text.colorize(prefix + msg().get("race.must-be-in-team")));
                            p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.6f);
                            return true;
                        }
                        if (!raceManager.join(p)) {
                            p.sendMessage(Text.colorize(prefix + msg().get("race.registration.not-open")));
                        }
                        return true;
                    }
                    case "leave" -> {
                        if (args.length < 3) { p.sendMessage(Text.colorize(prefix + msg().get("race.usage.leave", "label", label))); return true; }
                        String tname = args[2];
                        if (!trackLibrary.exists(tname)) { p.sendMessage(Text.colorize(prefix + msg().get("race.track-not-found", "track", tname))); return true; }
                        if (!trackLibrary.select(tname)) { p.sendMessage(Text.colorize(prefix + msg().get("race.track-load-failed", "track", tname))); return true; }
                        boolean removed = raceManager.leave(p);
                        if (!removed) {
                            if (!raceManager.isRegistering()) {
                                p.sendMessage(Text.colorize(prefix + msg().get("race.registration.not-open")));
                            } else {
                                p.sendMessage(Text.colorize(prefix + msg().get("race.registration.not-registered")));
                            }
                        }
                        return true;
                    }
                    case "force" -> {
                        if (args.length < 3) { p.sendMessage(Text.colorize(prefix + msg().get("race.usage.force", "label", label))); return true; }
                        String tname = args[2];
                        if (!trackLibrary.exists(tname)) { p.sendMessage(Text.colorize(prefix + msg().get("race.track-not-found", "track", tname))); return true; }
                        if (!trackLibrary.select(tname)) { p.sendMessage(Text.colorize(prefix + msg().get("race.track-load-failed", "track", tname))); return true; }
                        if (!canManageRace(p)) {
                            p.sendMessage(Text.colorize(prefix + msg().get("general.no-permission")));
                            p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.6f);
                            return true;
                        }
                        raceManager.loadSettings();
                        if (raceManager.getRegistered().isEmpty()) {
                            p.sendMessage(Text.colorize(prefix + msg().get("race.no-participants", "label", label)));
                            p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.6f);
                            return true;
                        }
                        raceManager.forceStart();
                        return true;
                    }
                    case "start" -> {
                        if (args.length < 3) { p.sendMessage(Text.colorize(prefix + msg().get("race.usage.start", "label", label))); return true; }
                        String tname = args[2];
                        if (!trackLibrary.exists(tname)) { p.sendMessage(Text.colorize(prefix + msg().get("race.track-not-found", "track", tname))); return true; }
                        if (!trackLibrary.select(tname)) { p.sendMessage(Text.colorize(prefix + msg().get("race.track-load-failed", "track", tname))); return true; }
                        if (!canManageRace(p)) {
                            p.sendMessage(Text.colorize(prefix + msg().get("general.no-permission")));
                            p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.6f);
                            return true;
                        }
                        if (!trackConfig.isReady()) {
                            p.sendMessage(Text.colorize(prefix + msg().get("race.track-not-ready", "requirements", String.join(", ", trackConfig.missingRequirements()))));
                            p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.6f);
                            return true;
                        }
                        if (raceManager.isRunning()) { p.sendMessage(Text.colorize(prefix + msg().get("race.already-running"))); return true; }
                        raceManager.loadSettings();
                        // Build participants: strictly registered participants only
                        java.util.List<org.bukkit.entity.Player> participants = new java.util.ArrayList<>();
                        java.util.Set<java.util.UUID> regs = new java.util.LinkedHashSet<>(raceManager.getRegistered());
                        for (java.util.UUID id : regs) {
                            org.bukkit.entity.Player rp = Bukkit.getPlayer(id);
                            if (rp != null && rp.isOnline()) participants.add(rp);
                        }
                        if (participants.isEmpty()) {
                            p.sendMessage(Text.colorize(prefix + msg().get("race.no-participants", "label", label)));
                            return true;
                        }
                        // Place with boats and start
                        java.util.List<org.bukkit.entity.Player> placed = raceManager.placeAtStartsWithBoats(participants);
                        if (placed.isEmpty()) { p.sendMessage(Text.colorize(prefix + msg().get("race.no-start-slots"))); return true; }
                        if (placed.size() < participants.size()) { p.sendMessage(Text.colorize(prefix + msg().get("race.some-not-placed"))); }
                        // Use start lights countdown if configured
                        raceManager.startRaceWithCountdown(placed);
                        return true;
                    }
                    case "stop" -> {
                        if (args.length < 3) { p.sendMessage(Text.colorize(prefix + msg().get("race.usage.stop", "label", label))); return true; }
                        String tname = args[2];
                        if (!trackLibrary.exists(tname)) { p.sendMessage(Text.colorize(prefix + msg().get("race.track-not-found", "track", tname))); return true; }
                        if (!trackLibrary.select(tname)) { p.sendMessage(Text.colorize(prefix + msg().get("race.track-load-failed", "track", tname))); return true; }
                        if (!canManageRace(p)) {
                            p.sendMessage(Text.colorize(prefix + msg().get("general.no-permission")));
                            p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.6f);
                            return true;
                        }
                        boolean any = false;
                        if (raceManager.isRegistering()) {
                            any |= raceManager.cancelRegistration(true);
                        }
                        if (raceManager.isRunning()) {
                            any |= raceManager.cancelRace();
                        }
                        if (!any) {
                            p.sendMessage(Text.colorize(prefix + msg().get("setup.nothing-to-stop")));
                        }
                        return true;
                    }
                    case "status" -> {
                        if (args.length < 3) { p.sendMessage(Text.colorize(prefix + msg().get("race.usage.status", "label", label))); return true; }
                        String tname = args[2];
                        if (!trackLibrary.exists(tname)) { p.sendMessage(Text.colorize(prefix + msg().get("race.track-not-found", "track", tname))); return true; }
                        if (!trackLibrary.select(tname)) { p.sendMessage(Text.colorize(prefix + msg().get("race.track-load-failed", "track", tname))); return true; }
                        String cur = (getTrackLibrary() != null && getTrackLibrary().getCurrent() != null) ? getTrackLibrary().getCurrent() : msg().get("general.unsaved");
                        boolean running = raceManager.isRunning();
                        boolean registering = raceManager.isRegistering();
                        int regs = raceManager.getRegistered().size();
                        int laps = raceManager.getTotalLaps();
                        int participants = running ? raceManager.getParticipants().size() : 0;
                        int starts = trackConfig.getStarts().size();
                        int lights = trackConfig.getLights().size();
                        int cps = trackConfig.getCheckpoints().size();
                        boolean hasFinish = trackConfig.getFinish() != null;
                        boolean hasPit = trackConfig.getPitlane() != null;
                        boolean ready = trackConfig.isReady();
                        java.util.List<String> missing = ready ? java.util.Collections.emptyList() : trackConfig.missingRequirements();

                        p.sendMessage(Text.colorize(prefix + msg().get("race.status.header")));
                        p.sendMessage(Text.colorize(msg().get("race.status.track", "track", cur)));
                        p.sendMessage(Text.colorize(running ? msg().get("race.status.running", "count", participants) : msg().get("race.status.not-running")));
                        p.sendMessage(Text.colorize(registering ? msg().get("race.status.registration-open", "count", regs) : msg().get("race.status.registration-closed")));
                        p.sendMessage(Text.colorize(msg().get("race.status.laps", "count", laps)));
                        p.sendMessage(Text.colorize(msg().get("race.status.starts-lights", "starts", starts, "lights", lights, "finish", msg().get(hasFinish ? "general.yes" : "general.no"), "pit", msg().get(hasPit ? "general.yes" : "general.no"))));
                        p.sendMessage(Text.colorize(msg().get("race.status.checkpoints", "count", cps)));
                        p.sendMessage(Text.colorize(msg().get("race.status.mandatory-pitstops", "count", raceManager.getMandatoryPitstops())));
                        if (ready) {
                            p.sendMessage(Text.colorize(msg().get("race.status.track-ready")));
                        } else {
                            p.sendMessage(Text.colorize(msg().get("race.track-not-ready", "requirements", String.join(", ", missing))));
                        }
                        return true;
                    }
                    default -> { p.sendMessage(Text.colorize(prefix + msg().get("race.unknown-subcommand", "label", label))); return true; }
                }
            }
            // /boatracing setup
            if (args[0].equalsIgnoreCase("setup")) {
                if (!p.hasPermission("boatracing.setup")) {
                    p.sendMessage(Text.colorize(prefix + msg().get("general.no-permission")));
                    p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.6f);
                    return true;
                }
                if (args.length == 1 || args[1].equalsIgnoreCase("help")) {
                    p.sendMessage(Text.colorize(prefix + msg().get("setup.usage.main")));
                    p.sendMessage(Text.colorize(msg().get("setup.usage.cmd-addstart", "label", label)));
                    p.sendMessage(Text.colorize(msg().get("setup.usage.cmd-clearstarts", "label", label)));
                    p.sendMessage(Text.colorize(msg().get("setup.usage.cmd-setfinish", "label", label)));
                    p.sendMessage(Text.colorize(msg().get("setup.usage.cmd-setpit", "label", label)));
                    p.sendMessage(Text.colorize(msg().get("setup.usage.cmd-addcheckpoint", "label", label)));
                    p.sendMessage(Text.colorize(msg().get("setup.usage.cmd-addlight", "label", label)));
                    p.sendMessage(Text.colorize(msg().get("setup.usage.cmd-clearlights", "label", label)));
                    p.sendMessage(Text.colorize(msg().get("setup.usage.cmd-setlaps", "label", label)));
                    p.sendMessage(Text.colorize(msg().get("setup.usage.cmd-setpitstops", "label", label)));
                    p.sendMessage(Text.colorize(msg().get("setup.usage.cmd-setpos", "label", label)));
                    p.sendMessage(Text.colorize(msg().get("setup.usage.cmd-clearpos", "label", label)));
                    p.sendMessage(Text.colorize(msg().get("setup.usage.cmd-clearcheckpoints", "label", label)));
                    p.sendMessage(Text.colorize(msg().get("setup.usage.cmd-show", "label", label)));
                    p.sendMessage(Text.colorize(msg().get("setup.usage.cmd-selinfo", "label", label)));
                    p.sendMessage(Text.colorize(msg().get("setup.usage.cmd-wand", "label", label)));
                    p.sendMessage(Text.colorize(msg().get("setup.usage.cmd-wizard", "label", label)));
                    return true;
                }
                String sub = args[1].toLowerCase();
                switch (sub) {
                    case "wand" -> {
                        es.jaie55.boatracing.track.SelectionManager.giveWand(p);
                        p.sendMessage(Text.colorize(prefix + msg().get("setup.wand-ready")));
                        p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_ITEM_PICKUP, 0.9f, 1.2f);
                        return true;
                    }
                    case "wizard" -> {
                        // Guided setup assistant with simple sub-actions
                        if (args.length >= 3) {
                            String action = args[2].toLowerCase();
                            switch (action) {
                                case "finish" -> setupWizard.finish(p);
                                case "back" -> setupWizard.back(p);
                                case "status" -> setupWizard.status(p);
                                case "cancel" -> setupWizard.cancel(p);
                                case "skip" -> setupWizard.skip(p); // only works on optional steps
                                case "next" -> setupWizard.next(p);
                                default -> setupWizard.start(p);
                            }
                        } else if (setupWizard.isActive(p)) {
                            setupWizard.status(p);
                        } else {
                            setupWizard.start(p);
                        }
                        return true;
                    }
                    case "addstart" -> {
                        org.bukkit.Location loc = p.getLocation();
                        trackConfig.addStart(loc);
                        p.sendMessage(Text.colorize(prefix + msg().get("setup.start-added", "x", loc.getBlockX(), "y", loc.getBlockY(), "z", loc.getBlockZ(), "world", loc.getWorld().getName())));
                        p.sendMessage(Text.colorize(msg().get("setup.start-added-tip")));
                        p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.2f);
                        if (setupWizard != null) setupWizard.afterAction(p);
                    }
                    case "clearstarts" -> {
                        trackConfig.clearStarts();
                        p.sendMessage(Text.colorize(prefix + msg().get("setup.starts-cleared")));
                        p.playSound(p.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 0.9f, 1.1f);
                        if (setupWizard != null) setupWizard.afterAction(p);
                    }
                    case "setfinish" -> {
                        var sel = SelectionUtils.getSelectionDetailed(p);
                        if (sel == null) {
                            p.sendMessage(Text.colorize(prefix + msg().get("setup.no-selection")));
                            p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.6f);
                            return true;
                        }
                        Region r = new Region(sel.worldName, sel.box);
                        trackConfig.setFinish(r);
                        p.sendMessage(Text.colorize(prefix + msg().get("setup.finish-set", "box", fmtBox(sel.box))));
                        p.playSound(p.getLocation(), org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.3f);
                        if (setupWizard != null) setupWizard.afterAction(p);
                    }
                    case "setpit" -> {
                        var sel = SelectionUtils.getSelectionDetailed(p);
                        if (sel == null) {
                            p.sendMessage(Text.colorize(prefix + msg().get("setup.no-selection")));
                            p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.6f);
                            return true;
                        }
                        Region r = new Region(sel.worldName, sel.box);
                        if (args.length >= 3) {
                            // Join the rest of tokens to support names with spaces; allow quoted names
                            String raw = String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length)).trim();
                            String teamName = raw;
                            if ((teamName.startsWith("\"") && teamName.endsWith("\"")) || (teamName.startsWith("'") && teamName.endsWith("'"))) {
                                teamName = teamName.substring(1, teamName.length()-1);
                            }
                            var ot = teamManager.findByName(teamName);
                            if (ot.isEmpty()) { p.sendMessage(Text.colorize(prefix + msg().get("setup.team-not-found"))); return true; }
                            trackConfig.setTeamPit(ot.get().getId(), r);
                            p.sendMessage(Text.colorize(prefix + msg().get("setup.pit-set-team", "team", ot.get().getName(), "box", fmtBox(sel.box))));
                        } else {
                            trackConfig.setPitlane(r);
                            p.sendMessage(Text.colorize(prefix + msg().get("setup.pit-set-default", "box", fmtBox(sel.box))));
                        }
                        p.playSound(p.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 0.9f, 1.2f);
                        if (setupWizard != null) setupWizard.afterAction(p);
                        return true;
                    }
                    case "addcheckpoint" -> {
                        var sel = SelectionUtils.getSelectionDetailed(p);
                        if (sel == null) {
                            p.sendMessage(Text.colorize(prefix + msg().get("setup.no-selection")));
                            p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.6f);
                            return true;
                        }
                        Region r = new Region(sel.worldName, sel.box);
                        trackConfig.addCheckpoint(r);
                        p.sendMessage(Text.colorize(prefix + msg().get("setup.checkpoint-added", "num", trackConfig.getCheckpoints().size(), "box", fmtBox(sel.box))));
                        p.sendMessage(Text.colorize(msg().get("setup.checkpoint-added-tip")));
                        p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.2f);
                        if (setupWizard != null) setupWizard.afterAction(p);
                    }
                    case "addlight" -> {
                        org.bukkit.block.Block target = p.getTargetBlockExact(6);
                        if (target == null) {
                            p.sendMessage(Text.colorize(prefix + msg().get("setup.light-look")));
                            p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.6f);
                            return true;
                        }
                        boolean ok = trackConfig.addLight(target);
                        if (!ok) {
                            p.sendMessage(Text.colorize(prefix + msg().get("setup.light-fail")));
                            p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.6f);
                            return true;
                        }
                        p.sendMessage(Text.colorize(prefix + msg().get("setup.light-added", "x", target.getX(), "y", target.getY(), "z", target.getZ())));
                        p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.2f);
                        if (setupWizard != null) setupWizard.afterAction(p);
                    }
                    case "clearlights" -> {
                        trackConfig.clearLights();
                        p.sendMessage(Text.colorize(prefix + msg().get("setup.lights-cleared")));
                        p.playSound(p.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 0.9f, 1.1f);
                        if (setupWizard != null) setupWizard.afterAction(p);
                    }
                    case "setlaps" -> {
                        if (args.length < 3 || !args[2].matches("\\d+")) {
                            p.sendMessage(Text.colorize(prefix + msg().get("setup.error.setlaps", "label", label)));
                            return true;
                        }
                        int laps = Math.max(1, Integer.parseInt(args[2]));
                        raceManager.setTotalLaps(laps);
                        trackConfig.setRacingOverride("laps", laps);
                        String tlName = trackLibrary.getCurrent();
                        p.sendMessage(Text.colorize(prefix + msg().get("setup.laps-set", "laps", laps, "track_info", (tlName != null ? msg().get("setup.track-info", "track", tlName) : ""))));
                        p.playSound(p.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 0.9f, 1.2f);
                        if (setupWizard != null) setupWizard.afterAction(p);
                        return true;
                    }
                    case "setpitstops" -> {
                        if (args.length < 3 || !args[2].matches("\\d+")) {
                            p.sendMessage(Text.colorize(prefix + msg().get("setup.error.setpitstops", "label", label)));
                            return true;
                        }
                        int req = Math.max(0, Integer.parseInt(args[2]));
                        raceManager.setMandatoryPitstops(req);
                        trackConfig.setRacingOverride("mandatory-pitstops", req);
                        String tlNamePit = trackLibrary.getCurrent();
                        p.sendMessage(Text.colorize(prefix + msg().get("setup.pitstops-set", "count", req, "track_info", (tlNamePit != null ? msg().get("setup.track-info", "track", tlNamePit) : ""))));
                        p.playSound(p.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 0.9f, 1.2f);
                        if (setupWizard != null) setupWizard.afterAction(p);
                        return true;
                    }
                    case "setpos" -> {
                        if (args.length < 4) { p.sendMessage(Text.colorize(prefix + msg().get("setup.error.setpos", "label", label))); return true; }
                        org.bukkit.OfflinePlayer off = resolveOffline(args[2]);
                        if (off == null || off.getUniqueId() == null) { p.sendMessage(Text.colorize(prefix + msg().get("setup.player-not-found"))); return true; }
                        String slotArg = args[3];
                        if (slotArg.equalsIgnoreCase("auto")) {
                            trackConfig.clearCustomStartSlot(off.getUniqueId());
                            p.sendMessage(Text.colorize(prefix + msg().get("setup.setpos-removed", "player", (off.getName()!=null?off.getName():off.getUniqueId().toString()))));
                        } else if (slotArg.matches("\\d+")) {
                            int oneBased = Integer.parseInt(slotArg);
                            if (oneBased < 1 || oneBased > trackConfig.getStarts().size()) { p.sendMessage(Text.colorize(prefix + msg().get("setup.setpos-invalid", "max", trackConfig.getStarts().size()))); return true; }
                            trackConfig.setCustomStartSlot(off.getUniqueId(), oneBased - 1);
                            p.sendMessage(Text.colorize(prefix + msg().get("setup.setpos-set", "player", (off.getName()!=null?off.getName():off.getUniqueId().toString()), "slot", oneBased)));
                        } else {
                            p.sendMessage(Text.colorize(prefix + msg().get("setup.error.setpos", "label", label)));
                        }
                        return true;
                    }
                    case "clearpos" -> {
                        if (args.length < 3) { p.sendMessage(Text.colorize(prefix + msg().get("setup.error.clearpos", "label", label))); return true; }
                        org.bukkit.OfflinePlayer off = resolveOffline(args[2]);
                        if (off == null || off.getUniqueId() == null) { p.sendMessage(Text.colorize(prefix + msg().get("setup.player-not-found"))); return true; }
                        trackConfig.clearCustomStartSlot(off.getUniqueId());
                        p.sendMessage(Text.colorize(prefix + msg().get("setup.setpos-removed", "player", (off.getName()!=null?off.getName():off.getUniqueId().toString()))));
                        return true;
                    }
                    case "clearcheckpoints" -> {
                        trackConfig.clearCheckpoints();
                        p.sendMessage(Text.colorize(prefix + msg().get("setup.checkpoints-cleared")));
                        p.playSound(p.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 0.9f, 1.1f);
                        if (setupWizard != null) setupWizard.afterAction(p);
                    }
                    case "show" -> {
                        int starts = trackConfig.getStarts().size();
                        int lights = trackConfig.getLights().size();
                        int cps = trackConfig.getCheckpoints().size();
                        boolean hasFinish = trackConfig.getFinish() != null;
                        boolean hasPit = trackConfig.getPitlane() != null;
                        int teamPitCount = trackConfig.getTeamPits().size();
                        int customStarts = trackConfig.getCustomStartSlots().size();
                        p.sendMessage(Text.colorize(prefix + msg().get("setup.show.header")));
                        String tname = (getTrackLibrary() != null && getTrackLibrary().getCurrent() != null) ? getTrackLibrary().getCurrent() : msg().get("general.unsaved");
                        p.sendMessage(Text.colorize(msg().get("setup.show.track", "track", tname)));
                        p.sendMessage(Text.colorize(msg().get("setup.show.starts", "count", starts)));
                        p.sendMessage(Text.colorize(msg().get("setup.show.lights", "count", lights)));
                        p.sendMessage(Text.colorize(msg().get("setup.show.finish", "status", msg().get(hasFinish ? "general.yes" : "general.no"))));
                        p.sendMessage(Text.colorize(msg().get("setup.show.pit-default", "status", msg().get(hasPit ? "general.yes" : "general.no"))));
                        p.sendMessage(Text.colorize(msg().get("setup.show.pit-teams", "info", (teamPitCount > 0 ? msg().get("setup.show.info-configured", "count", teamPitCount) : msg().get("general.none")))));
                        p.sendMessage(Text.colorize(msg().get("setup.show.custom-starts", "info", (customStarts > 0 ? msg().get("setup.show.info-players", "count", customStarts) : msg().get("general.none")))));
                        p.sendMessage(Text.colorize(msg().get("setup.show.checkpoints", "count", cps)));
                        p.sendMessage(Text.colorize(msg().get("setup.show.pitstops", "count", raceManager.getMandatoryPitstops())));
                        // Show per-track racing overrides if any
                        java.util.Map<String, Object> overrides = trackConfig.getRacingOverrides();
                        if (!overrides.isEmpty()) {
                            p.sendMessage(Text.colorize(msg().get("setup.show.overrides-header")));
                            for (java.util.Map.Entry<String, Object> oe : overrides.entrySet()) {
                                p.sendMessage(Text.colorize(msg().get("setup.show.override-entry", "key", oe.getKey(), "value", oe.getValue())));
                            }
                        }
                    }
                    case "selinfo" -> {
                        java.util.List<String> dump = SelectionUtils.debugSelection(p);
                        p.sendMessage(Text.colorize(prefix + msg().get("setup.selection-info")));
                        for (String line : dump) p.sendMessage(Text.colorize(msg().get("setup.show.selection-entry", "line", line)));
                    }
                    default -> p.sendMessage(Text.colorize(prefix + msg().get("setup.error.unknown-subcommand", "label", label)));
                }
                return true;
            }
            // /boatracing admin
            if (args[0].equalsIgnoreCase("admin")) {
                if (!p.hasPermission("boatracing.admin")) {
                    p.sendMessage(Text.colorize(prefix + msg().get("general.no-permission")));
                    return true;
                }
                if (args.length == 1) {
                    // Open Admin GUI by default
                    adminGUI.openMain(p);
                    return true;
                }
                if (args[1].equalsIgnoreCase("help")) {
                    p.sendMessage(Text.colorize(prefix + msg().get("admin.help.header")));
                    p.sendMessage(Text.colorize(msg().get("admin.help.team-create", "label", label)));
                    p.sendMessage(Text.colorize(msg().get("admin.help.team-delete", "label", label)));
                    p.sendMessage(Text.colorize(msg().get("admin.help.team-rename", "label", label)));
                    p.sendMessage(Text.colorize(msg().get("admin.help.team-color", "label", label)));
                    p.sendMessage(Text.colorize(msg().get("admin.help.team-add", "label", label)));
                    p.sendMessage(Text.colorize(msg().get("admin.help.team-remove", "label", label)));
                    p.sendMessage(Text.colorize(msg().get("admin.help.player-setteam", "label", label)));
                    p.sendMessage(Text.colorize(msg().get("admin.help.player-setnumber", "label", label)));
                    p.sendMessage(Text.colorize(msg().get("admin.help.player-setboat", "label", label)));
                    p.sendMessage(Text.colorize(msg().get("admin.help.tracks", "label", label)));
                    return true;
                }
                if (args[1].equalsIgnoreCase("tracks")) {
                    if (!p.hasPermission("boatracing.setup")) { p.sendMessage(Text.colorize(prefix + msg().get("general.no-permission"))); return true; }
                    tracksGUI.open(p);
                    return true;
                }
                // admin team ...
                if (args[1].equalsIgnoreCase("team")) {
                    if (args.length < 3) { p.sendMessage(Text.colorize(prefix + msg().get("admin.usage.team-main", "label", label))); return true; }
                    String op = args[2].toLowerCase();
                    switch (op) {
                        case "create" -> {
                            if (args.length < 4) { p.sendMessage(Text.colorize(prefix + msg().get("admin.usage.team-create", "label", label))); return true; }
                            String name = args[3];
                            org.bukkit.DyeColor color = org.bukkit.DyeColor.WHITE;
                            if (args.length >= 5) {
                                try { color = org.bukkit.DyeColor.valueOf(args[4].toUpperCase()); } catch (Exception ex) { p.sendMessage(Text.colorize(prefix + msg().get("admin.invalid-color"))); return true; }
                            }
                            java.util.UUID firstMemberId = p.getUniqueId();
                            if (args.length >= 6) {
                                org.bukkit.OfflinePlayer off = Bukkit.getOfflinePlayer(args[5]);
                                if (off == null || off.getUniqueId() == null) { p.sendMessage(Text.colorize(prefix + msg().get("admin.player-not-found"))); return true; }
                                firstMemberId = off.getUniqueId();
                            }
                            if (teamManager.findByName(name).isPresent()) { p.sendMessage(Text.colorize(prefix + msg().get("admin.team-name-exists"))); return true; }
                            teamManager.createTeam(firstMemberId, name, color);
                            p.sendMessage(Text.colorize(prefix + msg().get("admin.team-created-colored", "name", name, "color", color.name())));
                            return true;
                        }
                        case "delete" -> {
                            if (args.length < 4) { p.sendMessage(Text.colorize(prefix + msg().get("admin.usage.team-delete", "label", label))); return true; }
                            String name = args[3];
                            var ot = teamManager.findByName(name);
                            if (ot.isEmpty()) { p.sendMessage(Text.colorize(prefix + msg().get("admin.team-not-found"))); return true; }
                            // Notify members before deletion
                            java.util.List<java.util.UUID> members = new java.util.ArrayList<>(ot.get().getMembers());
                            teamManager.removeTeam(ot.get());
                            p.sendMessage(Text.colorize(prefix + msg().get("admin.team-deleted")));
                            for (java.util.UUID m : members) {
                                org.bukkit.OfflinePlayer memOp = Bukkit.getOfflinePlayer(m);
                                if (memOp.isOnline() && memOp.getPlayer() != null) {
                                    memOp.getPlayer().sendMessage(Text.colorize(prefix + msg().get("admin.team-deleted-notify")));
                                    memOp.getPlayer().playSound(memOp.getPlayer().getLocation(), org.bukkit.Sound.BLOCK_ANVIL_LAND, 0.6f, 0.9f);
                                }
                            }
                            p.playSound(p.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 0.9f, 1.1f);
                            return true;
                        }
                        case "rename" -> {
                            if (args.length < 5) { p.sendMessage(Text.colorize(prefix + msg().get("admin.usage.team-rename", "label", label))); return true; }
                            var ot = teamManager.findByName(args[3]);
                            if (ot.isEmpty()) { p.sendMessage(Text.colorize(prefix + msg().get("admin.team-not-found"))); return true; }
                            String newName = args[4];
                            if (teamManager.findByName(newName).isPresent()) { p.sendMessage(Text.colorize(prefix + msg().get("admin.team-name-exists"))); return true; }
                            ot.get().setName(newName);
                            teamManager.save();
                            p.sendMessage(Text.colorize(prefix + msg().get("admin.team-renamed", "name", newName)));
                            for (java.util.UUID m : ot.get().getMembers()) {
                                org.bukkit.OfflinePlayer memOp = Bukkit.getOfflinePlayer(m);
                                if (memOp.isOnline() && memOp.getPlayer() != null) {
                                    memOp.getPlayer().sendMessage(Text.colorize(prefix + msg().get("admin.team-renamed-notify", "name", newName)));
                                    memOp.getPlayer().playSound(memOp.getPlayer().getLocation(), org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.2f);
                                }
                            }
                            p.playSound(p.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 0.9f, 1.1f);
                            return true;
                        }
                        case "color" -> {
                            if (args.length < 5) { p.sendMessage(Text.colorize(prefix + msg().get("admin.usage.team-color", "label", label))); return true; }
                            var ot = teamManager.findByName(args[3]);
                            if (ot.isEmpty()) { p.sendMessage(Text.colorize(prefix + msg().get("admin.team-not-found"))); return true; }
                            org.bukkit.DyeColor color;
                            try { color = org.bukkit.DyeColor.valueOf(args[4].toUpperCase()); } catch (Exception ex) { p.sendMessage(Text.colorize(prefix + msg().get("admin.invalid-color"))); return true; }
                            ot.get().setColor(color);
                            teamManager.save();
                            p.sendMessage(Text.colorize(prefix + msg().get("admin.team-color-set", "color", color.name())));
                            for (java.util.UUID m : ot.get().getMembers()) {
                                org.bukkit.OfflinePlayer memOp = Bukkit.getOfflinePlayer(m);
                                if (memOp.isOnline() && memOp.getPlayer() != null) {
                                    memOp.getPlayer().sendMessage(Text.colorize(prefix + msg().get("admin.team-color-changed-notify", "color", color.name())));
                                    memOp.getPlayer().playSound(memOp.getPlayer().getLocation(), org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.2f);
                                }
                            }
                            p.playSound(p.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 0.9f, 1.1f);
                            return true;
                        }
                        case "add" -> {
                            if (args.length < 5) { p.sendMessage(Text.colorize(prefix + msg().get("admin.usage.team-add", "label", label))); return true; }
                            var ot = teamManager.findByName(args[3]);
                            if (ot.isEmpty()) { p.sendMessage(Text.colorize(prefix + msg().get("admin.team-not-found"))); return true; }
                            org.bukkit.OfflinePlayer off = Bukkit.getOfflinePlayer(args[4]);
                            if (off == null || off.getUniqueId() == null) { p.sendMessage(Text.colorize(prefix + msg().get("admin.player-not-found"))); return true; }
                            // remove from previous team if any
                            teamManager.getTeamByMember(off.getUniqueId()).ifPresent(prev -> { prev.removeMember(off.getUniqueId()); });
                            boolean ok = teamManager.addMember(ot.get(), off.getUniqueId());
                            if (!ok) { p.sendMessage(Text.colorize(prefix + msg().get("admin.team-full", "max", teamManager.getMaxMembers()))); return true; }
                            teamManager.save();
                            p.sendMessage(Text.colorize(prefix + msg().get("admin.member-added", "player", off.getName())));
                            if (off.isOnline() && off.getPlayer() != null) {
                                off.getPlayer().sendMessage(Text.colorize(prefix + msg().get("admin.member-added-notify", "team", ot.get().getName())));
                                off.getPlayer().playSound(off.getPlayer().getLocation(), org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.2f);
                            }
                            p.playSound(p.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 0.9f, 1.1f);
                            return true;
                        }
                        case "remove" -> {
                            if (args.length < 5) { p.sendMessage(Text.colorize(prefix + msg().get("admin.usage.team-remove", "label", label))); return true; }
                            var ot = teamManager.findByName(args[3]);
                            if (ot.isEmpty()) { p.sendMessage(Text.colorize(prefix + msg().get("admin.team-not-found"))); return true; }
                            org.bukkit.OfflinePlayer off = Bukkit.getOfflinePlayer(args[4]);
                            if (off == null || off.getUniqueId() == null) { p.sendMessage(Text.colorize(prefix + msg().get("admin.player-not-found"))); return true; }
                            boolean ok = teamManager.removeMember(ot.get(), off.getUniqueId());
                            if (!ok) { p.sendMessage(Text.colorize(prefix + msg().get("admin.player-not-member"))); return true; }
                            p.sendMessage(Text.colorize(prefix + msg().get("admin.player-removed")));
                            teamManager.save();
                            if (off.isOnline() && off.getPlayer() != null) {
                                off.getPlayer().sendMessage(Text.colorize(prefix + msg().get("admin.player-removed-notify", "team", ot.get().getName())));
                                off.getPlayer().playSound(off.getPlayer().getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.6f);
                            }
                            p.playSound(p.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 0.9f, 1.1f);
                            return true;
                        }
                        default -> { p.sendMessage(Text.colorize(prefix + msg().get("admin.unknown-team-op"))); return true; }
                    }
                }
                // admin player ...
                if (args[1].equalsIgnoreCase("player")) {
                    if (args.length < 3) { p.sendMessage(Text.colorize(prefix + msg().get("admin.usage.player-main", "label", label))); return true; }
                    String op = args[2].toLowerCase();
                    switch (op) {
                        case "setteam" -> {
                            if (args.length < 5) { p.sendMessage(Text.colorize(prefix + msg().get("admin.usage.player-setteam", "label", label))); return true; }
                            org.bukkit.OfflinePlayer off = resolveOffline(args[3]);
                            if (off == null || off.getUniqueId() == null) { p.sendMessage(Text.colorize(prefix + msg().get("admin.player-not-found"))); return true; }
                            String teamName = args[4];
                            teamManager.getTeamByMember(off.getUniqueId()).ifPresent(prev -> prev.removeMember(off.getUniqueId()));
                            if (!teamName.equalsIgnoreCase("none")) {
                                var ot = teamManager.findByName(teamName);
                                if (ot.isEmpty()) { p.sendMessage(Text.colorize(prefix + msg().get("admin.team-not-found"))); return true; }
                                if (!teamManager.addMember(ot.get(), off.getUniqueId())) { p.sendMessage(Text.colorize(prefix + msg().get("admin.team-full", "max", teamManager.getMaxMembers()))); return true; }
                            }
                            teamManager.save();
                            if (teamName.equalsIgnoreCase("none")) {
                                p.sendMessage(Text.colorize(prefix + msg().get("admin.player-unset-team")));
                            } else {
                                p.sendMessage(Text.colorize(prefix + msg().get("admin.player-assigned", "team", teamName)));
                            }
                            if (off.isOnline() && off.getPlayer() != null) {
                                if (teamName.equalsIgnoreCase("none")) {
                                    off.getPlayer().sendMessage(Text.colorize(prefix + msg().get("admin.player-unset-team-notify")));
                                    off.getPlayer().playSound(off.getPlayer().getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.6f);
                                } else {
                                    off.getPlayer().sendMessage(Text.colorize(prefix + msg().get("admin.player-assigned-notify", "team", teamName)));
                                    off.getPlayer().playSound(off.getPlayer().getLocation(), org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.2f);
                                }
                            }
                            p.playSound(p.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 0.9f, 1.1f);
                            return true;
                        }
                        case "setnumber" -> {
                            if (args.length < 5) { p.sendMessage(Text.colorize(prefix + msg().get("admin.usage.player-setnumber", "label", label))); return true; }
                            org.bukkit.OfflinePlayer off = resolveOffline(args[3]);
                            if (off == null || off.getUniqueId() == null) { p.sendMessage(Text.colorize(prefix + msg().get("admin.player-not-found"))); return true; }
                            int num;
                            try { num = Integer.parseInt(args[4]); } catch (Exception ex) { p.sendMessage(Text.colorize(prefix + msg().get("admin.invalid-number"))); return true; }
                            if (num < 1 || num > 99) { p.sendMessage(Text.colorize(prefix + msg().get("admin.number-out-of-range"))); return true; }
                            var ot = teamManager.getTeamByMember(off.getUniqueId());
                            if (ot.isEmpty()) { p.sendMessage(Text.colorize(prefix + msg().get("admin.player-no-team"))); return true; }
                            // Optional: global uniqueness check could go here
                            ot.get().setRacerNumber(off.getUniqueId(), num);
                            teamManager.save();
                            p.sendMessage(Text.colorize(prefix + msg().get("admin.racer-number-set", "player", off.getName(), "num", num)));
                            if (off.isOnline() && off.getPlayer() != null) {
                                off.getPlayer().sendMessage(Text.colorize(prefix + msg().get("admin.racer-number-changed-notify", "num", num)));
                                off.getPlayer().playSound(off.getPlayer().getLocation(), org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.2f);
                            }
                            p.playSound(p.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 0.9f, 1.1f);
                            return true;
                        }
                        case "setboat" -> {
                            if (args.length < 5) { p.sendMessage(Text.colorize(prefix + msg().get("admin.usage.player-setboat", "label", label))); return true; }
                            org.bukkit.OfflinePlayer off = resolveOffline(args[3]);
                            if (off == null || off.getUniqueId() == null) { p.sendMessage(Text.colorize(prefix + msg().get("admin.player-not-found"))); return true; }
                            String type = args[4].toUpperCase();
                            // Validate against allowed boats: boats, chest boats, and raft names
                            java.util.Set<String> allowed = new java.util.LinkedHashSet<>();
                            for (org.bukkit.Material m : org.bukkit.Material.values()) {
                                String n = m.name();
                                if (n.endsWith("_BOAT") || n.endsWith("_CHEST_BOAT")) allowed.add(n);
                            }
                            // Also accept RAFT/CHEST_RAFT tokens even if not present as Materials
                            allowed.add("RAFT");
                            allowed.add("CHEST_RAFT");
                            if (!allowed.contains(type)) { p.sendMessage(Text.colorize(prefix + msg().get("admin.invalid-boat"))); return true; }
                            var ot = teamManager.getTeamByMember(off.getUniqueId());
                            if (ot.isEmpty()) { p.sendMessage(Text.colorize(prefix + msg().get("admin.player-no-team"))); return true; }
                            ot.get().setBoatType(off.getUniqueId(), type);
                            teamManager.save();
                            p.sendMessage(Text.colorize(prefix + msg().get("admin.boat-changed", "boat", type)));
                            if (off.isOnline() && off.getPlayer() != null) {
                                off.getPlayer().sendMessage(Text.colorize(prefix + msg().get("admin.boat-changed-notify", "boat", type)));
                                off.getPlayer().playSound(off.getPlayer().getLocation(), org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.2f);
                            }
                            p.playSound(p.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 0.9f, 1.1f);
                            return true;
                        }
                        default -> { p.sendMessage(Text.colorize(prefix + msg().get("admin.unknown-player-op"))); return true; }
                    }
                }
                p.sendMessage(Text.colorize(prefix + msg().get("admin.usage.admin-help", "label", label)));
                return true;
            }
            if (!args[0].equalsIgnoreCase("teams")) {
                p.sendMessage(Text.colorize(prefix + msg().get("race.usage.main", "label", label)));
                return true;
            }
            // /boatracing teams
            if (args.length == 1) {
                if (!p.hasPermission("boatracing.teams")) {
                    p.sendMessage(Text.colorize(prefix + msg().get("general.no-permission")));
                    p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.6f);
                    return true;
                }
                teamGUI.openMain(p);
                return true;
            }
            // /boatracing teams create <name>
            if (!p.hasPermission("boatracing.teams")) {
                p.sendMessage(Text.colorize(prefix + msg().get("general.no-permission")));
                p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.6f);
                return true;
            }
            if (args.length >= 2 && args[1].equalsIgnoreCase("create")) {
                boolean allowCreate = getConfig().getBoolean("player-actions.allow-team-create", true);
                if (!allowCreate) { p.sendMessage(Text.colorize(prefix + msg().get("team.create-restricted"))); return true; }
                if (teamManager.getTeamByMember(p.getUniqueId()).isPresent()) {
                    p.sendMessage(Text.colorize(prefix + msg().get("team.already-in-team")));
                    return true;
                }
                if (args.length < 3) {
                    p.sendMessage(Text.colorize(prefix + msg().get("team.create-usage")));
                    return true;
                }
                String name = String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length));
                String err = es.jaie55.boatracing.ui.TeamGUI.validateNameMessage(name);
                if (err != null) {
                    p.sendMessage(Text.colorize(prefix + msg().get(err)));
                    return true;
                }
                boolean exists = teamManager.getTeams().stream().anyMatch(t -> t.getName().equalsIgnoreCase(name));
                if (exists) {
                    p.sendMessage(Text.colorize(prefix + msg().get("team.name-exists")));
                    return true;
                }
                teamManager.createTeam(p, name, org.bukkit.DyeColor.WHITE);
                return true;
            }
            // /boatracing teams rename <new name>
            if (args.length >= 2 && args[1].equalsIgnoreCase("rename")) {
                java.util.Optional<es.jaie55.boatracing.team.Team> ot = teamManager.getTeamByMember(p.getUniqueId());
                if (ot.isEmpty()) { p.sendMessage(Text.colorize(prefix + msg().get("team.not-in-team"))); return true; }
                es.jaie55.boatracing.team.Team t = ot.get();
                boolean allowRename = getConfig().getBoolean("player-actions.allow-team-rename", false);
                if (!allowRename && !p.hasPermission("boatracing.admin")) { p.sendMessage(Text.colorize(prefix + msg().get("team.rename-restricted"))); return true; }
                if (args.length < 3) { p.sendMessage(Text.colorize(prefix + msg().get("team.rename-usage"))); return true; }
                String name = String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length));
                String err = es.jaie55.boatracing.ui.TeamGUI.validateNameMessage(name);
                if (err != null) { p.sendMessage(Text.colorize(prefix + msg().get(err))); return true; }
                boolean exists = teamManager.getTeams().stream().anyMatch(tt -> tt != t && tt.getName().equalsIgnoreCase(name));
                if (exists) { p.sendMessage(Text.colorize(prefix + msg().get("team.name-exists"))); return true; }
                t.setName(name); teamManager.save();
                p.sendMessage(Text.colorize(prefix + msg().get("team.renamed", "name", name)));
                return true;
            }
            // /boatracing teams color <dyeColor>
            if (args.length >= 2 && args[1].equalsIgnoreCase("color")) {
                java.util.Optional<es.jaie55.boatracing.team.Team> ot = teamManager.getTeamByMember(p.getUniqueId());
                if (ot.isEmpty()) { p.sendMessage(Text.colorize(prefix + msg().get("team.not-in-team"))); return true; }
                es.jaie55.boatracing.team.Team t = ot.get();
                boolean allowColor = getConfig().getBoolean("player-actions.allow-team-color", false);
                if (!allowColor && !p.hasPermission("boatracing.admin")) { p.sendMessage(Text.colorize(prefix + msg().get("team.color-restricted"))); return true; }
                if (args.length < 3) { p.sendMessage(Text.colorize(prefix + msg().get("team.color-usage"))); return true; }
                try {
                    org.bukkit.DyeColor dc = org.bukkit.DyeColor.valueOf(args[2].toUpperCase());
                    t.setColor(dc); teamManager.save();
                    p.sendMessage(Text.colorize(prefix + msg().get("team.color-set", "color", dc.name())));
                } catch (IllegalArgumentException ex) {
                    p.sendMessage(Text.colorize(prefix + msg().get("team.invalid-color")));
                }
                return true;
            }
            // /boatracing teams join <team name>
            if (args.length >= 2 && args[1].equalsIgnoreCase("join")) {
                if (teamManager.getTeamByMember(p.getUniqueId()).isPresent()) {
                    p.sendMessage(Text.colorize(prefix + msg().get("team.already-in-team")));
                    return true;
                }
                if (args.length < 3) { p.sendMessage(Text.colorize(prefix + msg().get("team.join-usage"))); return true; }
                String name = String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length));
                es.jaie55.boatracing.team.Team target = teamManager.getTeams().stream()
                    .filter(t -> t.getName().equalsIgnoreCase(name)).findFirst().orElse(null);
                if (target == null) { p.sendMessage(Text.colorize(prefix + msg().get("team.not-found"))); return true; }
                if (target.getMembers().size() >= teamManager.getMaxMembers()) { p.sendMessage(Text.colorize(prefix + msg().get("team.full"))); return true; }
                target.addMember(p.getUniqueId()); teamManager.save();
                p.sendMessage(Text.colorize(prefix + msg().get("team.joined", "team", target.getName())));
                for (java.util.UUID m : target.getMembers()) {
                    if (m.equals(p.getUniqueId())) continue;
                    org.bukkit.OfflinePlayer op = Bukkit.getOfflinePlayer(m);
                    if (op.isOnline() && op.getPlayer() != null) {
                        op.getPlayer().sendMessage(Text.colorize(prefix + msg().get("team.member-joined", "player", p.getName())));
                    }
                }
                return true;
            }
            // /boatracing teams leave (with confirm if team would be empty)
            if (args.length >= 2 && args[1].equalsIgnoreCase("leave")) {
                java.util.Optional<es.jaie55.boatracing.team.Team> ot = teamManager.getTeamByMember(p.getUniqueId());
                if (ot.isEmpty()) { p.sendMessage(Text.colorize(prefix + msg().get("team.not-in-team"))); return true; }
                es.jaie55.boatracing.team.Team t = ot.get();
                if (t.getMembers().size() <= 1) {
                    pendingDisband.add(p.getUniqueId());
                    p.sendMessage(Text.colorize(prefix + msg().get("team.last-member-warning")));
                    p.sendMessage(Text.colorize(prefix + msg().get("team.confirm-prompt", "label", label)));
                    return true;
                }
                t.removeMember(p.getUniqueId());
                teamManager.save();
                p.sendMessage(Text.colorize(prefix + msg().get("team.left")));
                for (java.util.UUID m : t.getMembers()) {
                    org.bukkit.OfflinePlayer op = Bukkit.getOfflinePlayer(m);
                    if (op.isOnline() && op.getPlayer() != null) {
                        op.getPlayer().sendMessage(Text.colorize(prefix + msg().get("team.member-left", "player", p.getName())));
                    }
                }
                return true;
            }
            // /boatracing teams kick <playerName> (with confirm)
            if (args.length >= 2 && args[1].equalsIgnoreCase("kick")) {
                java.util.Optional<es.jaie55.boatracing.team.Team> ot = teamManager.getTeamByMember(p.getUniqueId());
                if (ot.isEmpty()) { p.sendMessage(Text.colorize(prefix + msg().get("team.not-in-team"))); return true; }
                p.sendMessage(Text.colorize(prefix + msg().get("team.kick-admin-only", "label", label)));
                return true;
            }
            // /boatracing teams transfer <playerName>
            if (args.length >= 2 && args[1].equalsIgnoreCase("transfer")) {
                java.util.Optional<es.jaie55.boatracing.team.Team> ot = teamManager.getTeamByMember(p.getUniqueId());
                if (ot.isEmpty()) { p.sendMessage(Text.colorize(prefix + msg().get("team.not-in-team"))); return true; }
                p.sendMessage(Text.colorize(prefix + msg().get("team.transfer-removed")));
                return true;
            }
            // /boatracing teams boat <type>
            if (args.length >= 2 && args[1].equalsIgnoreCase("boat")) {
                java.util.Optional<es.jaie55.boatracing.team.Team> ot = teamManager.getTeamByMember(p.getUniqueId());
                if (ot.isEmpty()) { p.sendMessage(Text.colorize(prefix + msg().get("team.not-in-team"))); return true; }
                if (args.length < 3) { p.sendMessage(Text.colorize(prefix + msg().get("team.boat-usage"))); return true; }
                boolean allowBoat = getConfig().getBoolean("player-actions.allow-set-boat", true);
                if (!allowBoat) { p.sendMessage(Text.colorize(prefix + msg().get("team.boat-restricted"))); return true; }
                String type = args[2].toUpperCase();
                // Accept RAFT tokens directly, otherwise require a valid BOAT material
                if (type.equals("RAFT") || type.equals("CHEST_RAFT")) {
                    ot.get().setBoatType(p.getUniqueId(), type);
                    teamManager.save();
                    p.sendMessage(Text.colorize(prefix + msg().get("team.boat-set", "boat", type.toLowerCase())));
                } else {
                    try {
                        org.bukkit.Material m = org.bukkit.Material.valueOf(type);
                        if (!m.name().endsWith("BOAT")) throw new IllegalArgumentException();
                        ot.get().setBoatType(p.getUniqueId(), m.name());
                        teamManager.save();
                        p.sendMessage(Text.colorize(prefix + msg().get("team.boat-set", "boat", type.toLowerCase())));
                    } catch (IllegalArgumentException ex) {
                        p.sendMessage(Text.colorize(prefix + msg().get("team.invalid-boat")));
                    }
                }
                return true;
            }
            // /boatracing teams number <1-99>
            if (args.length >= 2 && args[1].equalsIgnoreCase("number")) {
                java.util.Optional<es.jaie55.boatracing.team.Team> ot = teamManager.getTeamByMember(p.getUniqueId());
                if (ot.isEmpty()) { p.sendMessage(Text.colorize(prefix + msg().get("team.not-in-team"))); return true; }
                if (args.length < 3) { p.sendMessage(Text.colorize(prefix + msg().get("team.number-usage"))); return true; }
                boolean allowNumber = getConfig().getBoolean("player-actions.allow-set-number", true);
                if (!allowNumber) { p.sendMessage(Text.colorize(prefix + msg().get("team.number-restricted"))); return true; }
                String s = args[2];
                if (!s.matches("\\d+")) { p.sendMessage(Text.colorize(prefix + msg().get("admin.invalid-number"))); return true; }
                int n = Integer.parseInt(s);
                if (n < 1 || n > 99) { p.sendMessage(Text.colorize(prefix + msg().get("admin.number-out-of-range"))); return true; }
                ot.get().setRacerNumber(p.getUniqueId(), n); teamManager.save();
                p.sendMessage(Text.colorize(prefix + msg().get("team.number-set", "number", String.valueOf(n))));
                return true;
            }
            // /boatracing teams disband y /boatracing teams confirm
            if (args.length >= 2 && args[1].equalsIgnoreCase("disband")) {
                java.util.Optional<es.jaie55.boatracing.team.Team> ot = teamManager.getTeamByMember(p.getUniqueId());
                if (ot.isEmpty()) { p.sendMessage(Text.colorize(prefix + msg().get("team.not-in-team"))); return true; }
                p.sendMessage(Text.colorize(prefix + msg().get("team.disband-admin-only", "label", label)));
                return true;
            }
            if (args.length >= 2 && args[1].equalsIgnoreCase("confirm")) {
                // Confirm pending dangerous actions (last-member leave -> disband)
                if (pendingDisband.remove(p.getUniqueId())) {
                    java.util.Optional<es.jaie55.boatracing.team.Team> ot = teamManager.getTeamByMember(p.getUniqueId());
                    if (ot.isEmpty()) { p.sendMessage(Text.colorize(prefix + msg().get("team.not-in-team"))); return true; }
                    es.jaie55.boatracing.team.Team t = ot.get();
                    // Proceed: remove member (self) and delete team since no members left
                    t.removeMember(p.getUniqueId());
                    teamManager.deleteTeam(t);
                    p.sendMessage(Text.colorize(prefix + msg().get("team.left-and-deleted")));
                    return true;
                }
                p.sendMessage(Text.colorize(prefix + msg().get("team.nothing-to-confirm")));
                return true;
            }
            if (args.length >= 2 && args[1].equalsIgnoreCase("cancel")) {
                boolean any = false;
                if (pendingDisband.remove(p.getUniqueId())) { any = true; }
                if (pendingTransfer.remove(p.getUniqueId()) != null) { any = true; }
                if (pendingKick.remove(p.getUniqueId()) != null) { any = true; }
                if (!any) {
                    p.sendMessage(Text.colorize(prefix + msg().get("team.nothing-to-cancel")));
                    return true;
                }
                        p.sendMessage(Text.colorize(prefix + msg().get("team.cancelled")));
                        p.playSound(p.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 0.8f, 1.1f);
                return true;
            }
            // default fallback
            p.sendMessage(Text.colorize(prefix + msg().get("team.unknown-subcommand")));
            return true;
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (command.getName().equalsIgnoreCase("boatracing")) {
            // Root suggestions (handle no-arg and first arg prefix)
            if (args.length == 0 || (args.length == 1 && (args[0] == null || args[0].isEmpty()))) {
                java.util.List<String> root = new java.util.ArrayList<>();
                if (sender.hasPermission("boatracing.teams")) root.add("teams");
                // Expose 'race' root to all users for join/leave/status discoverability
                root.add("race");
                if (sender.hasPermission("boatracing.setup")) root.add("setup");
                if (sender.hasPermission("boatracing.admin")) root.add("admin");
                if (sender.hasPermission("boatracing.reload")) root.add("reload");
                if (sender.hasPermission("boatracing.version")) root.add("version");
                return root;
            }
            if (args.length == 1) {
                String pref = args[0].toLowerCase();
                java.util.List<String> root = new java.util.ArrayList<>();
                if (sender.hasPermission("boatracing.teams")) root.add("teams");
                root.add("race");
                if (sender.hasPermission("boatracing.setup")) root.add("setup");
                if (sender.hasPermission("boatracing.admin")) root.add("admin");
                if (sender.hasPermission("boatracing.reload")) root.add("reload");
                if (sender.hasPermission("boatracing.version")) root.add("version");
                return root.stream().filter(s -> s.startsWith(pref)).toList();
            }
            if (args.length >= 2 && args[0].equalsIgnoreCase("admin")) {
                if (!sender.hasPermission("boatracing.admin")) return java.util.Collections.emptyList();
                if (args.length == 2) return java.util.Arrays.asList("help","team","player");
                if (args.length == 3 && args[1].equalsIgnoreCase("team")) return java.util.Arrays.asList("create","delete","rename","color","add","remove");
                if (args.length == 3 && args[1].equalsIgnoreCase("player")) return java.util.Arrays.asList("setteam","setnumber","setboat");
                if (args.length == 5 && args[2].equalsIgnoreCase("setboat")) {
                    return java.util.Arrays.asList(
                        "oak_boat","spruce_boat","birch_boat","jungle_boat","acacia_boat","dark_oak_boat","mangrove_boat","cherry_boat","pale_oak_boat",
                        "oak_chest_boat","spruce_chest_boat","birch_chest_boat","jungle_chest_boat","acacia_chest_boat","dark_oak_chest_boat","mangrove_chest_boat","cherry_chest_boat","pale_oak_chest_boat",
                        "bamboo_raft","bamboo_chest_raft"
                    );
                }
                return java.util.Collections.emptyList();
            }
            if (args.length >= 2 && args[0].equalsIgnoreCase("race")) {
                // Admin subcommands guarded; expose join/leave/status to everyone
                if (args.length == 2) {
                    java.util.List<String> subs = new java.util.ArrayList<>();
                    subs.add("help");
                    subs.add("join"); subs.add("leave"); subs.add("status");
                    if (sender.hasPermission("boatracing.race.admin") || sender.hasPermission("boatracing.setup")) {
                        subs.add("open"); subs.add("start"); subs.add("force"); subs.add("stop");
                    }
                    String pref = args[1] == null ? "" : args[1].toLowerCase();
                    return subs.stream().filter(s -> s.startsWith(pref)).toList();
                }
                // For subcommands that take <track>, suggest track names from library
                if (args.length == 3 && java.util.Arrays.asList("open","join","leave","force","start","stop","status").contains(args[1].toLowerCase())) {
                    String prefix = args[2] == null ? "" : args[2].toLowerCase();
                    java.util.List<String> names = new java.util.ArrayList<>();
                    if (trackLibrary != null) {
                        for (String n : trackLibrary.list()) if (n.toLowerCase().startsWith(prefix)) names.add(n);
                    }
                    return names;
                }
                return java.util.Collections.emptyList();
            }
            if (args.length >= 2 && args[0].equalsIgnoreCase("setup")) {
                if (!sender.hasPermission("boatracing.setup")) return Collections.emptyList();
                if (args.length == 2) return Arrays.asList("help","addstart","clearstarts","setfinish","setpit","addcheckpoint","clearcheckpoints","addlight","clearlights","setpos","clearpos","show","selinfo","wand","wizard");
                if (args.length >= 3 && args[1].equalsIgnoreCase("setpit")) {
                    // Build current partial input (join tokens from index 2)
                    String partial = String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length)).toLowerCase();
                    boolean startedQuote = args[2] != null && (args[2].startsWith("\"") || args[2].startsWith("'"));
                    java.util.List<String> names = new java.util.ArrayList<>();
                    for (es.jaie55.boatracing.team.Team t : teamManager.getTeams()) {
                        String name = t.getName();
                        if (name == null) continue;
                        String quoted = '"' + name + '"';
                        String cand = startedQuote ? quoted : name;
                        if (cand.toLowerCase().startsWith(partial)) names.add(cand);
                    }
                    return names;
                }
                if (args.length >= 3 && (args[1].equalsIgnoreCase("setpos") || args[1].equalsIgnoreCase("clearpos"))) {
                    // Suggest player names (online + known offline)
                    String prefName = args[2] == null ? "" : args[2].toLowerCase();
                    java.util.Set<String> names = new java.util.LinkedHashSet<>();
                    for (org.bukkit.entity.Player op : org.bukkit.Bukkit.getOnlinePlayers()) {
                        if (op.getName() != null && op.getName().toLowerCase().startsWith(prefName)) names.add(op.getName());
                    }
                    for (org.bukkit.OfflinePlayer op : org.bukkit.Bukkit.getOfflinePlayers()) {
                        if (op.getName() != null && op.getName().toLowerCase().startsWith(prefName)) names.add(op.getName());
                    }
                    if (args.length == 3) return new java.util.ArrayList<>(names);
                    if (args.length == 4 && args[1].equalsIgnoreCase("setpos")) {
                        // Suggest slot numbers and keyword 'auto'
                        java.util.List<String> opts = new java.util.ArrayList<>();
                        opts.add("auto");
                        int max = trackConfig.getStarts().size();
                        for (int i = 1; i <= Math.min(max, 20); i++) opts.add(String.valueOf(i));
                        String pref = args[3] == null ? "" : args[3].toLowerCase();
                        return opts.stream().filter(s -> s.startsWith(pref)).toList();
                    }
                    return java.util.Collections.emptyList();
                }
                // Do not expose wizard subcommands in tab-completion; single entrypoint UX
                return Collections.emptyList();
            }
            if (args.length >= 2 && args[0].equalsIgnoreCase("teams")) {
                if (!sender.hasPermission("boatracing.teams")) return Collections.emptyList();
                if (args.length == 2) return Arrays.asList("create", "rename", "color", "join", "leave", "boat", "number", "confirm", "cancel");
                // Autocomplete team names for 'join'
                if (args.length >= 3 && args[1].equalsIgnoreCase("join")) {
                    String prefix = args[2].toLowerCase();
                    java.util.List<String> names = new java.util.ArrayList<>();
                    for (es.jaie55.boatracing.team.Team t : teamManager.getTeams()) {
                        String name = t.getName();
                        if (name != null && name.toLowerCase().startsWith(prefix)) names.add(name);
                    }
                    return names;
                }
                if (args.length >= 3 && args[1].equalsIgnoreCase("create")) return Collections.emptyList();
                if (args.length >= 3 && args[1].equalsIgnoreCase("color")) return java.util.Arrays.stream(org.bukkit.DyeColor.values()).map(Enum::name).map(String::toLowerCase).toList();
                if (args.length >= 3 && args[1].equalsIgnoreCase("boat")) return Arrays.asList(
                    // Normal boats first
                    "oak_boat","spruce_boat","birch_boat","jungle_boat","acacia_boat","dark_oak_boat","mangrove_boat","cherry_boat","pale_oak_boat",
                    // Then chest-boat variants
                    "oak_chest_boat","spruce_chest_boat","birch_chest_boat","jungle_chest_boat","acacia_chest_boat","dark_oak_chest_boat","mangrove_chest_boat","cherry_chest_boat","pale_oak_chest_boat",
                    // Rafts (bamboo)
                    "bamboo_raft","bamboo_chest_raft"
                );
            }
            return Collections.emptyList();
        }
        return Collections.emptyList();
    }

    private void sendUpdateStatus(Player p) {
        if (updateChecker == null) return;
                        String current = getDescription().getVersion();
        if (!updateChecker.isChecked()) return;
        if (updateChecker.hasError()) {
            p.sendMessage(Text.colorize(prefix + msg().get("update.failed")));
            p.sendMessage(Text.colorize(prefix + msg().get("update.releases", "url", updateChecker.getLatestUrl())));
            return;
        }
        if (updateChecker.isOutdated()) {
            int behind = updateChecker.getBehindCount();
            String latest = updateChecker.getLatestVersion() != null ? updateChecker.getLatestVersion() : "latest";
            p.sendMessage(Text.colorize(prefix + msg().get("update.available", "name", getName(), "behind", String.valueOf(behind))));
            p.sendMessage(Text.colorize(prefix + msg().get("update.current-vs-latest", "current", current, "latest", latest)));
            p.sendMessage(Text.colorize(prefix + msg().get("update.link", "url", updateChecker.getLatestUrl())));
        } else {
            String latest = updateChecker.getLatestVersion() != null ? updateChecker.getLatestVersion() : current;
            p.sendMessage(Text.colorize(prefix + msg().get("update.up-to-date", "version", current)));
            p.sendMessage(Text.colorize(prefix + msg().get("update.info", "latest", latest, "url", updateChecker.getLatestUrl())));
        }
    }

    private static String fmtBox(org.bukkit.util.BoundingBox b) {
        return String.format("min(%d,%d,%d) max(%d,%d,%d)",
                (int) Math.floor(b.getMinX()), (int) Math.floor(b.getMinY()), (int) Math.floor(b.getMinZ()),
                (int) Math.floor(b.getMaxX()), (int) Math.floor(b.getMaxY()), (int) Math.floor(b.getMaxZ()));
    }
}
