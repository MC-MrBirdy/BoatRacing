package es.jaie55.boatracing.ui;

import es.jaie55.boatracing.BoatRacingPlugin;
import es.jaie55.boatracing.track.TrackLibrary;
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

/**
 * GUI for managing multiple named tracks: create, select, delete, and save-as current.
 */
public class AdminTracksGUI implements Listener {
    private final Component TITLE;
    private final Component TITLE_CONFIRM;
    private final BoatRacingPlugin plugin;
    private final TrackLibrary library;
    private final org.bukkit.NamespacedKey KEY_NAME;

    public AdminTracksGUI(BoatRacingPlugin plugin, TrackLibrary library) {
        this.plugin = plugin;
        this.library = library;
        this.KEY_NAME = new org.bukkit.NamespacedKey(plugin, "adm-track-name");
        this.TITLE = Text.title(plugin.msg().get("gui.tracks.title"));
        this.TITLE_CONFIRM = Text.title(plugin.msg().get("gui.tracks.title-confirm"));
    }

    public void open(Player p) {
        int size = 54;
        Inventory inv = Bukkit.createInventory(null, size, TITLE);
        java.util.List<String> names = library.list();
        int slot = 0;
        String current = library.getCurrent();
        for (String n : names) {
            if (slot >= size - 9) break;
            ItemStack it = new ItemStack(Material.MAP);
            ItemMeta im = it.getItemMeta();
            if (im != null) {
                im.displayName(Text.item("&f" + n + (current != null && current.equalsIgnoreCase(n) ? plugin.msg().get("gui.tracks.lore-selected") : "")));
                java.util.List<String> lore = new java.util.ArrayList<>();
                lore.add(plugin.msg().get("gui.tracks.lore-click-load"));
                lore.add(plugin.msg().get("gui.tracks.lore-right-rename"));
                lore.add(plugin.msg().get("gui.tracks.lore-shift-delete"));
                im.lore(Text.lore(lore));
                im.addItemFlags(ItemFlag.values());
                im.getPersistentDataContainer().set(KEY_NAME, org.bukkit.persistence.PersistentDataType.STRING, n);
                it.setItemMeta(im);
            }
            inv.setItem(slot++, it);
        }
        // Bar
        fillRow(inv, size - 9, pane(Material.GRAY_STAINED_GLASS_PANE));
        inv.setItem(size - 9, button(Material.ARROW, Text.item(plugin.msg().get("gui.common.back"))));
        // Create button with description
        {
            ItemStack it = new ItemStack(Material.ANVIL);
            ItemMeta im = it.getItemMeta();
            if (im != null) {
                im.displayName(Text.item(plugin.msg().get("gui.tracks.btn-create")));
                java.util.List<String> lore = new java.util.ArrayList<>();
                lore.add(plugin.msg().get("gui.tracks.lore-create"));
                im.lore(Text.lore(lore));
                it.setItemMeta(im);
            }
            inv.setItem(size - 5, it);
        }
    // (Removed) Save as button
        // Reapply button with description
        {
            ItemStack it = new ItemStack(Material.COMPASS);
            ItemMeta im = it.getItemMeta();
            if (im != null) {
                im.displayName(Text.item(plugin.msg().get("gui.tracks.btn-reapply")));
                java.util.List<String> lore = new java.util.ArrayList<>();
                lore.add(plugin.msg().get("gui.tracks.lore-reapply"));
                im.lore(Text.lore(lore));
                it.setItemMeta(im);
            }
            inv.setItem(size - 1, it);
        }
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
        int base = e.getInventory().getSize() - 9;
        int slot = e.getSlot();
    if (slot == base) { plugin.getAdminGUI().openMain(p); return; }
    if (slot == base + 4) { openAnvilCreate(p); return; }
        if (slot == base + 8) { // reapply selected
            plugin.getTrackConfig().load();
            p.sendMessage(Text.colorize(plugin.pref() + plugin.msg().get("admin.track.reapplied")));
            p.playSound(p.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 0.9f, 1.1f);
            open(p);
            return;
        }
        ItemStack it = e.getCurrentItem(); if (it == null) return;
        ItemMeta im = it.getItemMeta(); if (im == null) return;
        String name = im.getPersistentDataContainer().get(KEY_NAME, org.bukkit.persistence.PersistentDataType.STRING);
        if (name == null) return;
        // Click: load; right-click: rename; shift-right-click: delete (with confirmation)
        if (e.getClick() == org.bukkit.event.inventory.ClickType.SHIFT_RIGHT) {
            openDeleteConfirm(p, name);
            return;
        }
        if (e.getClick() == org.bukkit.event.inventory.ClickType.RIGHT) {
            openAnvilRename(p, name);
            return;
        }
        if (!library.select(name)) {
            p.sendMessage(Text.colorize(plugin.pref() + plugin.msg().get("admin.track.load-failed")));
            return;
        }
    p.sendMessage(Text.colorize(plugin.pref() + plugin.msg().get("admin.track.selected", "name", name)));
        p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.9f, 1.2f);
        open(p);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory() == null) return;
        String plain = Text.plain(e.getView().title());
        if (plain.equals(Text.plain(TITLE)) || plain.equals(Text.plain(TITLE_CONFIRM))) e.setCancelled(true);
    }

    private void openAnvilCreate(Player p) { openAnvil(p, "create", plugin.msg().get("gui.tracks.anvil-track-name"), " "); }

    private void openAnvilRename(Player p, String oldName) { openAnvil(p, "rename:" + oldName, plugin.msg().get("gui.tracks.anvil-track-name"), oldName); }

    private void openAnvil(Player p, String action, String title, String initialText) {
        ItemStack left = new ItemStack(Material.PAPER);
        ItemMeta lm = left.getItemMeta(); if (lm != null) { lm.displayName(Component.empty()); left.setItemMeta(lm); }
        new AnvilGUI.Builder()
                .title(title)
                .text(initialText == null ? " " : initialText)
                .itemLeft(left)
                .interactableSlots()
                .onClick((slot, state) -> {
                    if (slot != AnvilGUI.Slot.OUTPUT) return Collections.emptyList();
                    String input = state.getText() == null ? "" : state.getText().trim();
                    return handleAnvil(state.getPlayer(), action, input);
                })
                .plugin(plugin)
                .open(p);
    }

    private List<AnvilGUI.ResponseAction> handleAnvil(Player p, String action, String input) {
        if (input.isEmpty()) return Collections.singletonList(AnvilGUI.ResponseAction.close());
        if ("create".equals(action)) {
            if (library.exists(input)) {
                p.sendMessage(Text.colorize(plugin.pref() + plugin.msg().get("admin.track.exists")));
                return Arrays.asList(AnvilGUI.ResponseAction.replaceInputText(input));
            }
            if (!library.create(input)) {
                p.sendMessage(Text.colorize(plugin.pref() + plugin.msg().get("admin.track.create-failed")));
                return Collections.singletonList(AnvilGUI.ResponseAction.close());
            }
            // Auto-load the newly created (empty) track as current
            boolean selected = library.select(input);
            if (selected) {
                p.sendMessage(Text.colorize(plugin.pref() + plugin.msg().get("admin.track.created", "name", input)));
                // Clickable tip to paste the wizard command in chat
                p.sendMessage(Text.c(plugin.msg().get("setup.tip-label")).append(Text.suggest(plugin.msg().get("setup.tip-open-wizard"),"/boatracing setup wizard")));
                p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.9f, 1.2f);
            } else {
                p.sendMessage(Text.colorize(plugin.pref() + plugin.msg().get("admin.track.created-not-loaded")));
                p.playSound(p.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 0.9f, 1.1f);
            }
            return Arrays.asList(
                AnvilGUI.ResponseAction.close(),
                AnvilGUI.ResponseAction.run(() -> {
                    // Close the parent GUI so the admin can type the wizard command immediately
                    p.closeInventory();
                })
            );
        }
        if (action.startsWith("rename:")) {
            String oldName = action.substring("rename:".length());
            String oldNorm = library.normalizeName(oldName);
            String newNorm = library.normalizeName(input);
            if (newNorm.isEmpty()) {
                p.sendMessage(Text.colorize(plugin.pref() + plugin.msg().get("admin.track.create-failed")));
                return Collections.singletonList(AnvilGUI.ResponseAction.close());
            }
            if (!oldNorm.equalsIgnoreCase(newNorm) && library.exists(newNorm)) {
                p.sendMessage(Text.colorize(plugin.pref() + plugin.msg().get("admin.track.exists")));
                return Arrays.asList(AnvilGUI.ResponseAction.replaceInputText(input));
            }
            if (plugin.isTrackSessionBusy(oldNorm)) {
                p.sendMessage(Text.colorize(plugin.pref() + plugin.msg().get("race.already-running")));
                return Collections.singletonList(AnvilGUI.ResponseAction.close());
            }
            if (!library.rename(oldNorm, newNorm)) {
                p.sendMessage(Text.colorize(plugin.pref() + plugin.msg().get("admin.track.create-failed")));
                return Collections.singletonList(AnvilGUI.ResponseAction.close());
            }
            plugin.discardInactiveRaceSession(oldNorm);
            plugin.discardInactiveRaceSession(newNorm);
            library.select(newNorm);
            p.sendMessage(Text.colorize(plugin.pref() + plugin.msg().get("admin.track.selected", "name", newNorm)));
            p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.9f, 1.2f);
            return Arrays.asList(
                AnvilGUI.ResponseAction.close(),
                AnvilGUI.ResponseAction.run(() -> open(p))
            );
        }
        // 'Save as…' flow removed
        return Collections.singletonList(AnvilGUI.ResponseAction.close());
    }

    // Small UI helpers (local copy)
    private static ItemStack button(Material mat, Component name) { ItemStack it = new ItemStack(mat); ItemMeta im = it.getItemMeta(); if (im != null) { im.displayName(name); im.addItemFlags(ItemFlag.values()); it.setItemMeta(im);} return it; }
    private static ItemStack pane(Material mat) { ItemStack it = new ItemStack(mat); ItemMeta im = it.getItemMeta(); if (im != null) { im.displayName(Component.text(" ")); im.addItemFlags(ItemFlag.values()); it.setItemMeta(im);} return it; }
    private static void fillRow(Inventory inv, int start, ItemStack filler) { for (int i=0;i<9;i++) if (inv.getItem(start+i)==null || inv.getItem(start+i).getType()==Material.AIR) inv.setItem(start+i, filler); }

    private void openDeleteConfirm(Player p, String name) {
        Inventory inv = Bukkit.createInventory(null, 27, TITLE_CONFIRM);
        ItemStack yes = new ItemStack(Material.RED_CONCRETE);
        ItemMeta yim = yes.getItemMeta();
        if (yim != null) {
            yim.displayName(Text.item(plugin.msg().get("gui.tracks.btn-delete-now")));
            yim.getPersistentDataContainer().set(KEY_NAME, org.bukkit.persistence.PersistentDataType.STRING, name);
            yes.setItemMeta(yim);
        }
        inv.setItem(13, yes);
        inv.setItem(18, button(Material.ARROW, Text.item(plugin.msg().get("gui.common.back"))));
        // Background
        ItemStack bg = pane(Material.RED_STAINED_GLASS_PANE);
        for (int i=0;i<inv.getSize();i++) if (inv.getItem(i)==null) inv.setItem(i, bg);
        p.openInventory(inv);
        p.playSound(p.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 0.8f, 1.2f);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClickConfirm(InventoryClickEvent e) {
        if (e.getView().getTopInventory() == null) return;
        if (!Text.plain(e.getView().title()).equals(Text.plain(TITLE_CONFIRM))) return;
        e.setCancelled(true);
        if (e.getClickedInventory() == null || e.getClickedInventory() != e.getView().getTopInventory()) return;
        if (!(e.getWhoClicked() instanceof Player p)) return;
        int slot = e.getSlot();
        if (slot == 18) { open(p); return; }
        ItemStack it = e.getCurrentItem(); if (it == null) return;
        if (it.getType() != Material.RED_CONCRETE) return;
        ItemMeta im = it.getItemMeta(); if (im == null) return;
        String name = im.getPersistentDataContainer().get(KEY_NAME, org.bukkit.persistence.PersistentDataType.STRING);
        if (name == null) { open(p); return; }
        if (!library.delete(name)) {
            p.sendMessage(Text.colorize(plugin.pref() + plugin.msg().get("admin.track.delete-failed")));
            open(p); return;
        }
        p.sendMessage(Text.colorize(plugin.pref() + plugin.msg().get("admin.track.deleted", "name", name)));
        p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_ANVIL_DESTROY, 0.8f, 0.9f);
        open(p);
    }
}
