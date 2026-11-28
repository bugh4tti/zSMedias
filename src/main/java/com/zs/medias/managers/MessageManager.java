package com.zs.medias.managers;

import com.zs.medias.util.Text;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class MessageManager {

    private static final String DEFAULT_LANG = "es";

    private final JavaPlugin plugin;
    private FileConfiguration messages;

    public MessageManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /** Carga lang/messages_<idioma>.yml. Las claves que falten se toman del español incluido en el jar. */
    public void load() {
        String lang = plugin.getConfig().getString("language", DEFAULT_LANG).toLowerCase();
        String resource = "lang/messages_" + lang + ".yml";
        File file = new File(plugin.getDataFolder(), resource);

        if (!file.exists()) {
            if (plugin.getResource(resource) != null) {
                plugin.saveResource(resource, false);
            } else {
                plugin.getLogger().warning("No existe el idioma '" + lang + "'. Usando '" + DEFAULT_LANG + "'.");
                lang = DEFAULT_LANG;
                resource = "lang/messages_" + lang + ".yml";
                file = new File(plugin.getDataFolder(), resource);
                if (!file.exists()) {
                    plugin.saveResource(resource, false);
                }
            }
        }

        messages = YamlConfiguration.loadConfiguration(file);

        try (InputStream in = plugin.getResource("lang/messages_" + DEFAULT_LANG + ".yml")) {
            if (in != null) {
                messages.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8)));
            }
        } catch (IOException ex) {
            plugin.getLogger().warning("No se pudieron cargar los mensajes por defecto: " + ex.getMessage());
        }
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
