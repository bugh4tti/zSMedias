package com.zs.medias.menu;

import com.zs.medias.ZSMedias;
import com.zs.medias.managers.Creator;
import com.zs.medias.managers.MessageManager;
import com.zs.medias.util.CommandUtil;
import com.zs.medias.util.ItemUtil;
import com.zs.medias.util.Text;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class CreadoresMenu implements Listener {

    private enum Type {
        MAIN,
        CONFIRM
    }

    private static final class MenuHolder implements InventoryHolder {
        private final Type type;
        private final String creatorId;
        private final Map<Integer, String> slots = new HashMap<>();
        private Inventory inventory;
        private int closeSlot = -1;
        private int claimSlot = -1;
        private int confirmSlot = -1;
        private int cancelSlot = -1;

        private MenuHolder(Type type, String creatorId) {
            this.type = type;
            this.creatorId = creatorId;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private final ZSMedias plugin;
    // Jugadores que tienen abierto el menú principal (para actualizar los votos en vivo)
    private final Map<UUID, Inventory> viewers = new HashMap<>();

    public CreadoresMenu(ZSMedias plugin) {
        this.plugin = plugin;
    }

    // =====================================================
    //  Menú principal
    // =====================================================

    public void open(Player player) {
        if (plugin.getCreators().getAll().isEmpty()) {
            plugin.getMessages().send(player, "creadores.no-creators");
            return;
        }

        FileConfiguration yml = plugin.getCreators().getYml();
        int rows = clamp(yml.getInt("menu.rows", 4), 1, 6);

        MenuHolder holder = new MenuHolder(Type.MAIN, null);
        Inventory inventory = Bukkit.createInventory(holder, rows * 9, Text.color(yml.getString("menu.title", "&8Creadores")));
        holder.inventory = inventory;

        populateMain(inventory, player);
        player.openInventory(inventory);
        viewers.put(player.getUniqueId(), inventory);
    }

    private void populateMain(Inventory inventory, Player player) {
        MenuHolder holder = (MenuHolder) inventory.getHolder();
        if (holder == null) {
            return;
        }

        holder.slots.clear();
        inventory.clear();

        FileConfiguration yml = plugin.getCreators().getYml();
        int size = inventory.getSize();

        if (yml.getBoolean("menu.filler.enabled", true)) {
            ItemStack filler = ItemUtil.named(
                    ItemUtil.material(yml.getString("menu.filler.material"), Material.BLACK_STAINED_GLASS_PANE),
                    yml.getString("menu.filler.name", " "),
                    null
            );
            for (int i = 0; i < size; i++) {
                inventory.setItem(i, filler);
            }
        }

        holder.closeSlot = -1;
        if (yml.getBoolean("menu.close-button.enabled", true)) {
            int closeSlot = yml.getInt("menu.close-button.slot", size - 5);
            if (closeSlot >= 0 && closeSlot < size) {
                holder.closeSlot = closeSlot;
                inventory.setItem(closeSlot, ItemUtil.named(
                        ItemUtil.material(yml.getString("menu.close-button.material"), Material.BARRIER),
                        yml.getString("menu.close-button.name", "&cCerrar"),
                        yml.getStringList("menu.close-button.lore")
                ));
            }
        }

        // El botón de recompensa diaria se oculta si creadores.daily-enabled es false
        holder.claimSlot = -1;
        if (plugin.getRewards().isDailyEnabled() && yml.getBoolean("menu.claim-button.enabled", true)) {
            int claimSlot = yml.getInt("menu.claim-button.slot", size - 4);
            if (claimSlot >= 0 && claimSlot < size && claimSlot != holder.closeSlot) {
                holder.claimSlot = claimSlot;
                inventory.setItem(claimSlot, buildClaimButton(player));
            }
        }

        String chosen = plugin.getData().getChoice(player.getUniqueId());
        for (Creator creator : plugin.getCreators().getAll()) {
            int slot = creator.slot();
            if (slot < 0 || slot >= size || slot == holder.closeSlot || slot == holder.claimSlot
                    || holder.slots.containsKey(slot)) {
                slot = firstFreeSlot(holder, size);
                if (slot == -1) {
                    continue;
                }
            }
            holder.slots.put(slot, creator.id());
            inventory.setItem(slot, buildHead(creator, player, chosen));
        }
    }

    private int firstFreeSlot(MenuHolder holder, int size) {
        for (int i = 0; i < size; i++) {
            int column = i % 9;
            if (column == 0 || column == 8) {
                continue;
            }
            if (i == holder.closeSlot || i == holder.claimSlot || holder.slots.containsKey(i)) {
                continue;
            }
            return i;
        }
        return -1;
    }

    private ItemStack buildHead(Creator creator, Player player, String chosen) {
        FileConfiguration yml = plugin.getCreators().getYml();
        boolean selected = chosen != null && chosen.equalsIgnoreCase(creator.id());

        String statusKey;
        if (chosen == null || (!selected && canChange(player))) {
            statusKey = "available";
        } else if (selected) {
            statusKey = "selected";
        } else {
            statusKey = "locked";
        }

        String status = yml.getString("menu.status." + statusKey, "");
        String[] pairs = placeholders(creator, player, status);

        ItemStack item = ItemUtil.createHead(creator.texture());
        ItemUtil.apply(item, Text.replace(creator.displayName(), pairs), Text.replace(creator.lore(), pairs));

        if (selected && yml.getBoolean("menu.glow-selected", true)) {
            ItemUtil.glow(item);
        }
        return item;
    }

    private ItemStack buildClaimButton(Player player) {
        FileConfiguration yml = plugin.getCreators().getYml();

        Creator creator = plugin.getCreators().get(plugin.getData().getChoice(player.getUniqueId()));

        String statusKey;
        long remaining = 0;
        if (creator == null) {
            statusKey = "no-creator";
        } else if (creator.daily().isEmpty()) {
            statusKey = "no-reward";
        } else {
            remaining = plugin.getRewards().dailyRemaining(player);
            statusKey = remaining > 0 ? "cooldown" : "ready";
        }

        String time = Text.formatTime(remaining);
        String status = Text.replace(yml.getString("menu.claim-button.status." + statusKey, ""), "{time}", time);

        String[] pairs = {
                "{status}", status,
                "{time}", time,
                "{creator}", creator == null ? "" : creator.name(),
                "{creator_color}", creator == null ? "" : creator.color()
        };

        return ItemUtil.named(
                ItemUtil.material(yml.getString("menu.claim-button.material"), Material.CHEST),
                Text.replace(yml.getString("menu.claim-button.name", "&6Recompensa diaria"), pairs),
                Text.replace(yml.getStringList("menu.claim-button.lore"), pairs)
        );
    }

    /** Actualiza en vivo los votos de todos los que tienen el menú abierto. */
    public void refreshOpen() {
        for (Map.Entry<UUID, Inventory> entry : new HashMap<>(viewers).entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null || !player.isOnline()) {
                viewers.remove(entry.getKey());
                continue;
            }
            populateMain(entry.getValue(), player);
            player.updateInventory();
        }
    }

    // =====================================================
    //  Menú de confirmación
    // =====================================================

    private void openConfirm(Player player, Creator creator) {
        FileConfiguration yml = plugin.getCreators().getYml();
        int rows = clamp(yml.getInt("confirm-menu.rows", 3), 1, 6);
        int size = rows * 9;

        MenuHolder holder = new MenuHolder(Type.CONFIRM, creator.id());
        Inventory inventory = Bukkit.createInventory(
                holder,
                size,
                Text.color(yml.getString("confirm-menu.title", "&8Confirmar creador"))
        );
        holder.inventory = inventory;

        String[] pairs = placeholders(creator, player, "");

        if (yml.getBoolean("confirm-menu.filler.enabled", true)) {
            ItemStack filler = ItemUtil.named(
                    ItemUtil.material(yml.getString("confirm-menu.filler.material"), Material.GRAY_STAINED_GLASS_PANE),
                    yml.getString("confirm-menu.filler.name", " "),
                    null
            );
            for (int i = 0; i < size; i++) {
                inventory.setItem(i, filler);
            }
        }

        int headSlot = yml.getInt("confirm-menu.head-slot", 13);
        if (headSlot >= 0 && headSlot < size) {
            ItemStack head = ItemUtil.createHead(creator.texture());
            ItemUtil.apply(head, Text.replace(creator.displayName(), pairs), null);
            inventory.setItem(headSlot, head);
        }

        int confirmSlot = yml.getInt("confirm-menu.confirm.slot", 11);
        if (confirmSlot >= 0 && confirmSlot < size) {
            holder.confirmSlot = confirmSlot;
            inventory.setItem(confirmSlot, ItemUtil.named(
                    ItemUtil.material(yml.getString("confirm-menu.confirm.material"), Material.LIME_STAINED_GLASS_PANE),
                    Text.replace(yml.getString("confirm-menu.confirm.name", "&aCONFIRMAR"), pairs),
                    Text.replace(yml.getStringList("confirm-menu.confirm.lore"), pairs)
            ));
        }

        int cancelSlot = yml.getInt("confirm-menu.cancel.slot", 15);
        if (cancelSlot >= 0 && cancelSlot < size) {
            holder.cancelSlot = cancelSlot;
            inventory.setItem(cancelSlot, ItemUtil.named(
                    ItemUtil.material(yml.getString("confirm-menu.cancel.material"), Material.RED_STAINED_GLASS_PANE),
                    Text.replace(yml.getString("confirm-menu.cancel.name", "&cCANCELAR"), pairs),
                    Text.replace(yml.getStringList("confirm-menu.cancel.lore"), pairs)
            ));
        }

        player.openInventory(inventory);
    }

    // =====================================================
    //  Eventos
    // =====================================================

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getInventory();
        if (!(top.getHolder() instanceof MenuHolder holder)) {
            return;
        }

        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        Inventory clicked = event.getClickedInventory();
        if (clicked == null || !clicked.equals(top)) {
            return;
        }

        int slot = event.getSlot();

        if (holder.type == Type.MAIN) {
            handleMainClick(player, holder, slot, event);
        } else {
            handleConfirmClick(player, holder, slot);
        }
    }

    private void handleMainClick(Player player, MenuHolder holder, int slot, InventoryClickEvent event) {
        if (slot == holder.closeSlot) {
            plugin.getAnnouncer().playSound(player, "creadores-click");
            later(player::closeInventory);
            return;
        }

        if (slot == holder.claimSlot) {
            later(() -> {
                plugin.getRewards().claimDaily(player);
                refreshOpen();
            });
            return;
        }

        String id = holder.slots.get(slot);
        if (id == null) {
            return;
        }

        Creator creator = plugin.getCreators().get(id);
        if (creator == null) {
            plugin.getMessages().send(player, "creadores.unavailable");
            return;
        }

        plugin.getAnnouncer().playSound(player, "creadores-click");

        if (event.getClick().isRightClick()) {
            sendLinks(player, creator);
        } else if (event.getClick().isLeftClick()) {
            tryChoose(player, creator);
        }
    }

    private void handleConfirmClick(Player player, MenuHolder holder, int slot) {
        if (slot == holder.confirmSlot) {
            Creator creator = plugin.getCreators().get(holder.creatorId);
            if (creator == null) {
                plugin.getMessages().send(player, "creadores.unavailable");
                later(player::closeInventory);
                return;
            }
            later(() -> finalizeChoice(player, creator));
        } else if (slot == holder.cancelSlot) {
            plugin.getAnnouncer().playSound(player, "creadores-click");
            later(() -> open(player));
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof MenuHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof MenuHolder holder && holder.type == Type.MAIN) {
            viewers.remove(event.getPlayer().getUniqueId());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        viewers.remove(event.getPlayer().getUniqueId());
    }

    // =====================================================
    //  Elección
    // =====================================================

    private void tryChoose(Player player, Creator creator) {
        String chosen = plugin.getData().getChoice(player.getUniqueId());

        if (chosen != null && (chosen.equalsIgnoreCase(creator.id()) || !canChange(player))) {
            plugin.getMessages().send(player, "creadores.already-chosen");
            return;
        }

        if (plugin.getCreators().getYml().getBoolean("confirm-menu.enabled", true)
                && plugin.getConfig().getBoolean("creadores.confirm-menu", true)) {
            later(() -> openConfirm(player, creator));
        } else {
            later(() -> finalizeChoice(player, creator));
        }
    }

    private void finalizeChoice(Player player, Creator creator) {
        UUID uuid = player.getUniqueId();
        MessageManager msg = plugin.getMessages();

        String previous = plugin.getData().getChoice(uuid);
        if (previous != null && (previous.equalsIgnoreCase(creator.id()) || !canChange(player))) {
            msg.send(player, "creadores.already-chosen");
            player.closeInventory();
            return;
        }

        plugin.getData().setChoice(uuid, creator.id());
        player.closeInventory();

        String[] pairs = placeholders(creator, player, "");

        // Las recompensas solo se dan la primera vez (cambiar de creador no las repite)
        if (previous == null) {
            CommandUtil.run(creator.rewards(), pairs);
        }

        List<String> lines = creator.message().isEmpty() ? msg.rawList("creadores.chosen-message") : creator.message();
        for (String line : lines) {
            player.sendMessage(Text.line(Text.replace(line, pairs)));
        }

        String title = creator.title() != null ? creator.title() : msg.raw("creadores.chosen-title");
        String subtitle = creator.subtitle() != null ? creator.subtitle() : msg.raw("creadores.chosen-subtitle");
        plugin.getAnnouncer().sendTitle(player, "creadores", Text.replace(title, pairs), Text.replace(subtitle, pairs));
        plugin.getAnnouncer().playSound(player, "creadores-elegir");

        // Un voto nuevo puede desbloquear un hito, y el bonus depende de si su creador está en directo
        plugin.getRewards().checkMilestones(creator);
        plugin.getLive().refreshBonus(player);

        refreshOpen();
    }

    // =====================================================
    //  Links (click derecho)
    // =====================================================

    private void sendLinks(Player player, Creator creator) {
        MessageManager msg = plugin.getMessages();
        String[] pairs = placeholders(creator, player, "");

        if (creator.links().isEmpty()) {
            player.sendMessage(Text.color(Text.replace(msg.raw("creadores.links-none"), pairs)));
            later(player::closeInventory);
            return;
        }

        player.sendMessage(Text.color(Text.replace(msg.raw("creadores.links-header"), pairs)));

        for (Map.Entry<String, String> entry : creator.links().entrySet()) {
            String link = entry.getValue();
            String lower = link.toLowerCase();
            if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
                link = "https://" + link;
            }

            String key = entry.getKey();
            String platformName = plugin.getConfig().getString(
                    "platforms." + key + ".name",
                    key.substring(0, 1).toUpperCase() + key.substring(1)
            );

            String line = Text.color(Text.replace(
                    msg.raw("creadores.links-line"),
                    "{platform}", platformName,
                    "{link}", link
            ));

            BaseComponent[] components = TextComponent.fromLegacyText(line);
            for (BaseComponent component : components) {
                component.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, link));
                component.setHoverEvent(new HoverEvent(
                        HoverEvent.Action.SHOW_TEXT,
                        new net.md_5.bungee.api.chat.hover.content.Text(Text.color(msg.raw("creadores.links-hover")))
                ));
            }
            player.spigot().sendMessage(components);
        }

        later(player::closeInventory);
    }

    // =====================================================
    //  Utilidades
    // =====================================================

    private boolean canChange(Player player) {
        return plugin.getConfig().getBoolean("creadores.allow-change", false)
                && player.hasPermission("zsmedias.creadores.change");
    }

    private String[] placeholders(Creator creator, Player player, String status) {
        int votes = plugin.getData().getVotes(creator.id());
        String votesText = plugin.getMessages().raw(votes == 1 ? "creadores.votes-singular" : "creadores.votes-plural");

        return new String[]{
                "{creator}", creator.name(),
                "{creator_color}", creator.color(),
                "{votes}", String.valueOf(votes),
                "{votes_text}", votesText,
                "{player}", player.getName(),
                "{status}", status == null ? "" : status
        };
    }

    private void later(Runnable runnable) {
        Bukkit.getScheduler().runTask(plugin, runnable);
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
                                          }
