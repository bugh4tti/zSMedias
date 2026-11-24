package com.zs.medias.managers;

import com.zs.medias.util.Text;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

public class AnnounceManager {

    private static final String CENTER_TAG = "[center]";

    private final JavaPlugin plugin;

    public AnnounceManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    private record Line(String plain, BaseComponent[] components) {
    }

    /**
     * Envía un anuncio a todos los jugadores (y a la consola).
     * Si la línea contiene {button} y hay link, se reemplaza por un botón clickeable.
     */
    public void broadcast(List<String> rawLines, String buttonText, List<String> hover, String link) {
        List<Line> lines = new ArrayList<>();
        String buttonColored = buttonText == null ? "" : Text.color(buttonText);

        for (String raw : rawLines) {
            if (link != null && buttonText != null && raw.contains("{button}")) {
                boolean centered = Text.isCentered(raw);
                String body = centered ? raw.substring(CENTER_TAG.length()) : raw;

                String full = Text.color(body.replace("{button}", buttonText));
                if (centered) {
                    full = Text.center(full);
                }

                int index = buttonColored.isEmpty() ? -1 : full.indexOf(buttonColored);
                if (index >= 0) {
                    String before = full.substring(0, index);
                    String after = full.substring(index + buttonColored.length());

                    List<BaseComponent> parts = new ArrayList<>();
                    for (BaseComponent component : TextComponent.fromLegacyText(before)) {
                        parts.add(component);
                    }

                    BaseComponent[] hoverComponents = null;
                    if (hover != null && !hover.isEmpty()) {
                        hoverComponents = TextComponent.fromLegacyText(Text.color(String.join("\n", hover)));
                    }

                    for (BaseComponent component : TextComponent.fromLegacyText(buttonColored)) {
                        component.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, link));
                        if (hoverComponents != null) {
                            component.setHoverEvent(new HoverEvent(
                                    HoverEvent.Action.SHOW_TEXT,
                                    new net.md_5.bungee.api.chat.hover.content.Text(hoverComponents)
                            ));
                        }
                        parts.add(component);
                    }

                    for (BaseComponent component : TextComponent.fromLegacyText(after)) {
                        parts.add(component);
                    }

                    lines.add(new Line(full, parts.toArray(new BaseComponent[0])));
                    continue;
                }

                lines.add(new Line(full, null));
                continue;
            }

            lines.add(new Line(Text.line(raw), null));
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            for (Line line : lines) {
                if (line.components() != null) {
                    player.spigot().sendMessage(line.components());
                } else {
                    player.sendMessage(line.plain());
                }
            }
        }

        for (Line line : lines) {
            Bukkit.getConsoleSender().sendMessage(line.plain());
        }
    }

    /** Sonido + title para todos los jugadores. */
    public void effects(String key, String title, String subtitle) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            playSound(player, key);
            sendTitle(player, key, title, subtitle);
        }
    }

    public void playSound(Player player, String key) {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("sounds." + key);
        if (section == null || !section.getBoolean("enabled", true)) {
            return;
        }

        String name = section.getString("sound", "");
        try {
            Sound sound = Sound.valueOf(name.toUpperCase());
            player.playSound(
                    player.getLocation(),
                    sound,
                    (float) section.getDouble("volume", 1.0),
                    (float) section.getDouble("pitch", 1.0)
            );
        } catch (IllegalArgumentException ex) {
            plugin.getLogger().warning("Sonido inválido en config.yml (sounds." + key + "): " + name);
        }
    }

    public void sendTitle(Player player, String key, String title, String subtitle) {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("titles." + key);
        if (section == null || !section.getBoolean("enabled", true)) {
            return;
        }

        player.sendTitle(
                Text.color(title == null ? "" : title),
                Text.color(subtitle == null ? "" : subtitle),
                section.getInt("fade-in", 10),
                section.getInt("stay", 60),
                section.getInt("fade-out", 10)
        );
    }
}
