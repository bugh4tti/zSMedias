package com.zs.medias.listeners;

import com.zs.medias.ZSMedias;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerListener implements Listener {

    private final ZSMedias plugin;

    public PlayerListener(ZSMedias plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // Pequeño delay para que los demás plugins terminen de cargar al jugador
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            plugin.getRewards().claimPendingMilestones(player);
            plugin.getLive().handleJoin(player);
        }, 40L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.getLive().handleQuit(event.getPlayer());
    }
}
