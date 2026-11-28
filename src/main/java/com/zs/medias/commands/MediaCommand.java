package com.zs.medias.commands;

import com.zs.medias.ZSMedias;
import com.zs.medias.managers.HistoryManager;
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
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * /media help | reload | config | toggle | historial [página] | vincular [plataforma] [canal|link|quitar]
 */
public class MediaCommand implements CommandExecutor, TabCompleter {

    private static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern("dd/MM HH:mm").withZone(ZoneId.systemDefault());

    private final ZSMedias plugin;

    public MediaCommand(ZSMedias plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "help" : args[0].toLowerCase();

        switch (sub) {
            case "reload":
                reload(sender);
                return true;
            case "vincular":
            case "link":
                vincular(sender, args);
                return true;
            case "historial":
            case "history":
                historial(sender, args);
                return true;
            case "toggle":
                toggle(sender);
                return true;
            case "config":
                openConfig(sender);
                return true;
            default:
                help(sender);
                return true;
        }
    }

    // =====================================================
    //  help / reload / config
    // =====================================================

    private void help(CommandSender sender) {
        for (String line : plugin.getMessages().rawList("help")) {
            sender.sendMessage(Text.line(line));
        }
    }

    private void reload(CommandSender sender) {
        MessageManager msg = plugin.getMessages();

        if (!sender.hasPermission("zsmedias.admin")) {
            msg.send(sender, "general.no-permission");
            return;
        }

        plugin.reloadAll();
        plugin.getMessages().send(sender, "general.reload");
    }

    private void openConfig(CommandSender sender) {
        MessageManager msg = plugin.getMessages();

        if (!(sender instanceof Player player)) {
            msg.send(sender, "general.only-players");
            return;
        }
        if (!player.hasPermission("zsmedias.admin")) {
            msg.send(player, "general.no-permission");
            return;
        }

        plugin.getConfigMenu().open(player);
    }

    // =====================================================
    //  toggle
    // =====================================================

    private void toggle(CommandSender sender) {
        MessageManager msg = plugin.getMessages();

        if (!(sender instanceof Player player)) {
            msg.send(sender, "general.only-players");
            return;
        }
        if (!player.hasPermission("zsmedias.toggle")) {
            msg.send(player, "general.no-permission");
            return;
        }

        boolean nowMuted = plugin.getData().toggleMuted(player.getUniqueId());
        msg.send(player, nowMuted ? "media.toggle-muted" : "media.toggle-unmuted");
    }

    // =====================================================
    //  historial
    // =====================================================

    private void historial(CommandSender sender, String[] args) {
        MessageManager msg = plugin.getMessages();

        if (!sender.hasPermission("zsmedias.historial")) {
            msg.send(sender, "general.no-permission");
            return;
        }

        HistoryManager history = plugin.getHistory();

        if (history.total() == 0) {
            msg.send(sender, "media.history-empty");
            return;
        }

        int size = Math.max(1, plugin.getConfig().getInt("history.page-size", 8));
        int pages = history.pages(size);

        int page = 1;
        if (args.length >= 2) {
            try {
                page = Integer.parseInt(args[1]);
            } catch (NumberFormatException ex) {
                page = 1;
            }
        }
        page = Math.max(1, Math.min(pages, page));

        msg.sendList(sender, "media.history-header",
                "{page}", String.valueOf(page),
                "{pages}", String.valueOf(pages),
                "{total}", String.valueOf(history.total()));

        for (HistoryManager.Entry entry : history.getPage(page, size)) {
            String line = Text.replace(
                    msg.raw("media.history-line"),
                    "{time}", TIME.format(Instant.ofEpochMilli(entry.time())),
                    "{type}", entry.type(),
                    "{player}", entry.player(),
                    "{detail}", entry.detail()
            );
            sender.sendMessage(Text.line(line));
        }

        msg.sendList(sender, "media.history-footer");
    }

    // =====================================================
    //  vincular
    // =====================================================

