package com.zs.medias.commands;

import com.zs.medias.ZSMedias;
import com.zs.medias.managers.Creator;
import com.zs.medias.managers.MessageManager;
import com.zs.medias.util.ItemUtil;
import com.zs.medias.util.Ranking;
import com.zs.medias.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class CreadoresCommand implements CommandExecutor, TabCompleter {

    private final ZSMedias plugin;

    public CreadoresCommand(ZSMedias plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0) {
            switch (args[0].toLowerCase()) {
                case "top":
                    top(sender);
                    return true;
                case "claim":
                    claim(sender);
                    return true;
                case "add":
                    add(sender, args);
                    return true;
                case "remove":
                    remove(sender, args);
                    return true;
                case "reset":
                    reset(sender, args);
                    return true;
                default:
                    break;
            }
        }

        openMenu(sender);
        return true;
    }

    private void openMenu(CommandSender sender) {
        MessageManager msg = plugin.getMessages();

        if (!(sender instanceof Player player)) {
            msg.send(sender, "general.only-players");
            return;
        }
        if (!player.hasPermission("zsmedias.creadores")) {
            msg.send(player, "general.no-permission");
            return;
        }
        plugin.getCreadoresMenu().open(player);
    }

    private void claim(CommandSender sender) {
        MessageManager msg = plugin.getMessages();

        if (!(sender instanceof Player player)) {
            msg.send(sender, "general.only-players");
            return;
        }
        if (!player.hasPermission("zsmedias.creadores.claim")) {
            msg.send(player, "general.no-permission");
            return;
        }

        plugin.getRewards().claimDaily(player);
        plugin.getCreadoresMenu().refreshOpen();
    }

    private void top(CommandSender sender) {
        MessageManager msg = plugin.getMessages();

        if (!sender.hasPermission("zsmedias.creadores")) {
            msg.send(sender, "general.no-permission");
            return;
        }

        List<Creator> ranking = Ranking.top(plugin);
        if (ranking.isEmpty()) {
            msg.send(sender, "creadores.no-creators");
            return;
        }

        msg.sendList(sender, "creadores.top-header");

        int position = 1;
        for (Creator creator : ranking) {
            String line = Text.replace(
                    msg.raw("creadores.top-line"),
                    "{position}", String.valueOf(position),
                    "{creator}", creator.name(),
                    "{creator_color}", creator.color(),
                    "{votes}", String.valueOf(plugin.getData().getVotes(creator.id()))
            );
            sender.sendMessage(Text.line(line));
            position++;
        }

        msg.sendList(sender, "creadores.top-footer");
    }

    private void add(CommandSender sender, String[] args) {
        MessageManager msg = plugin.getMessages();

        if (!sender.hasPermission("zsmedias.admin")) {
            msg.send(sender, "general.no-permission");
            return;
        }
        if (!(sender instanceof Player player)) {
            msg.send(sender, "general.only-players");
            return;
        }
        if (args.length < 3) {
            msg.send(sender, "creadores.add-usage");
            return;
        }

        String id = args[1].toLowerCase();
        if (!id.matches("[a-z0-9_-]{1,32}")) {
            msg.send(sender, "creadores.add-usage");
            return;
        }

        String name = String.join(" ", Arrays.copyOfRange(args, 2, args.length));

        if (plugin.getCreators().exists(id)) {
            msg.send(sender, "creadores.add-exists");
            return;
        }

        String texture = ItemUtil.getTexture(player.getInventory().getItemInMainHand());
        if (texture == null) {
            msg.send(sender, "creadores.add-need-head");
            return;
        }

        plugin.getCreators().add(id, name, texture);
        msg.send(sender, "creadores.add-done", "{creator}", name);
        plugin.getCreadoresMenu().refreshOpen();
    }

    private void remove(CommandSender sender, String[] args) {
        MessageManager msg = plugin.getMessages();

        if (!sender.hasPermission("zsmedias.admin")) {
            msg.send(sender, "general.no-permission");
            return;
        }
        if (args.length < 2) {
            msg.send(sender, "creadores.remove-usage");
            return;
        }

        Creator creator = plugin.getCreators().get(args[1]);
        if (creator == null) {
            msg.send(sender, "creadores.remove-not-found");
            return;
        }

        // Libera los votos de los jugadores que lo habían elegido
        plugin.getData().removeCreator(creator.id());
        plugin.getCreators().remove(creator.id());

        msg.send(sender, "creadores.remove-done", "{creator}", creator.name());
        plugin.getLive().refreshAllBonuses();
        plugin.getCreadoresMenu().refreshOpen();
    }

    private void reset(CommandSender sender, String[] args) {
        MessageManager msg = plugin.getMessages();

        if (!sender.hasPermission("zsmedias.admin")) {
            msg.send(sender, "general.no-permission");
            return;
        }
        if (args.length < 2) {
            msg.send(sender, "creadores.reset-usage");
            return;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        if (!target.isOnline() && !target.hasPlayedBefore()) {
            msg.send(sender, "creadores.player-not-found");
            return;
        }

        String name = target.getName() != null ? target.getName() : args[1];

        String previous = plugin.getData().removeChoice(target.getUniqueId());
        if (previous == null) {
            msg.send(sender, "creadores.reset-none", "{player}", name);
            return;
        }

        msg.send(sender, "creadores.reset-done", "{player}", name);

        Player online = target.getPlayer();
        if (online != null) {
            plugin.getLive().refreshBonus(online);
        }
        plugin.getCreadoresMenu().refreshOpen();
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> options = new ArrayList<>();

        if (args.length == 1) {
            if (sender.hasPermission("zsmedias.creadores")) {
                options.add("top");
            }
            if (sender.hasPermission("zsmedias.creadores.claim")) {
                options.add("claim");
            }
            if (sender.hasPermission("zsmedias.admin")) {
                options.add("add");
                options.add("remove");
                options.add("reset");
            }
            return filter(options, args[0]);
        }

        if (args.length == 2 && sender.hasPermission("zsmedias.admin")) {
            String sub = args[0].toLowerCase();
            if (sub.equals("remove")) {
                for (Creator creator : plugin.getCreators().getAll()) {
                    options.add(creator.id());
                }
                return filter(options, args[1]);
            }
            if (sub.equals("reset")) {
                for (Player online : Bukkit.getOnlinePlayers()) {
                    options.add(online.getName());
                }
                return filter(options, args[1]);
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
