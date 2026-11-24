package com.zs.medias.managers;

import com.zs.medias.util.Text;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class MessageManager {

    private final JavaPlugin plugin;
    private FileConfiguration messages;

    public MessageManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        File file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        messages = YamlConfiguration.loadConfiguration(file);
    }

    private String prefix() {
        return messages.getString("prefix", "");
    }

    /** Texto sin colorear, con {prefix} ya reemplazado. */
    public String raw(String path) {
        String value = messages.getString(path);
        if (value == null) {
            return "&cMensaje no encontrado: " + path;
        }
        return value.replace("{prefix}", prefix());
    }

    /** Lista sin colorear, con {prefix} ya reemplazado. */
    public List<String> rawList(String path) {
        List<String> result = new ArrayList<>();
        for (String line : messages.getStringList(path)) {
            result.add(line.replace("{prefix}", prefix()));
        }
        return result;
    }

    /** Texto coloreado con reemplazos opcionales (clave, valor, clave, valor...). */
    public String get(String path, String... pairs) {
        return Text.color(Text.replace(raw(path), pairs));
    }

    public void send(CommandSender sender, String path, String... pairs) {
        sender.sendMessage(get(path, pairs));
    }

    /** Envía una lista de líneas (soporta [center]). */
    public void sendList(CommandSender sender, String path, String... pairs) {
        for (String line : rawList(path)) {
            sender.sendMessage(Text.line(Text.replace(line, pairs)));
        }
    }
}
