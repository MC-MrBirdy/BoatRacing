package es.jaie55.boatracing.ui;

import es.jaie55.boatracing.BoatRacingPlugin;
import es.jaie55.boatracing.race.RaceManager;
import es.jaie55.boatracing.track.TrackConfig;
import es.jaie55.boatracing.util.SchedulerCompat;
import es.jaie55.boatracing.util.Text;
import net.kyori.adventure.text.Component;
import net.wesjd.anvilgui.AnvilGUI;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public class AdminRaceGUI implements Listener {
    private final Component TITLE;
    private final BoatRacingPlugin plugin;
    private final org.bukkit.NamespacedKey KEY_ACTION;
    private final org.bukkit.NamespacedKey KEY_PLAYER_ID;

    public AdminRaceGUI(BoatRacingPlugin plugin) {
        this.plugin = plugin;
        this.KEY_ACTION = new org.bukkit.NamespacedKey(plugin, "adm-race-action");
        this.KEY_PLAYER_ID = new org.bukkit.NamespacedKey(plugin, "adm-race-player");
        this.TITLE = Text.title(plugin.msg().get("gui.race.title"));
    }

    public void open(Player p) {
        Inventory inv = Bukkit.createInventory(null, 54, TITLE);
        fill(inv, pane(Material.GRAY_STAINED_GLASS_PANE));

        RaceManager rm = plugin.getRaceManager();
        TrackConfig tc = plugin.getTrackConfig();
        String cur = (plugin.getTrackLibrary() != null && plugin.getTrackLibrary().getCurrent() != null) ? plugin.getTrackLibrary().getCurrent() : plugin.msg().get("general.unsaved");
        boolean running = rm.isRunning();
        boolean registering = rm.isRegistering();
        int regs = rm.getRegistered().size();
        int laps = rm.getTotalLaps();
        int participants = running ? rm.getParticipants().size() : 0;
        boolean ready = tc.isReady();

        // Status card (center top)
        inv.setItem(4, card(
            Material.NETHER_STAR,
            Text.item(plugin.msg().get("gui.race.btn-status")),
            java.util.Arrays.asList(
                plugin.msg().get("gui.race.lore-track", "track", cur),
                plugin.msg().get(ready ? "gui.race.lore-ready-yes" : "gui.race.lore-ready-no"),
                plugin.msg().get("gui.race.lore-laps", "laps", laps),
                plugin.msg().get(registering ? "gui.race.lore-registration-open" : "gui.race.lore-registration-closed", "count", regs),
                plugin.msg().get(running ? "gui.race.lore-running-yes" : "gui.race.lore-running-no", "count", participants)
            )
        ));

        // Controls row
        inv.setItem(18, action(Material.LIME_DYE, plugin.msg().get("gui.race.btn-open-reg"), "open"));
        inv.setItem(19, action(Material.RED_DYE, plugin.msg().get("gui.race.btn-close-reg"), "close"));
        inv.setItem(20, action(Material.GOLD_BLOCK, plugin.msg().get("gui.race.btn-start"), "start"));
        inv.setItem(21, action(Material.REDSTONE_TORCH, plugin.msg().get("gui.race.btn-force"), "force"));
        inv.setItem(22, action(Material.BARRIER, plugin.msg().get("gui.race.btn-stop"), "stop"));
        String voteState = plugin.msg().get(plugin.isMapVoteOpen() ? "gui.race.lore-vote-open" : "gui.race.lore-vote-closed");
        inv.setItem(23, actionWithLore(Material.CARTOGRAPHY_TABLE, plugin.msg().get("gui.race.btn-vote-setup"), "vote:setup", java.util.Arrays.asList(voteState)));
        inv.setItem(24, action(Material.BARRIER, plugin.msg().get("gui.race.btn-vote-close"), "vote:close"));

        // Laps quick set
        inv.setItem(27, action(Material.PAPER, plugin.msg().get("gui.race.btn-set-laps", "n", "1"), "laps:1"));
        inv.setItem(28, action(Material.PAPER, plugin.msg().get("gui.race.btn-set-laps", "n", "3"), "laps:3"));
        inv.setItem(29, action(Material.PAPER, plugin.msg().get("gui.race.btn-set-laps", "n", "5"), "laps:5"));
        inv.setItem(30, action(Material.PAPER, plugin.msg().get("gui.race.btn-set-laps", "n", "10"), "laps:10"));
        inv.setItem(31, action(Material.NAME_TAG, plugin.msg().get("gui.race.btn-custom-laps"), "laps:custom"));

        // Track edit shortcuts (some are command tips)
        inv.setItem(36, action(Material.WHITE_CONCRETE, plugin.msg().get("gui.race.btn-add-start-tip"), "tip:addstart"));
        inv.setItem(37, action(Material.TNT, plugin.msg().get("gui.race.btn-clear-starts"), "clear:starts"));
        inv.setItem(38, action(Material.YELLOW_CONCRETE, plugin.msg().get("gui.race.btn-set-finish-tip"), "tip:setfinish"));
        inv.setItem(39, action(Material.BARRIER, plugin.msg().get("gui.race.btn-clear-finish"), "clear:finish"));
        inv.setItem(40, action(Material.LIGHT_BLUE_CONCRETE, plugin.msg().get("gui.race.btn-set-pit-tip"), "tip:setpit"));
        inv.setItem(41, action(Material.BARRIER, plugin.msg().get("gui.race.btn-clear-pit"), "clear:pit"));
        inv.setItem(42, action(Material.LIGHT_GRAY_CONCRETE, plugin.msg().get("gui.race.btn-add-checkpoint-tip"), "tip:addcheckpoint"));
        inv.setItem(43, action(Material.TNT, plugin.msg().get("gui.race.btn-clear-checkpoints"), "clear:checkpoints"));
        inv.setItem(44, action(Material.BOOK, plugin.msg().get("gui.race.btn-open-wizard-tip"), "tip:wizard"));

        // Participants heads (registered online players)
        int slot = 9;
        for (UUID id : rm.getRegistered()) {
            Player op = Bukkit.getPlayer(id);
            if (op == null) continue;
            if (slot >= 18) break; // reserve row 2 for controls
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            org.bukkit.inventory.meta.SkullMeta sm = (org.bukkit.inventory.meta.SkullMeta) head.getItemMeta();
            if (sm != null) {
                sm.setOwningPlayer(op);
                sm.displayName(Text.item("&f" + op.getName()));
                java.util.List<String> lore = new java.util.ArrayList<>();
                lore.add(plugin.msg().get("gui.race.lore-click-remove-reg"));
                sm.lore(Text.lore(lore));
                sm.getPersistentDataContainer().set(KEY_PLAYER_ID, org.bukkit.persistence.PersistentDataType.STRING, id.toString());
                sm.getPersistentDataContainer().set(KEY_ACTION, org.bukkit.persistence.PersistentDataType.STRING, "kickreg");
                head.setItemMeta(sm);
            }
            inv.setItem(slot++, head);
        }

    // Back button
    inv.setItem(45, button(Material.ARROW, Text.item(plugin.msg().get("gui.common.back"))));

        p.openInventory(inv);
        p.playSound(p.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 0.8f, 1.2f);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent e) {
        if (e.getView().getTopInventory() == null) return;
        if (!Text.plain(e.getView().title()).equals(Text.plain(TITLE))) return;
        e.setCancelled(true);
        if (e.getClickedInventory() == null || e.getClickedInventory() != e.getView().getTopInventory()) return;
        HumanEntity he = e.getWhoClicked(); if (!(he instanceof Player)) return; Player p = (Player) he;
        if (!p.hasPermission("boatracing.setup")) { p.closeInventory(); return; }
        ItemStack it = e.getCurrentItem(); if (it == null) return;
        ItemMeta im = it.getItemMeta(); if (im == null) return;
        String action = im.getPersistentDataContainer().get(KEY_ACTION, org.bukkit.persistence.PersistentDataType.STRING);
        if (action == null) {
            // Back button
            if (it.getType() == Material.ARROW) { plugin.getAdminGUI().openMain(p); return; }
            return;
        }
        // Some actions open an Anvil (e.g., custom laps). If we refresh immediately,
        // it will close the Anvil. Skip auto-refresh for those.
        boolean skipRefresh = "laps:custom".equals(action) || "vote:setup".equals(action);
        handle(p, action, im);
        if (!skipRefresh) {
            // Refresh UI after action
            SchedulerCompat.runNow(plugin, () -> open(p));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory() == null) return;
        if (Text.plain(e.getView().title()).equals(Text.plain(TITLE))) e.setCancelled(true);
    }

    private void handle(Player p, String action, ItemMeta im) {
        RaceManager rm = plugin.getRaceManager();
        TrackConfig tc = plugin.getTrackConfig();
        switch (action) {
            case "open" -> {
                int laps = rm.getTotalLaps();
                boolean ok = rm.openRegistration(laps, null);
                if (!ok) p.sendMessage(Text.colorize(plugin.pref() + plugin.msg().get("race.cannot-open-registration")));
            }
            case "close" -> rm.cancelRegistration(true);
            case "start" -> {
                if (rm.isRunning()) { p.sendMessage(Text.colorize(plugin.pref() + plugin.msg().get("race.already-running"))); break; }
                java.util.Set<java.util.UUID> regs = new java.util.LinkedHashSet<>(rm.getRegistered());
                java.util.List<Player> participants = new java.util.ArrayList<>();
                for (java.util.UUID id : regs) { Player op = Bukkit.getPlayer(id); if (op != null) participants.add(op); }
                if (participants.isEmpty()) { p.sendMessage(Text.colorize(plugin.pref() + plugin.msg().get("race.no-participants", "label", "boatracing"))); break; }
                java.util.List<Player> placed = rm.placeAtStartsWithBoats(participants);
                if (placed.isEmpty()) { p.sendMessage(Text.colorize(plugin.pref() + plugin.msg().get("race.no-start-slots"))); break; }
                if (placed.size() < participants.size()) { p.sendMessage(Text.colorize(plugin.pref() + plugin.msg().get("race.some-not-placed"))); }
                rm.startRaceWithCountdown(placed);
            }
            case "force" -> rm.forceStart();
            case "stop" -> {
                boolean any = false;
                if (rm.isRegistering()) any |= rm.cancelRegistration(true);
                if (rm.isRunning()) any |= rm.cancelRace();
                if (!any) p.sendMessage(Text.colorize(plugin.pref() + plugin.msg().get("setup.nothing-to-stop")));
            }
            case "vote:setup" -> {
                if (plugin.getVoteGUI() != null) plugin.getVoteGUI().openAdminSetup(p);
            }
            case "vote:close" -> plugin.closeMapVoteFromAdmin(p);
            default -> {
                if (action.startsWith("laps:")) {
                    String v = action.substring("laps:".length());
                    if ("custom".equals(v)) { openAnvilLaps(p); return; }
                    try {
                        int n = Math.max(1, Integer.parseInt(v));
                        rm.setTotalLaps(n);
                        p.sendMessage(Text.colorize(plugin.pref() + plugin.msg().get("admin.laps-set", "laps", n)));
                        p.playSound(p.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 0.9f, 1.2f);
                    } catch (NumberFormatException ignored) {}
                    return;
                }
                if (action.startsWith("tip:")) {
                    switch (action.substring(4)) {
                        case "addstart" -> p.sendMessage(Text.c(plugin.msg().get("setup.tip-label")).append(Text.suggest(plugin.msg().get("setup.tip-paste-addstart"),"/boatracing setup addstart")));
                        case "setfinish" -> p.sendMessage(Text.c(plugin.msg().get("setup.tip-label")).append(Text.suggest(plugin.msg().get("setup.tip-paste-setfinish"),"/boatracing setup setfinish")));
                        case "setpit" -> p.sendMessage(Text.c(plugin.msg().get("setup.tip-label")).append(Text.suggest(plugin.msg().get("setup.tip-paste-setpit"),"/boatracing setup setpit ")));
                        case "addcheckpoint" -> p.sendMessage(Text.c(plugin.msg().get("setup.tip-label")).append(Text.suggest(plugin.msg().get("setup.tip-paste-addcheckpoint"),"/boatracing setup addcheckpoint")));
                        case "wizard" -> p.sendMessage(Text.c(plugin.msg().get("setup.tip-label")).append(Text.suggest(plugin.msg().get("setup.tip-open-wizard"),"/boatracing setup wizard")));
                    }
                    return;
                }
                if (action.startsWith("clear:")) {
                    switch (action.substring(6)) {
                        case "starts" -> { tc.clearStarts(); p.sendMessage(Text.colorize(plugin.pref() + plugin.msg().get("setup.starts-cleared"))); }
                        case "finish" -> { tc.setFinish(null); p.sendMessage(Text.colorize(plugin.pref() + plugin.msg().get("setup.finish-cleared"))); }
                        case "pit" -> { tc.setPitlane(null); p.sendMessage(Text.colorize(plugin.pref() + plugin.msg().get("setup.pit-cleared"))); }
                        case "checkpoints" -> { tc.clearCheckpoints(); p.sendMessage(Text.colorize(plugin.pref() + plugin.msg().get("setup.checkpoints-cleared"))); }
                    }
                    p.playSound(p.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 0.9f, 1.1f);
                    return;
                }
                if ("kickreg".equals(action)) {
                    String uid = im.getPersistentDataContainer().get(KEY_PLAYER_ID, org.bukkit.persistence.PersistentDataType.STRING);
                    if (uid != null) {
                        Player op = Bukkit.getPlayer(java.util.UUID.fromString(uid));
                        if (op != null) rm.leave(op);
                    }
                }
            }
        }
    }

    private void openAnvilLaps(Player p) {
        ItemStack left = new ItemStack(Material.PAPER);
        ItemMeta lm = left.getItemMeta(); if (lm != null) { lm.displayName(Component.empty()); left.setItemMeta(lm); }
        new AnvilGUI.Builder()
            .title(plugin.msg().get("gui.race.anvil-set-laps"))
            .text(" ")
            .itemLeft(left)
            .interactableSlots()
            .onClick((slot, state) -> {
                if (slot != AnvilGUI.Slot.OUTPUT) return java.util.Collections.emptyList();
                String input = state.getText() == null ? "" : state.getText().trim();
                try {
                    int n = Math.max(1, Integer.parseInt(input));
                    plugin.getRaceManager().setTotalLaps(n);
                    state.getPlayer().sendMessage(Text.colorize(plugin.pref() + plugin.msg().get("admin.laps-set", "laps", n)));
                    return java.util.Arrays.asList(AnvilGUI.ResponseAction.close(), AnvilGUI.ResponseAction.run(() -> open(state.getPlayer())));
                } catch (NumberFormatException ex) {
                    return java.util.Arrays.asList(AnvilGUI.ResponseAction.replaceInputText(input));
                }
            })
            .plugin(plugin)
            .open(p);
    }

    // Helpers
    private static ItemStack button(Material mat, Component name) { ItemStack it = new ItemStack(mat); ItemMeta im = it.getItemMeta(); if (im != null) { im.displayName(name); im.addItemFlags(ItemFlag.values()); it.setItemMeta(im);} return it; }
    private static ItemStack pane(Material mat) { ItemStack it = new ItemStack(mat); ItemMeta im = it.getItemMeta(); if (im != null) { im.displayName(Component.text(" ")); im.addItemFlags(ItemFlag.values()); it.setItemMeta(im);} return it; }
    private static void fill(Inventory inv, ItemStack filler) { for (int i=0;i<inv.getSize();i++) if (inv.getItem(i)==null || inv.getItem(i).getType()==Material.AIR) inv.setItem(i, filler); }
    private ItemStack card(Material mat, Component name, java.util.List<String> loreLines) {
        ItemStack it = new ItemStack(mat);
        ItemMeta im = it.getItemMeta();
        if (im != null) {
            im.displayName(name);
            if (loreLines != null && !loreLines.isEmpty()) im.lore(Text.lore(loreLines));
            im.addItemFlags(ItemFlag.values());
            it.setItemMeta(im);
        }
        return it;
    }
    private ItemStack action(Material mat, String titleLegacy, String action) {
        ItemStack it = new ItemStack(mat);
        ItemMeta im = it.getItemMeta();
        if (im != null) {
            im.displayName(Text.item(titleLegacy));
            im.getPersistentDataContainer().set(KEY_ACTION, org.bukkit.persistence.PersistentDataType.STRING, action);
            im.addItemFlags(ItemFlag.values());
            it.setItemMeta(im);
        }
        return it;
    }

    private ItemStack actionWithLore(Material mat, String titleLegacy, String action, java.util.List<String> loreLines) {
        ItemStack it = new ItemStack(mat);
        ItemMeta im = it.getItemMeta();
        if (im != null) {
            im.displayName(Text.item(titleLegacy));
            if (loreLines != null && !loreLines.isEmpty()) im.lore(Text.lore(loreLines));
            im.getPersistentDataContainer().set(KEY_ACTION, org.bukkit.persistence.PersistentDataType.STRING, action);
            im.addItemFlags(ItemFlag.values());
            it.setItemMeta(im);
        }
        return it;
    }
}
