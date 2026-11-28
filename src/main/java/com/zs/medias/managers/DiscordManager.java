package com.zs.medias.managers;

import com.zs.medias.util.Text;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

public class DiscordManager {

    private final JavaPlugin plugin;
    private final HttpClient client = HttpClient.newHttpClient();
    private boolean warnedInvalidUrl = false;

    public DiscordManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Envía un embed al webhook. type: directo, video o anuncio.
     * pairs: {player}, {platform}, {platform_color}, {link}, {message}...
     */
    public void send(String type, String... pairs) {
        FileConfiguration config = plugin.getConfig();
        if (!config.getBoolean("discord.enabled", false)) {
            return;
        }

        ConfigurationSection section = config.getConfigurationSection("discord." + type);
        if (section == null || !section.getBoolean("enabled", true)) {
            return;
        }

        String url = config.getString("discord.webhook-url", "").trim();
        if (!isValidWebhook(url)) {
            if (!warnedInvalidUrl) {
                warnedInvalidUrl = true;
                plugin.getLogger().warning("discord.webhook-url no es un webhook válido de Discord. No se envían mensajes.");
            }
            return;
        }

        String content = plain(section.getString("content", ""), pairs);
        String title = limit(plain(section.getString("title", ""), pairs), 256);
        String description = limit(plain(section.getString("description", ""), pairs), 4000);
        String link = findLink(pairs);

        StringBuilder json = new StringBuilder("{");
        json.append("\"username\":").append(quote(config.getString("discord.username", "zSMedias")));

        String avatar = config.getString("discord.avatar-url", "").trim();
        if (!avatar.isEmpty()) {
            json.append(",\"avatar_url\":").append(quote(avatar));
        }
        if (!content.isEmpty()) {
            json.append(",\"content\":").append(quote(limit(content, 1900)));
        }

        json.append(",\"embeds\":[{");
        json.append("\"title\":").append(quote(title));
        json.append(",\"description\":").append(quote(description));
        json.append(",\"color\":").append(parseColor(section.getString("color")));
        if (link != null) {
            json.append(",\"url\":").append(quote(link));
        }

        String footer = config.getString("discord.footer", "").trim();
        if (!footer.isEmpty()) {
            json.append(",\"footer\":{\"text\":").append(quote(limit(footer, 2000))).append("}");
        }
        json.append(",\"timestamp\":").append(quote(Instant.now().toString()));
        json.append("}]}");

        HttpRequest request;
        try {
            request = HttpRequest.newBuilder(URI.create(url))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(10))
                    .POST(HttpRequest.BodyPublishers.ofString(json.toString(), StandardCharsets.UTF_8))
                    .build();
        } catch (IllegalArgumentException ex) {
            plugin.getLogger().warning("Webhook de Discord inválido: " + ex.getMessage());
            return;
        }

        client.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                .thenAccept(response -> {
                    if (response.statusCode() >= 300) {
                        plugin.getLogger().warning("Discord respondió con el código " + response.statusCode() + ".");
                    }
                })
                .exceptionally(ex -> {
                    plugin.getLogger().warning("No se pudo enviar el webhook de Discord: " + ex.getMessage());
                    return null;
                });
    }

    private boolean isValidWebhook(String url) {
        return url.startsWith("https://discord.com/api/webhooks/")
                || url.startsWith("https://discordapp.com/api/webhooks/");
    }

    /** Reemplaza placeholders y quita los colores de Minecraft. */
    private String plain(String text, String... pairs) {
        return Text.strip(Text.replace(text, pairs)).trim();
    }

    private String findLink(String... pairs) {
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            if (pairs[i].equals("{link}")) {
                String value = pairs[i + 1];
                if (value != null && (value.startsWith("http://") || value.startsWith("https://"))) {
                    return value;
                }
            }
        }
        return null;
    }

    private int parseColor(String value) {
        if (value == null) {
            return 0x5865F2;
        }
        String hex = value.replace("#", "").replace("&", "").trim();
        try {
            return Integer.parseInt(hex, 16);
        } catch (NumberFormatException ex) {
            return 0x5865F2;
        }
    }

    private String limit(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max);
    }

    private String quote(String text) {
        StringBuilder sb = new StringBuilder("\"");
        for (char c : text.toCharArray()) {
            switch (c) {
                case '"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                    break;
            }
        }
        return sb.append('"').toString();
    }
}
