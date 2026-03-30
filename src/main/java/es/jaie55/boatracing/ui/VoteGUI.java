package es.jaie55.boatracing.ui;

import es.jaie55.boatracing.BoatRacingPlugin;
import es.jaie55.boatracing.util.Text;
import net.kyori.adventure.text.Component;
import net.wesjd.anvilgui.AnvilGUI;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
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
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * UI for map-vote administration and player voting.
 */
public class VoteGUI implements Listener {
    private final BoatRacingPlugin plugin;
    private final Component TITLE_ADMIN;
    private final Component TITLE_PLAYER;
    private final NamespacedKey KEY_ACTION;
    private final NamespacedKey KEY_TRACK;
    private final NamespacedKey KEY_SECONDS;

    private final Map<UUID, LinkedHashSet<String>> pendingOptionsByPlayer = new HashMap<>();
    private final Map<UUID, Long> pendingSecondsByPlayer = new HashMap<>();

    public VoteGUI(BoatRacingPlugin plugin) {
        this.plugin = plugin;
        this.TITLE_ADMIN = Text.title(plugin.msg().get("gui.vote.title-admin"));
        this.TITLE_PLAYER = Text.title(plugin.msg().get("gui.vote.title-player"));
        this.KEY_ACTION = new NamespacedKey(plugin, "vote-ui-action");
        this.KEY_TRACK = new NamespacedKey(plugin, "vote-ui-track");
        this.KEY_SECONDS = new NamespacedKey(plugin, "vote-ui-seconds");
    }

    public void openAdminSetup(Player p) {
        if (!(p.hasPermission("boatracing.race.admin") || p.hasPermission("boatracing.setup"))) {
            p.sendMessage(Text.colorize(plugin.pref() + plugin.msg().get("general.no-permission")));
            p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.6f);
            return;
        }

        List<String> allTracks = listVoteTracks();
        LinkedHashSet<String> selected = pendingOptionsByPlayer.computeIfAbsent(p.getUniqueId(), id -> {
            LinkedHashSet<String> defaults = new LinkedHashSet<>();
            for (String t : allTracks) {
                defaults.add(t);
                if (defaults.size() >= 2) break;
            }
            return defaults;
        });
        long seconds = pendingSecondsByPlayer.getOrDefault(p.getUniqueId(), 30L);

        Inventory inv = Bukkit.createInventory(null, 54, TITLE_ADMIN);
        fill(inv, pane(Material.GRAY_STAINED_GLASS_PANE));

        int slot = 0;
        for (String track : allTracks) {
            if (slot >= 45) break;
            boolean isSelected = selected.stream().anyMatch(s -> s.equalsIgnoreCase(track));
            Material mat = isSelected ? Material.FILLED_MAP : Material.MAP;
            String titleKey = isSelected ? "gui.vote.btn-track-selected" : "gui.vote.btn-track-unselected";
            ItemStack item = new ItemStack(mat);
            ItemMeta im = item.getItemMeta();
            if (im != null) {
                im.displayName(Text.item(plugin.msg().get(titleKey, "track", track)));
                List<String> lore = new ArrayList<>();
                lore.add(plugin.msg().get(isSelected ? "gui.vote.lore-selected-yes" : "gui.vote.lore-selected-no"));
                lore.add(plugin.msg().get("gui.vote.lore-click-toggle"));
                im.lore(Text.lore(lore));
                im.getPersistentDataContainer().set(KEY_ACTION, PersistentDataType.STRING, "toggle-track");
                im.getPersistentDataContainer().set(KEY_TRACK, PersistentDataType.STRING, track);
                im.addItemFlags(ItemFlag.values());
                item.setItemMeta(im);
            }
            inv.setItem(slot++, item);
        }

        int base = inv.getSize() - 9;
        inv.setItem(base, action(Material.ARROW, plugin.msg().get("gui.common.back"), "back"));
        inv.setItem(base + 2, actionWithSeconds(Material.CLOCK, plugin.msg().get("gui.vote.btn-seconds", "seconds", "30"), "set-seconds", 30L));
        inv.setItem(base + 3, actionWithSeconds(Material.CLOCK, plugin.msg().get("gui.vote.btn-seconds", "seconds", "45"), "set-seconds", 45L));
        inv.setItem(base + 4, actionWithSeconds(Material.CLOCK, plugin.msg().get("gui.vote.btn-seconds", "seconds", "60"), "set-seconds", 60L));
        inv.setItem(base + 5, action(Material.NAME_TAG, plugin.msg().get("gui.vote.btn-custom-seconds"), "set-seconds-custom"));
        inv.setItem(base + 6, action(Material.SUNFLOWER, plugin.msg().get("gui.vote.btn-refresh"), "refresh"));

