package es.jaie55.boatracing.track;

import es.jaie55.boatracing.BoatRacingPlugin;
import es.jaie55.boatracing.util.Text;
import org.bukkit.Sound;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.block.Action;

public class WandListener implements Listener {
    private final BoatRacingPlugin plugin;
    public WandListener(BoatRacingPlugin plugin) { this.plugin = plugin; }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getItem() == null) return;
        if (!SelectionManager.isWand(e.getItem())) return;
        if (e.getAction() == Action.LEFT_CLICK_BLOCK && e.getClickedBlock() != null) {
            SelectionManager.setPos1(e.getPlayer(), e.getClickedBlock().getLocation());
            e.getPlayer().sendMessage(Text.colorize(plugin.pref() + plugin.msg().get("setup.selection-corner-a",
                "x", e.getClickedBlock().getX(),
                "y", e.getClickedBlock().getY(),
                "z", e.getClickedBlock().getZ())));
            e.getPlayer().playSound(e.getPlayer().getLocation(), Sound.UI_BUTTON_CLICK, 0.9f, 1.3f);
            e.setCancelled(true);
        } else if (e.getAction() == Action.RIGHT_CLICK_BLOCK && e.getClickedBlock() != null) {
            SelectionManager.setPos2(e.getPlayer(), e.getClickedBlock().getLocation());
            e.getPlayer().sendMessage(Text.colorize(plugin.pref() + plugin.msg().get("setup.selection-corner-b",
                "x", e.getClickedBlock().getX(),
                "y", e.getClickedBlock().getY(),
                "z", e.getClickedBlock().getZ())));
            e.getPlayer().playSound(e.getPlayer().getLocation(), Sound.UI_BUTTON_CLICK, 0.9f, 1.0f);
            e.setCancelled(true);
        }
    }
}
