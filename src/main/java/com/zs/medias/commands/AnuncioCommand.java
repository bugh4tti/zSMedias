package com.zs.medias.commands;

import com.zs.medias.ZSMedias;
import com.zs.medias.managers.MessageManager;
import com.zs.medias.util.Text;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;

public class AnuncioCommand implements CommandExecutor, TabCompleter {

    private static final String TYPE = "anuncio";

    private final ZSMedias plugin;

    public AnuncioCommand(ZSMedias plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        MessageManager msg = plugin.getMessages();

        Player player = sender instanceof Player p ? p : null;

        if (player != null && !player.hasPermission("zsmedias.anuncio")) {
            msg.send(sender, "general.no-permission");
            return true;
        }

        if (args.length == 0) {
            msg.send(sender, "anuncio.usage");
            return true;
        }

        String content = String.join(" ", args);

        // Colores solo si está permitido y el jugador tiene permiso (la consola siempre puede)
        boolean colorsAllowed = plugin.getConfig().getBoolean("anuncio.allow-colors", true)
                && (player == null || player.hasPermission("zsmedias.anuncio.color"));
        if (!colorsAllowed) {
            content = Text.strip(content);
        }

        String visible = Text.strip(content);

        int max = plugin.getConfig().getInt("anuncio.max-length", 120);
        if (visible.length() > max) {
            msg.send(sender, "anuncio.too-long", "{max}", String.valueOf(max));
            return true;
        }

        String lowerVisible = visible.toLowerCase();
        for (String word : plugin.getConfig().getStringList("anuncio.blocked-words")) {
            if (word != null && !word.trim().isEmpty() && lowerVisible.contains(word.trim().toLowerCase())) {
                msg.send(sender, "anuncio.blocked-word");
                return true;
            }
        }

        boolean bypass = player == null || player.hasPermission("zsmedias.cooldown.bypass");
        if (!bypass) {
            long remaining = plugin.getCooldowns().getRemaining(player.getUniqueId(), TYPE);
            if (remaining > 0) {
                msg.send(sender, "general.cooldown", "{time}", Text.formatTime(remaining));
                return true;
            }
        }

        String senderName = player == null ? "Consola" : player.getName();

        // {message} va al final para que el contenido del jugador no se vuelva a procesar
        String[] pairs = {"{player}", senderName, "{message}", content};

        plugin.getAnnouncer().broadcast(Text.replace(msg.rawList("anuncio.lines"), pairs), null, null, null);

        plugin.getAnnouncer().effects(
                TYPE,
                Text.replace(msg.raw("anuncio.title"), "{player}", senderName),
                Text.replace(msg.raw("anuncio.subtitle"), "{player}", senderName)
        );

        if (!bypass) {
            plugin.getCooldowns().set(player.getUniqueId(), TYPE, plugin.getConfig().getLong("cooldowns.anuncio", 0));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return Collections.emptyList();
    }
}
