package es.jaie55.boatracing.track;

import es.jaie55.boatracing.BoatRacingPlugin;
import org.bukkit.Bukkit;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;
import org.bukkit.entity.Player;
import es.jaie55.boatracing.util.Text;

/**
 * TrackLibrary manages multiple named tracks backed by separate YAML files.
 * Each track is stored under dataFolder/tracks/<name>.yml
 *
 * It wraps an existing TrackConfig instance by swapping its underlying file
 * contents when loading/saving different tracks. This avoids touching usage sites.
 */
public class TrackLibrary {
    private final BoatRacingPlugin plugin;
    private final File tracksDir;
    private final TrackConfig trackConfig;
    private String current;

    public TrackLibrary(BoatRacingPlugin plugin, File dataFolder, TrackConfig trackConfig) {
        this.plugin = plugin;
        this.tracksDir = new File(dataFolder, "tracks");
        if (!tracksDir.exists()) tracksDir.mkdirs();
        this.trackConfig = trackConfig;
        this.current = null;
    migrateLegacyIfPresent();
    }

    public List<String> list() {
        String[] names = tracksDir.list((dir, name) -> name.toLowerCase().endsWith(".yml"));
        if (names == null) return Collections.emptyList();
        List<String> out = new ArrayList<>();
        for (String n : names) out.add(n.substring(0, n.length()-4));
        Collections.sort(out, String.CASE_INSENSITIVE_ORDER);
        return out;
    }

    public String getCurrent() { return current; }

    public String normalizeName(String raw) { return sanitize(raw); }

    public boolean exists(String name) { return new File(tracksDir, sanitize(name) + ".yml").exists(); }

    public boolean create(String name) {
        String clean = sanitize(name);
        if (clean.isEmpty()) return false;
        File f = new File(tracksDir, clean + ".yml");
        if (f.exists()) return false;
        try {
            if (!f.createNewFile()) return false;
            // Start empty file; current TrackConfig can be reset and saved into it when selected
            return true;
        } catch (IOException e) {
            Bukkit.getLogger().warning("[BoatRacing] Failed to create track file: " + e.getMessage());
            return false;
        }
    }

    public boolean delete(String name) {
        File f = new File(tracksDir, sanitize(name) + ".yml");
        if (!f.exists()) return false;
        if (current != null && current.equalsIgnoreCase(name)) current = null;
        return f.delete();
    }

    public boolean rename(String oldName, String newName) {
        String oldClean = sanitize(oldName);
        String newClean = sanitize(newName);
        if (oldClean.isEmpty() || newClean.isEmpty()) return false;

        File oldFile = new File(tracksDir, oldClean + ".yml");
        if (!oldFile.exists()) return false;

        if (oldClean.equalsIgnoreCase(newClean)) {
            if (current != null && current.equalsIgnoreCase(oldClean)) {
                current = newClean;
                trackConfig.setBackingFile(oldFile);
            }
            return true;
        }

        File newFile = new File(tracksDir, newClean + ".yml");
        if (newFile.exists()) return false;

        try {
            Files.move(oldFile.toPath(), newFile.toPath());
            if (current != null && current.equalsIgnoreCase(oldClean)) {
                current = newClean;
                trackConfig.setBackingFile(newFile);
            }
            return true;
        } catch (IOException e) {
            Bukkit.getLogger().warning("[BoatRacing] Failed to rename track file: " + e.getMessage());
            return false;
        }
    }

    /**
     * Load the given named track into the bound TrackConfig from tracks/<name>.yml.
     */
    public boolean select(String name) {
        String clean = sanitize(name);
        File f = new File(tracksDir, clean + ".yml");
        if (!f.exists()) return false;
        trackConfig.setBackingFile(f);
        current = clean;
        return true;
    }

    /**
     * Save current TrackConfig snapshot into the named track file under tracks/.
     */
    public boolean saveAs(String name) {
        String clean = sanitize(name);
        File f = new File(tracksDir, clean + ".yml");
        trackConfig.setBackingFile(f);
        trackConfig.save();
        current = clean;
        return true;
    }

    private static String sanitize(String raw) {
        if (raw == null) return "";
        String s = raw.trim();
        s = s.replaceAll("[^a-zA-Z0-9-_ ]", "");
        s = s.replace(' ', '_');
        if (s.length() > 24) s = s.substring(0, 24);
        return s;
    }

    /**
     * Migrate legacy dataFolder/track.yml into tracks/default.yml (or default_N.yml) and select it.
     */
    private void migrateLegacyIfPresent() {
        File legacy = new File(tracksDir.getParentFile(), "track.yml");
        if (!legacy.exists()) return;
        // Pick a destination name that doesn't collide
        String base = "default";
        String name = base;
        int i = 1;
        while (new File(tracksDir, name + ".yml").exists()) {
            name = base + "_" + (i++);
        }
        File dest = new File(tracksDir, name + ".yml");
        try {
            Files.copy(legacy.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
            Bukkit.getLogger().info("[BoatRacing] Migrated legacy track.yml to tracks/" + dest.getName());
            // Try to delete legacy after successful copy
            boolean deleted = legacy.delete();
            if (!deleted) {
                Bukkit.getLogger().warning("[BoatRacing] Could not delete legacy track.yml after migration. Please remove it manually.");
            }
            this.current = name;
            this.trackConfig.setBackingFile(dest);
            // Notify online admins (setup permission)
            String msg = Text.colorize(plugin.pref() + plugin.msg().get(
                deleted ? "setup.legacy-migrated-removed" : "setup.legacy-migrated-kept",
                "file", dest.getName()));
            for (Player pl : Bukkit.getOnlinePlayers()) {
                if (pl.hasPermission("boatracing.setup")) pl.sendMessage(msg);
            }
        } catch (IOException e) {
            Bukkit.getLogger().warning("[BoatRacing] Failed to migrate legacy track.yml: " + e.getMessage());
        }
    }
}