    private void vincular(CommandSender sender, String[] args) {
        MessageManager msg = plugin.getMessages();

        if (!(sender instanceof Player player)) {
            msg.send(sender, "general.only-players");
            return;
        }
        if (!player.hasPermission("zsmedias.vincular")) {
            msg.send(player, "general.no-permission");
            return;
        }

        UUID uuid = player.getUniqueId();

        // /media vincular -> lista los canales vinculados
        if (args.length == 1) {
            Map<String, String> channels = plugin.getData().getChannels(uuid);
            if (channels.isEmpty()) {
                msg.send(player, "media.vincular-list-empty");
                msg.send(player, "media.vincular-usage", "{platforms}", String.join(", ", platformKeys()));
                return;
            }

            msg.send(player, "media.vincular-list-header");
            for (Map.Entry<String, String> entry : channels.entrySet()) {
                msg.send(player, "media.vincular-list-line",
                        "{platform}", platformName(entry.getKey()),
                        "{link}", entry.getValue());
            }
            return;
        }

        String key = args[1].toLowerCase();
        ConfigurationSection platform = plugin.getConfig().getConfigurationSection("platforms." + key);
        if (platform == null) {
            msg.send(player, "media.vincular-invalid-platform", "{platforms}", String.join(", ", platformKeys()));
            return;
        }

        String platformName = platform.getString("name", key);

        if (args.length < 3) {
            msg.send(player, "media.vincular-usage", "{platforms}", String.join(", ", platformKeys()));
            return;
        }

        String input = args[2].trim();

        // /media vincular <plataforma> quitar
        if (input.equalsIgnoreCase("quitar") || input.equalsIgnoreCase("remove")) {
            if (plugin.getData().removeChannel(uuid, key)) {
                msg.send(player, "media.vincular-removed", "{platform}", platformName);
            } else {
                msg.send(player, "media.vincular-not-linked", "{platform}", platformName);
            }
            return;
        }

        String link = buildChannelLink(platform, input);
        if (link == null) {
            msg.send(player, "media.vincular-invalid", "{platform}", platformName);
            return;
        }

        plugin.getData().setChannel(uuid, key, link);
        plugin.getLogs().log("vincular", player.getName(), key + " " + link);
        msg.send(player, "media.vincular-done", "{platform}", platformName, "{link}", link);
    }

    /**
     * Acepta un link completo (se valida que sea de la plataforma) o solo el usuario del canal
     * (se arma con profile-url). Devuelve null si es inválido.
     */
    private String buildChannelLink(ConfigurationSection platform, String input) {
        String lower = input.toLowerCase();
        boolean isUrl = lower.startsWith("http://") || lower.startsWith("https://")
                || lower.startsWith("www.") || input.contains("/");

        if (isUrl) {
            String link = (lower.startsWith("http://") || lower.startsWith("https://")) ? input : "https://" + input;
            try {
                String host = new URI(link).getHost();
                if (host == null) {
                    return null;
                }
                host = host.toLowerCase();

                for (String domain : platform.getStringList("domains")) {
                    String d = domain.toLowerCase();
                    if (host.equals(d) || host.endsWith("." + d)) {
                        return link;
                    }
                }
            } catch (URISyntaxException ex) {
                return null;
            }
            return null;
        }

        String channel = input.startsWith("@") ? input.substring(1) : input;
        if (!channel.matches("[A-Za-z0-9_.-]{2,64}")) {
            return null;
        }

        String template = platform.getString("profile-url", "");
        if (template.isEmpty()) {
            return null;
        }
        return template.replace("{channel}", channel);
    }

    private List<String> platformKeys() {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("platforms");
        if (section == null) {
            return Collections.emptyList();
        }
        return new ArrayList<>(section.getKeys(false));
    }

    private String platformName(String key) {
        return plugin.getConfig().getString("platforms." + key + ".name", key);
    }

    // =====================================================
    //  Tab
    // =====================================================

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> options = new ArrayList<>();
            options.add("help");
            if (sender.hasPermission("zsmedias.vincular")) {
                options.add("vincular");
            }
            if (sender.hasPermission("zsmedias.toggle")) {
                options.add("toggle");
            }
            if (sender.hasPermission("zsmedias.historial")) {
                options.add("historial");
            }
            if (sender.hasPermission("zsmedias.admin")) {
                options.add("config");
                options.add("reload");
            }
            return filter(options, args[0]);
        }

        if (args[0].equalsIgnoreCase("vincular") && sender.hasPermission("zsmedias.vincular")) {
            if (args.length == 2) {
                return filter(platformKeys(), args[1]);
            }
            if (args.length == 3) {
                return filter(Arrays.asList("quitar"), args[2]);
            }
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
