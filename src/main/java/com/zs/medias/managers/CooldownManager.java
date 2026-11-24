package com.zs.medias.managers;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CooldownManager {

    private final JavaPlugin plugin;
    private final File file;
    // uuid -> (clave del comando -> momento en que expira, en milisegundos)
    private final Map<UUID, Map<String, Long>> cooldowns = new ConcurrentHashMap<>();

    public CooldownManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "cooldowns.yml");
    }

    public synchronized void load() {
        cooldowns.clear();
        if (!file.exists()) {
            return;
        }

        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yml.getConfigurationSection("cooldowns");
        if (root == null) {
            return;
        }

        long now = System.currentTimeMillis();
        for (String uuidKey : root.getKeys(false)) {
            UUID uuid;
            try {
                uuid = UUID.fromString(uuidKey);
            } catch (IllegalArgumentException ex) {
                continue;
            }

            ConfigurationSection section = root.getConfigurationSection(uuidKey);
            if (section == null) {
                continue;
            }

            for (String key : section.getKeys(false)) {
                long expires = section.getLong(key);
                if (expires > now) {
                    cooldowns.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>()).put(key, expires);
                }
            }
        }
    }

    /** Segundos que le faltan al jugador para volver a usar ese comando (0 = libre). */
    public long getRemaining(UUID uuid, String key) {
        Map<String, Long> map = cooldowns.get(uuid);
        if (map == null) {
            return 0;
        }
        Long expires = map.get(key);
        if (expires == null) {
            return 0;
        }

        long left = expires - System.currentTimeMillis();
        if (left <= 0) {
            map.remove(key);
            return 0;
        }
        return (long) Math.ceil(left / 1000.0);
    }

    /** Inicia el cooldown (en segundos) y lo guarda. */
    public synchronized void set(UUID uuid, String key, long seconds) {
        if (seconds <= 0) {
            return;
        }
        long expires = System.currentTimeMillis() + (seconds * 1000L);
        cooldowns.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>()).put(key, expires);
        save();
    }

    public synchronized void reset(UUID uuid, String key) {
        Map<String, Long> map = cooldowns.get(uuid);
        if (map != null && map.remove(key) != null) {
            save();
        }
    }

    public synchronized void resetAll(UUID uuid) {
        if (cooldowns.remove(uuid) != null) {
            save();
        }
    }

    public synchronized void save() {
        YamlConfiguration yml = new YamlConfiguration();
        long now = System.currentTimeMillis();

        for (Map.Entry<UUID, Map<String, Long>> entry : cooldowns.entrySet()) {
            for (Map.Entry<String, Long> cd : entry.getValue().entrySet()) {
                if (cd.getValue() > now) {
                    yml.set("cooldowns." + entry.getKey() + "." + cd.getKey(), cd.getValue());
                }
            }
        }

        try {
            plugin.getDataFolder().mkdirs();
            yml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().severe("No se pudo guardar cooldowns.yml: " + ex.getMessage());
        }
    }
}
