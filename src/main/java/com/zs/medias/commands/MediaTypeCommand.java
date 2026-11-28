package com.zs.medias.commands;

import com.zs.medias.ZSMedias;
import com.zs.medias.managers.MessageManager;
import com.zs.medias.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
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

/**
 * Maneja /directo y /video.
 * /directo <plataforma> [link]                  -> sin link usa el canal vinculado con /media vincular
 * /directo execute <plataforma> <nick> [link]   -> anuncia el directo en nombre de otro jugador
 * /video <plataforma> <link>
 */
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

        if (type.equals("directo") && args.length >= 1 && args[0].equalsIgnoreCase("execute")) {
            return execute(sender, args);
        }

        if (!(sender instanceof Player player)) {
            msg.send(sender, "general.only-players");
            return true;
        }

        if (!player.hasPermission("zsmedias." + type)) {
            msg.send(player, "general.no-permission");
            return true;
        }

        List<String> allowed = plugin.getConfig().getStringList(type + ".platforms");

        // /directo acepta solo la plataforma (usa el canal vinculado); /video siempre necesita link
        int minArgs = type.equals("directo") ? 1 : 2;
        if (args.length < minArgs) {
            msg.send(player, type + ".usage", "{platforms}", String.join("|", allowed));
            return true;
        }

        String key = args[0].toLowerCase();
        ConfigurationSection platform = findPlatform(key, allowed);
        if (platform == null) {
            msg.send(player, type + ".invalid-platform", "{platforms}", String.join(", ", allowed));
            return true;
        }

        String platformName = platform.getString("name", key);

        // Link escrito o canal vinculado
        String link;
        if (args.length >= 2) {
            link = normalizeLink(args[1]);
        } else {
            String linked = plugin.getData().getChannel(player.getUniqueId(), key);
            if (linked == null) {
                msg.send(player, type + ".no-channel", "{platform}", platformName);
                return true;
            }
            link = normalizeLink(linked);
        }

        boolean validate = plugin.getConfig().getBoolean(type + ".validate-links", true);
        if (link == null || (validate && !matchesDomain(link, platform.getStringList("domains")))) {
            msg.send(player, type + ".invalid-link", "{platform}", platformName);
            return true;
        }

        // Cooldown (global o por plataforma)
        String cooldownKey = plugin.getConfig().getBoolean("cooldowns.per-platform", false)
                ? type + ":" + key
                : type;

        boolean bypass = player.hasPermission("zsmedias.cooldown.bypass");
        if (!bypass) {
            long remaining = plugin.getCooldowns().getRemaining(player.getUniqueId(), cooldownKey);
            if (remaining > 0) {
                msg.send(player, "general.cooldown", "{time}", Text.formatTime(remaining));
                return true;
            }
        }

        publish(player.getName(), player, key, platform, link, null);

        if (!bypass) {
            plugin.getCooldowns().set(player.getUniqueId(), cooldownKey, plugin.getConfig().getLong("cooldowns." + type, 0));
        }
        return true;
    }

    /**
     * /directo execute <plataforma> <nick> [link]
     * Anuncia el directo como si lo hubiera usado <nick>. No aplica ni cambia cooldowns.
     */
    private boolean execute(CommandSender sender, String[] args) {
        MessageManager msg = plugin.getMessages();

        if (!sender.hasPermission("zsmedias.directo.execute")) {
            msg.send(sender, "general.no-permission");
            return true;
        }

        List<String> allowed = plugin.getConfig().getStringList("directo.platforms");

        if (args.length < 3) {
            msg.send(sender, "directo.execute-usage", "{platforms}", String.join("|", allowed));
            return true;
        }

        String key = args[1].toLowerCase();
        ConfigurationSection platform = findPlatform(key, allowed);
        if (platform == null) {
            msg.send(sender, "directo.invalid-platform", "{platforms}", String.join(", ", allowed));
            return true;
        }

        Player online = Bukkit.getPlayerExact(args[2]);
        OfflinePlayer target = online != null ? online : Bukkit.getOfflinePlayer(args[2]);
        if (online == null && !target.hasPlayedBefore()) {
            msg.send(sender, "creadores.player-not-found");
            return true;
        }

        String targetName = target.getName() != null ? target.getName() : args[2];
        String platformName = platform.getString("name", key);

        // Link escrito o canal vinculado del jugador
        String link;
        if (args.length >= 4) {
            link = normalizeLink(args[3]);
        } else {
            String linked = plugin.getData().getChannel(target.getUniqueId(), key);
            if (linked == null) {
                msg.send(sender, "directo.execute-no-channel", "{player}", targetName, "{platform}", platformName);
                return true;
            }
            link = normalizeLink(linked);
        }

        boolean validate = plugin.getConfig().getBoolean("directo.validate-links", true);
        if (link == null || (validate && !matchesDomain(link, platform.getStringList("domains")))) {
            msg.send(sender, "directo.invalid-link", "{platform}", platformName);
            return true;
        }

        publish(targetName, online, key, platform, link, sender.getName());

        msg.send(sender, "directo.execute-done", "{player}", targetName, "{platform}", platformName);
        return true;
    }

    /**
     * Anuncia, abre la sesión de directo (si el jugador está conectado) y registra historial, log y Discord.
     * executor: quién lo ejecutó en nombre de otro (null si lo usó el propio jugador).
     */
    private void publish(String playerName, Player online, String key, ConfigurationSection platform,
                         String link, String executor) {
        MessageManager msg = plugin.getMessages();

        String platformName = platform.getString("name", key);
        String platformColor = platform.getString("color", "#FFFFFF");

        String[] pairs = {
                "{player}", playerName,
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

        plugin.getLive().showBossBar(Text.replace(msg.raw(type + ".bossbar"), pairs));

        // /directo abre una sesión: recordatorios y bonus de los fans del creador
        if (type.equals("directo") && online != null) {
            plugin.getLive().startSession(online, platformName, platformColor, link);
        }

        String suffix = executor == null ? "" : " [exec: " + executor + "]";
        plugin.getHistory().add(type, playerName, platformName + " " + link + suffix);
        plugin.getLogs().log(type, playerName, key + " " + link + suffix);
        plugin.getDiscord().send(type, pairs);
    }

    /** Sección de la plataforma si está habilitada para este comando, o null. */
    private ConfigurationSection findPlatform(String key, List<String> allowed) {
        for (String platformKey : allowed) {
            if (platformKey.equalsIgnoreCase(key)) {
                return plugin.getConfig().getConfigurationSection("platforms." + key.toLowerCase());
            }
        }
        return null;
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
        List<String> platforms = plugin.getConfig().getStringList(type + ".platforms");

        if (type.equals("directo") && args.length >= 1 && args[0].equalsIgnoreCase("execute")) {
            if (!sender.hasPermission("zsmedias.directo.execute")) {
                return Collections.emptyList();
            }
            if (args.length == 2) {
                return filter(platforms, args[1]);
            }
            if (args.length == 3) {
                List<String> names = new ArrayList<>();
                for (Player online : Bukkit.getOnlinePlayers()) {
                    names.add(online.getName());
                }
                return filter(names, args[2]);
            }
            if (args.length == 4) {
                return Collections.singletonList("https://");
            }
            return Collections.emptyList();
        }

        if (args.length == 1) {
            List<String> options = new ArrayList<>(platforms);
            if (type.equals("directo") && sender.hasPermission("zsmedias.directo.execute")) {
                options.add("execute");
            }
            return filter(options, args[0]);
        }
        if (args.length == 2) {
            return Collections.singletonList("https://");
        }
        return Collections.emptyList();
    }

    private List<String> filter(List<String> options, String typed) {
        List<String> result = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase().startsWith(typed.toLowerCase())) {
                result.add(option);
            }
        }
        return result;
    }
    }
