package com.zs.medias.commands;

import com.zs.medias.ZSMedias;
import com.zs.medias.util.Text;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MediaCommand implements CommandExecutor, TabCompleter {

    private final ZSMedias plugin;

    public MediaCommand(ZSMedias plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length >= 1 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("zsmedias.admin")) {
                plugin.getMessages().send(sender, "general.no-permission");
                return true;
            }
            plugin.reloadAll();
            plugin.getMessages().send(sender, "general.reload");
            return true;
        }

        for (String line : plugin.getMessages().rawList("help")) {
            sender.sendMessage(Text.line(line));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> options = new ArrayList<>();
            options.add("help");
            if (sender.hasPermission("zsmedias.admin")) {
                options.add("reload");
            }

            List<String> result = new ArrayList<>();
            for (String option : options) {
                if (option.startsWith(args[0].toLowerCase())) {
                    result.add(option);
                }
            }
            return result;
        }
        return Collections.emptyList();
    }
}
