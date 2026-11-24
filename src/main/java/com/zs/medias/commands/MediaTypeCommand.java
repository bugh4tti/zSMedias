package com.zs.medias.commands;

import com.zs.medias.ZSMedias;
import com.zs.medias.managers.MessageManager;
import com.zs.medias.util.Text;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Maneja /directo y /video (mismo comportamiento, distinto tipo). */
public class MediaTypeCommand implements CommandExecutor, TabCompleter {

    private final ZSMedias plugin;
    private final String type;

    public MediaTypeCommand(ZSMedias plugin, String type) {
        this.plugin = plugin;
        this.type = type;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        MessageManager msg = plugin.getMessages();

        if (!(sender instanceof Player player)) {
            msg.send(sender, "general.only-players");
            return true;
        }

        if (!player.hasPermission("zsmedias." + type)) {
            msg.send(player, "general.no-permission");
            return true;
        }

        List<String> allowed = plugin.getConfig().getStringList(type + ".platforms");

        if (args.length < 2) {
            msg.send(player, type + ".usage", "{platforms}", String.join("|", allowed));
            return true;
        }

        String key = args[0].toLowerCase();
        boolean platformAllowed = false;
        for (String platformKey : allowed) {
            if (platformKey.equalsIgnoreCase(key)) {
                platformAllowed = true;
                break;
            }
        }

        ConfigurationSection platform = plugin.getConfig().getConfigurationSection("platforms." + key);
        if (!platformAllowed || platform == null) {
            msg.send(player, type + ".invalid-platform", "{platforms}", String.join(", ", allowed));
            return true;
        }

        String platformName = platform.getString("name", key);
        String platformColor = platform.getString("color", "#FFFFFF");

        String link = normalizeLink(args[1]);
        boolean validate = plugin.getConfig().getBoolean(type + ".validate-links", true);
        if (link == null || (validate && !matchesDomain(link, platform.getStringList("domains")))) {
            msg.send(player, type + ".invalid-link", "{platform}", platformName);
            return true;
        }

        boolean bypass = player.hasPermission("zsmedias.cooldown.bypass");
        if (!bypass) {
            long remaining = plugin.getCooldowns().getRemaining(player.getUniqueId(), type);
            if (remaining > 0) {
                msg.send(player, "general.cooldown", "{time}", Text.formatTime(remaining));
                return true;
            }
        }

        String[] pairs = {
                "{player}", player.getName(),
                "{platform}", platformName,
                "{platform_upper}", platformName.toUpperCase(),
                "{platform_color}", platformColor,
                "{link}", link
        };

        List<String> lines = Text.replace(msg.rawList(type + ".lines"), pairs);
        String button = Text.replace(msg.raw(type + ".button.text"), pairs);
        List<String> hover = Text.replace(msg.rawList(type + ".button.hover"), pairs);

        plugin.getAnnouncer().broadcast(lines, button, hover, link);

        plugin.getAnnouncer().effects(
                type,
                Text.replace(msg.raw(type + ".title"), pairs),
                Text.replace(msg.raw(type + ".subtitle"), pairs)
        );

        if (!bypass) {
            plugin.getCooldowns().set(player.getUniqueId(), type, plugin.getConfig().getLong("cooldowns." + type, 0));
        }
        return true;
    }

    /** Agrega https:// si falta y verifica que sea una URL con host. Devuelve null si es inválida. */
    private String normalizeLink(String input) {
        String link = input.trim();
        String lower = link.toLowerCase();
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
            link = "https://" + link;
        }

        try {
            URI uri = new URI(link);
            String host = uri.getHost();
            if (host == null || !host.contains(".")) {
                return null;
            }
            return link;
        } catch (URISyntaxException ex) {
            return null;
        }
    }

    private boolean matchesDomain(String link, List<String> domains) {
        try {
            String host = new URI(link).getHost();
            if (host == null) {
                return false;
            }
            host = host.toLowerCase();

            for (String domain : domains) {
                String d = domain.toLowerCase();
                if (host.equals(d) || host.endsWith("." + d)) {
                    return true;
                }
            }
        } catch (URISyntaxException ex) {
            return false;
        }
        return false;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> result = new ArrayList<>();
            for (String platform : plugin.getConfig().getStringList(type + ".platforms")) {
                if (platform.toLowerCase().startsWith(args[0].toLowerCase())) {
                    result.add(platform);
                }
            }
            return result;
        }
        if (args.length == 2) {
            return Collections.singletonList("https://");
        }
        return Collections.emptyList();
    }
}
