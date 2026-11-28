package com.zs.medias.menu;

import com.zs.medias.ZSMedias;
import com.zs.medias.managers.MessageManager;
import com.zs.medias.util.ItemUtil;
import com.zs.medias.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Menú /media config (solo admin): cooldowns, plataformas de /directo y /video
 * y el interruptor de recompensas diarias. Guarda en config.yml al hacer click.
 */
public class ConfigMenu implements Listener {

    private static final class Holder implements InventoryHolder {
        private final Map<Integer, String> actions = new HashMap<>();
        private Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private static final String[] COOLDOWN_TYPES = {"directo", "video", "anuncio"};
    private static final int[] COOLDOWN_SLOTS = {10, 12, 14};
    private static final int DAILY_SLOT = 16;
    private static final int DIRECTO_START = 19;
    private static final int VIDEO_START = 28;
    private static final int CLOSE_SLOT = 40;
    private static final int MAX_PLATFORMS = 7;
    private static final long MAX_COOLDOWN = 30L * 86400L;

    private final ZSMedias plugin;

    public ConfigMenu(ZSMedias plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        Holder holder = new Holder();
        Inventory inventory = Bukkit.createInventory(
                holder,
                45,
                Text.color(plugin.getMessages().raw("config-menu.title"))
        );
        holder.inventory = inventory;

        populate(holder);
        player.openInventory(inventory);
    }

    private void populate(Holder holder) {
        Inventory inventory = holder.inventory;
        MessageManager msg = plugin.getMessages();

        holder.actions.clear();
        inventory.clear();

        ItemStack filler = ItemUtil.named(Material.GRAY_STAINED_GLASS_PANE, " ", null);
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, filler);
        }

        // Cooldowns
        for (int i = 0; i < COOLDOWN_TYPES.length; i++) {
            String type = COOLDOWN_TYPES[i];
            long seconds = plugin.getConfig().getLong("cooldowns." + type, 0L);

            ItemStack item = ItemUtil.named(
                    Material.CLOCK,
                    Text.replace(msg.raw("config-menu.cooldown-name"), "{type}", type),
                    Text.replace(
                            msg.rawList("config-menu.cooldown-lore"),
                            "{time}", Text.formatTime(seconds),
                            "{step}", Text.formatTime(step(type))
                    )
            );

            inventory.setItem(COOLDOWN_SLOTS[i], item);
            holder.actions.put(COOLDOWN_SLOTS[i], "cooldown:" + type);
        }

        // Recompensas diarias
        boolean dailyEnabled = plugin.getRewards().isDailyEnabled();
        ItemStack daily = ItemUtil.named(
                Material.CHEST,
                msg.raw("config-menu.daily-name"),
                Text.replace(
                        msg.rawList("config-menu.daily-lore"),
                        "{state}", msg.raw(dailyEnabled ? "config-menu.state-on" : "config-menu.state-off")
                )
        );
        if (dailyEnabled) {
            ItemUtil.glow(daily);
        }
        inventory.setItem(DAILY_SLOT, daily);
        holder.actions.put(DAILY_SLOT, "daily");

        // Plataformas
        placePlatforms(holder, "directo", DIRECTO_START);
        placePlatforms(holder, "video", VIDEO_START);

        // Cerrar
        inventory.setItem(CLOSE_SLOT, ItemUtil.named(Material.BARRIER, msg.raw("config-menu.close-name"), null));
        holder.actions.put(CLOSE_SLOT, "close");
    }

    private void placePlatforms(Holder holder, String type, int start) {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("platforms");
        if (section == null) {
            return;
        }

        MessageManager msg = plugin.getMessages();
        List<String> enabled = plugin.getConfig().getStringList(type + ".platforms");

        int index = 0;
        for (String key : section.getKeys(false)) {
            if (index >= MAX_PLATFORMS) {
                break;
            }

            boolean on = containsIgnoreCase(enabled, key);
            String[] pairs = {
                    "{platform}", section.getString(key + ".name", key),
                    "{type}", type,
                    "{state}", msg.raw(on ? "config-menu.platform-on" : "config-menu.platform-off")
            };

            ItemStack item = ItemUtil.named(
                    on ? Material.LIME_DYE : Material.GRAY_DYE,
                    Text.replace(msg.raw(on ? "config-menu.platform-name-on" : "config-menu.platform-name-off"), pairs),
                    Text.replace(msg.rawList("config-menu.platform-lore"), pairs)
            );

            int slot = start + index;
            holder.inventory.setItem(slot, item);
            holder.actions.put(slot, "platform:" + type + ":" + key);
            index++;
        }
    }

    // =====================================================
    //  Eventos
    // =====================================================

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof Holder holder)) {
            return;
        }

        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        Inventory clicked = event.getClickedInventory();
        if (clicked == null || !clicked.equals(event.getInventory())) {
            return;
        }

        if (!player.hasPermission("zsmedias.admin")) {
            Bukkit.getScheduler().runTask(plugin, player::closeInventory);
            return;
        }

        String action = holder.actions.get(event.getSlot());
        if (action == null) {
            return;
        }

        if (action.equals("close")) {
            plugin.getAnnouncer().playSound(player, "creadores-click");
            Bukkit.getScheduler().runTask(plugin, player::closeInventory);
            return;
        }

        if (action.startsWith("cooldown:")) {
            editCooldown(action.substring("cooldown:".length()), event);
        } else if (action.equals("daily")) {
            plugin.getConfig().set("creadores.daily-enabled", !plugin.getRewards().isDailyEnabled());
        } else if (action.startsWith("platform:")) {
            String[] parts = action.split(":", 3);
            if (parts.length == 3) {
                togglePlatform(parts[1], parts[2]);
            }
        }

        plugin.saveConfig();
        plugin.getAnnouncer().playSound(player, "creadores-click");

        populate(holder);
        player.updateInventory();

        // El botón de recompensa diaria del menú /creadores depende de este interruptor
        plugin.getCreadoresMenu().refreshOpen();
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof Holder) {
            event.setCancelled(true);
        }
    }

    // =====================================================
    //  Acciones
    // =====================================================

    /** Click izquierdo suma, derecho resta, shift multiplica por 5. */
    private void editCooldown(String type, InventoryClickEvent event) {
        long current = plugin.getConfig().getLong("cooldowns." + type, 0L);
        long amount = step(type) * (event.isShiftClick() ? 5L : 1L);

        if (event.isLeftClick()) {
            current += amount;
        } else if (event.isRightClick()) {
            current -= amount;
        } else {
            return;
        }

        current = Math.max(0L, Math.min(MAX_COOLDOWN, current));
        plugin.getConfig().set("cooldowns." + type, current);
    }

    private void togglePlatform(String type, String key) {
        List<String> list = new ArrayList<>(plugin.getConfig().getStringList(type + ".platforms"));

        boolean removed = list.removeIf(entry -> entry.equalsIgnoreCase(key));
        if (!removed) {
            list.add(key);
        }

        plugin.getConfig().set(type + ".platforms", list);
    }

    private long step(String type) {
        switch (type) {
            case "directo":
                return 60L;
            case "video":
                return 1800L;
            default:
                return 3600L;
        }
    }

    private boolean containsIgnoreCase(List<String> list, String value) {
        for (String entry : list) {
            if (entry.equalsIgnoreCase(value)) {
                return true;
            }
        }
        return false;
    }
}
