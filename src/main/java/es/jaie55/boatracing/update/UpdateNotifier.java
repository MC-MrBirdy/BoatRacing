package es.jaie55.boatracing.update;

import es.jaie55.boatracing.BoatRacingPlugin;
import es.jaie55.boatracing.util.SchedulerCompat;
import es.jaie55.boatracing.util.Text;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class UpdateNotifier implements Listener {
    private final BoatRacingPlugin plugin;
    private final UpdateChecker checker;
    private final String prefix;
    // Throttle network checks triggered by joins (ms)
    private volatile long lastJoinCheckMs = 0L;
    private static final long JOIN_CHECK_COOLDOWN_MS = 60_000L; // 60s

    public UpdateNotifier(BoatRacingPlugin plugin, UpdateChecker checker, String prefix) {
        this.plugin = plugin;
        this.checker = checker;
        this.prefix = prefix;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        if (!plugin.getConfig().getBoolean("updates.enabled", true)) return;
        if (!plugin.getConfig().getBoolean("updates.notify-admins", true)) return;
        Player p = e.getPlayer();
        if (!p.hasPermission("boatracing.update")) return;
        // Notify immediately if we already know we're outdated
        if (checker != null && checker.isChecked() && checker.isOutdated()) {
            int behind = checker.getBehindCount();
            String latest = checker.getLatestVersion() != null ? checker.getLatestVersion() : "latest";
            String current = plugin.getDescription().getVersion();
            p.sendMessage(Text.colorize(prefix + plugin.msg().get("update.outdated", "behind", behind)));
            p.sendMessage(Text.colorize(prefix + plugin.msg().get("update.version-info", "current", current, "latest", latest)));
            p.sendMessage(Text.colorize(prefix + plugin.msg().get("update.download-link", "url", checker.getLatestUrl())));
        } else if (checker != null) {
            // If result is stale or not yet checked, trigger a quick check (throttled)
            long now = System.currentTimeMillis();
            if (!checker.isChecked() || (now - lastJoinCheckMs) >= JOIN_CHECK_COOLDOWN_MS) {
                lastJoinCheckMs = now;
                try { checker.checkAsync(); } catch (Exception ignored) { plugin.getLogger().finer("Async update check failed on join: " + ignored.getMessage()); }
                SchedulerCompat.runLater(plugin, () -> {
                    if (checker.isChecked() && checker.isOutdated() && p.isOnline() && p.hasPermission("boatracing.update")) {
                        int behind = checker.getBehindCount();
                        String latest = checker.getLatestVersion() != null ? checker.getLatestVersion() : "latest";
                        String current = plugin.getDescription().getVersion();
                        p.sendMessage(Text.colorize(prefix + plugin.msg().get("update.outdated", "behind", behind)));
                        p.sendMessage(Text.colorize(prefix + plugin.msg().get("update.version-info", "current", current, "latest", latest)));
                        p.sendMessage(Text.colorize(prefix + plugin.msg().get("update.download-link", "url", checker.getLatestUrl())));
                    }
                }, 20L * 5);
            }
        }
    }
}
