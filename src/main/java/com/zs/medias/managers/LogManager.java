package com.zs.medias.managers;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class LogManager {

    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final JavaPlugin plugin;
    private final File file;

    public LogManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "logs/usage.log");
    }

    /** Guarda una línea en logs/usage.log, ej: [2026-11-26 15:04:05] [DIRECTO] Bughatti -> twitch https://... */
    public synchronized void log(String type, String player, String detail) {
        if (!plugin.getConfig().getBoolean("logs.enabled", true)) {
            return;
        }

        String clean = detail == null ? "" : detail.replace('\n', ' ').replace('\r', ' ');
        String line = "[" + LocalDateTime.now().format(FORMAT) + "] ["
                + type.toUpperCase() + "] " + player + " -> " + clean + System.lineSeparator();

        try {
            File folder = file.getParentFile();
            if (folder != null) {
                folder.mkdirs();
            }
            Files.writeString(
                    file.toPath(),
                    line,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
            );
        } catch (IOException ex) {
            plugin.getLogger().warning("No se pudo escribir en usage.log: " + ex.getMessage());
        }
    }
}
