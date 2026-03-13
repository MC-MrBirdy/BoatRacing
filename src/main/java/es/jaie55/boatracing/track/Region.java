package es.jaie55.boatracing.track;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.BoundingBox;

public class Region {
    private final String worldName;
    private final BoundingBox box;

    public Region(String worldName, BoundingBox box) {
        this.worldName = worldName;
        this.box = box;
    }

    public String getWorldName() { return worldName; }
    public BoundingBox getBox() { return box; }
    public boolean contains(Location loc) {
        if (loc == null) return false;
        if (!loc.getWorld().getName().equals(worldName)) return false;
        return box.contains(loc.toVector());
    }
    public World world() { return Bukkit.getWorld(worldName); }
}
