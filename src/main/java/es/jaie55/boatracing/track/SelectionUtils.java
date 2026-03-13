package es.jaie55.boatracing.track;

import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;

import java.util.ArrayList;
import java.util.List;

/**
 * Utilities to read the current BoatRacing selection (independent of WorldEdit).
 */
public class SelectionUtils {
    public static class DetailedSelection {
        public final String worldName;
        public final BoundingBox box;

        public DetailedSelection(String worldName, BoundingBox box) {
            this.worldName = worldName;
            this.box = box;
        }
    }

    /** Return selection as world name + BoundingBox, or null if incomplete. */
    public static DetailedSelection getSelectionDetailed(Player p) {
        SelectionManager.Sel s = SelectionManager.get(p);
        if (s == null || s.world == null) return null;
        BoundingBox b = SelectionManager.toBox(s);
        if (b == null) return null;
        return new DetailedSelection(s.world, b);
    }

    /** Return a compact human-friendly dump of the current selection. */
    public static List<String> debugSelection(Player p) {
        List<String> out = new ArrayList<>();
        SelectionManager.Sel s = SelectionManager.get(p);
        if (s == null) {
            out.add("no selection");
            return out;
        }
        out.add("world=" + (s.world == null ? "null" : s.world));
        out.add("pos1=(" + s.x1 + "," + s.y1 + "," + s.z1 + ")");
        out.add("pos2=(" + s.x2 + "," + s.y2 + "," + s.z2 + ")");
        BoundingBox b = SelectionManager.toBox(s);
        if (b == null) {
            out.add("box=null (need pos1 and pos2)");
        } else {
            out.add("box=min(" + (int)Math.floor(b.getMinX()) + "," + (int)Math.floor(b.getMinY()) + "," + (int)Math.floor(b.getMinZ()) + ") " +
                    "max(" + (int)Math.floor(b.getMaxX()) + "," + (int)Math.floor(b.getMaxY()) + "," + (int)Math.floor(b.getMaxZ()) + ")");
        }
        return out;
    }
}