        ItemStack start = new ItemStack(Material.EMERALD_BLOCK);
        ItemMeta sim = start.getItemMeta();
        if (sim != null) {
            sim.displayName(Text.item(plugin.msg().get("gui.vote.btn-start")));
            List<String> lore = Arrays.asList(
                    plugin.msg().get("gui.vote.lore-selected-count", "count", selected.size()),
                    plugin.msg().get("gui.vote.lore-current-seconds", "seconds", seconds),
                    plugin.msg().get(plugin.isMapVoteOpen() ? "gui.vote.status-open" : "gui.vote.status-closed")
            );
            sim.lore(Text.lore(lore));
            sim.getPersistentDataContainer().set(KEY_ACTION, PersistentDataType.STRING, "start-vote");
            sim.addItemFlags(ItemFlag.values());
            start.setItemMeta(sim);
        }
        inv.setItem(base + 7, start);

        inv.setItem(base + 8, action(Material.BARRIER, plugin.msg().get("gui.vote.btn-close-now"), "close-vote-now"));

        p.openInventory(inv);
        p.playSound(p.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 0.8f, 1.2f);
    }

    public void openPlayerVote(Player p) {
        if (!plugin.isMapVoteOpen()) {
            p.sendMessage(Text.colorize(plugin.pref() + plugin.msg().get("race.vote.no-active")));
            p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.6f);
            return;
        }

        Map<String, Integer> snapshot = plugin.getMapVoteCountsSnapshot();
        Inventory inv = Bukkit.createInventory(null, 36, TITLE_PLAYER);
        fill(inv, pane(Material.GRAY_STAINED_GLASS_PANE));

        int slot = 0;
        for (Map.Entry<String, Integer> e : snapshot.entrySet()) {
            if (slot >= 27) break;
            String track = e.getKey();
            int count = e.getValue();

            ItemStack item = new ItemStack(Material.FILLED_MAP);
            ItemMeta im = item.getItemMeta();
            if (im != null) {
                im.displayName(Text.item(plugin.msg().get("gui.vote.btn-track-unselected", "track", track)));
                List<String> lore = new ArrayList<>();
                lore.add(plugin.msg().get("gui.vote.lore-votes", "count", count));
                lore.add(plugin.msg().get("gui.vote.lore-click-vote"));
                im.lore(Text.lore(lore));
                im.getPersistentDataContainer().set(KEY_ACTION, PersistentDataType.STRING, "vote-track");
                im.getPersistentDataContainer().set(KEY_TRACK, PersistentDataType.STRING, track);
                im.addItemFlags(ItemFlag.values());
                item.setItemMeta(im);
            }
            inv.setItem(slot++, item);
        }

        int base = inv.getSize() - 9;
        inv.setItem(base + 6, action(Material.SUNFLOWER, plugin.msg().get("gui.vote.btn-refresh"), "refresh-player"));
        inv.setItem(base + 8, action(Material.BARRIER, plugin.msg().get("gui.vote.btn-close"), "close"));

        p.openInventory(inv);
        p.playSound(p.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 0.8f, 1.2f);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent e) {
        if (e.getView().getTopInventory() == null) return;
        String title = Text.plain(e.getView().title());
        boolean inAdmin = title.equals(Text.plain(TITLE_ADMIN));
        boolean inPlayer = title.equals(Text.plain(TITLE_PLAYER));
        if (!inAdmin && !inPlayer) return;

        e.setCancelled(true);
        if (e.getClickedInventory() == null || e.getClickedInventory() != e.getView().getTopInventory()) return;
        HumanEntity he = e.getWhoClicked();
        if (!(he instanceof Player p)) return;

        ItemStack it = e.getCurrentItem();
        if (it == null || it.getType() == Material.AIR) return;
        ItemMeta im = it.getItemMeta();
        if (im == null) return;

        String action = im.getPersistentDataContainer().get(KEY_ACTION, PersistentDataType.STRING);
        if (action == null) return;

        if (inAdmin) {
            handleAdminAction(p, action, im);
            return;
        }
        handlePlayerAction(p, action, im);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory() == null) return;
        String title = Text.plain(e.getView().title());
        if (title.equals(Text.plain(TITLE_ADMIN)) || title.equals(Text.plain(TITLE_PLAYER))) {
            e.setCancelled(true);
        }
    }

    private void handleAdminAction(Player p, String action, ItemMeta im) {
        LinkedHashSet<String> selected = pendingOptionsByPlayer.computeIfAbsent(p.getUniqueId(), id -> new LinkedHashSet<>());

        switch (action) {
            case "toggle-track" -> {
                String track = im.getPersistentDataContainer().get(KEY_TRACK, PersistentDataType.STRING);
                if (track == null || track.isBlank()) return;
                String existing = findIgnoreCase(selected, track);
                if (existing != null) selected.remove(existing);
                else selected.add(track);
                openAdminSetup(p);
            }
            case "set-seconds" -> {
                Long seconds = im.getPersistentDataContainer().get(KEY_SECONDS, PersistentDataType.LONG);
                if (seconds == null) return;
                pendingSecondsByPlayer.put(p.getUniqueId(), Math.max(10L, seconds));
                openAdminSetup(p);
            }
            case "set-seconds-custom" -> openCustomSecondsAnvil(p);
            case "start-vote" -> {
                long seconds = pendingSecondsByPlayer.getOrDefault(p.getUniqueId(), 30L);
                boolean ok = plugin.startMapVote(p, selected, seconds, "boatracing");
                if (ok) openPlayerVote(p);
            }
            case "close-vote-now" -> {
                plugin.closeMapVoteFromAdmin(p);
                openAdminSetup(p);
            }
            case "refresh" -> openAdminSetup(p);
            case "back" -> plugin.getAdminRaceGUI().open(p);
            default -> {
            }
        }
    }

    private void handlePlayerAction(Player p, String action, ItemMeta im) {
        switch (action) {
            case "vote-track" -> {
                String track = im.getPersistentDataContainer().get(KEY_TRACK, PersistentDataType.STRING);
                if (track == null || track.isBlank()) return;
                plugin.submitMapVote(p, track);
                if (plugin.isMapVoteOpen()) openPlayerVote(p);
                else p.closeInventory();
            }
            case "refresh-player" -> openPlayerVote(p);
            case "close" -> p.closeInventory();
            default -> {
            }
        }
    }

    private void openCustomSecondsAnvil(Player p) {
        ItemStack left = new ItemStack(Material.CLOCK);
        ItemMeta lm = left.getItemMeta();
        if (lm != null) {
            lm.displayName(Component.empty());
            left.setItemMeta(lm);
        }

        new AnvilGUI.Builder()
                .title(plugin.msg().get("gui.vote.anvil-seconds"))
                .text("30")
                .itemLeft(left)
                .interactableSlots()
                .onClick((slot, state) -> {
                    if (slot != AnvilGUI.Slot.OUTPUT) return java.util.Collections.emptyList();
                    String input = state.getText() == null ? "" : state.getText().trim();
                    try {
                        long seconds = Math.max(10L, Long.parseLong(input));
                        pendingSecondsByPlayer.put(state.getPlayer().getUniqueId(), seconds);
                        return java.util.Arrays.asList(
                                AnvilGUI.ResponseAction.close(),
                                AnvilGUI.ResponseAction.run(() -> openAdminSetup(state.getPlayer()))
                        );
                    } catch (NumberFormatException ignored) {
                        return java.util.Arrays.asList(AnvilGUI.ResponseAction.replaceInputText(input));
                    }
                })
                .plugin(plugin)
                .open(p);
    }

    private List<String> listVoteTracks() {
        List<String> names = new ArrayList<>();
        if (plugin.getTrackLibrary() != null) names.addAll(plugin.getTrackLibrary().list());
        if (names.stream().noneMatch(n -> n.equalsIgnoreCase("unsaved"))) names.add("unsaved");
        return names;
    }

    private static String findIgnoreCase(LinkedHashSet<String> set, String target) {
        for (String value : set) {
            if (value.equalsIgnoreCase(target)) return value;
        }
        return null;
    }

    private ItemStack action(Material mat, String titleLegacy, String action) {
        ItemStack it = new ItemStack(mat);
        ItemMeta im = it.getItemMeta();
        if (im != null) {
            im.displayName(Text.item(titleLegacy));
            im.getPersistentDataContainer().set(KEY_ACTION, PersistentDataType.STRING, action);
            im.addItemFlags(ItemFlag.values());
            it.setItemMeta(im);
        }
        return it;
    }

    private ItemStack actionWithSeconds(Material mat, String titleLegacy, String action, long seconds) {
        ItemStack it = new ItemStack(mat);
        ItemMeta im = it.getItemMeta();
        if (im != null) {
            im.displayName(Text.item(titleLegacy));
            im.getPersistentDataContainer().set(KEY_ACTION, PersistentDataType.STRING, action);
            im.getPersistentDataContainer().set(KEY_SECONDS, PersistentDataType.LONG, seconds);
            im.addItemFlags(ItemFlag.values());
            it.setItemMeta(im);
        }
        return it;
    }

    private static ItemStack pane(Material mat) {
        ItemStack it = new ItemStack(mat);
        ItemMeta im = it.getItemMeta();
        if (im != null) {
            im.displayName(Component.text(" "));
            im.addItemFlags(ItemFlag.values());
            it.setItemMeta(im);
        }
        return it;
    }

    private static void fill(Inventory inv, ItemStack filler) {
        for (int i = 0; i < inv.getSize(); i++) {
            if (inv.getItem(i) == null || inv.getItem(i).getType() == Material.AIR) {
                inv.setItem(i, filler);
            }
        }
    }
}
