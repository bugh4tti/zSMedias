package com.zs.medias.util;

import org.bukkit.Bukkit;

import java.util.List;

public final class CommandUtil {

    private CommandUtil() {
    }

    /** Ejecuta comandos desde la consola, reemplazando placeholders y quitando el "/" inicial. */
    public static void run(List<String> commands, String... pairs) {
        if (commands == null) {
            return;
        }
        for (String command : commands) {
            String cmd = Text.replace(command, pairs).trim();
            if (cmd.startsWith("/")) {
                cmd = cmd.substring(1);
            }
            if (!cmd.isEmpty()) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
            }
        }
    }
}
