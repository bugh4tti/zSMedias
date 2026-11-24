package com.zs.medias.managers;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class DataManager {

    private final JavaPlugin plugin;
    private final File file;
    // jugador -> id del creador elegido
    private final Map<UUID, String> choices = new ConcurrentHashMap<>();
    // id del creador -> cantidad de votos
    private final Map<String, Integer> votes = new ConcurrentHashMap<>();

    public DataManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "data.yml");
    }

    public synchronized void load() {
        choices.clear();
        votes.clear();
        if (!file.exists()) {
            return;
        }

        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = yml.getConfigurationSection("choices");
        if (section == null) {
            return;
        }

        for (String key : section.getKeys(false)) {
            UUID uuid;
            try {
                uuid = UUID.fromString(key);
            } catch (IllegalArgumentException ex) {
                continue;
            }

            String id = section.getString(key);
            if (id == null || id.isEmpty()) {
                continue;
            }

            id = id.toLowerCase();
            choices.put(uuid, id);
            votes.merge(id, 1, Integer::sum);
        }
    }

    public boolean hasChoice(UUID uuid) {
        return choices.containsKey(uuid);
    }

    /** Id del creador elegido por el jugador, o null si no eligió. */
    public String getChoice(UUID uuid) {
        return choices.get(uuid);
    }

    public synchronized void setChoice(UUID uuid, String creatorId) {
        String id = creatorId.toLowerCase();
        String previous = choices.put(uuid, id);
        if (previous != null) {
            decrement(previous);
        }
        votes.merge(id, 1, Integer::sum);
        save();
    }

    /** Quita la elección del jugador. Devuelve el id anterior o null si no tenía. */
    public synchronized String removeChoice(UUID uuid) {
        String previous = choices.remove(uuid);
        if (previous != null) {
            decrement(previous);
            save();
        }
        return previous;
    }

    /** Libera todos los votos de un creador eliminado. Devuelve cuántos jugadores quedaron libres. */
    public synchronized int removeCreator(String creatorId) {
        String id = creatorId.toLowerCase();
        int removed = 0;

        Iterator<Map.Entry<UUID, String>> iterator = choices.entrySet().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().getValue().equals(id)) {
                iterator.remove();
                removed++;
            }
        }

        votes.remove(id);
        if (removed > 0) {
            save();
        }
        return removed;
    }

    public int getVotes(String creatorId) {
        return votes.getOrDefault(creatorId.toLowerCase(), 0);
    }

    public Map<String, Integer> getAllVotes() {
        return Collections.unmodifiableMap(new HashMap<>(votes));
    }

    private void decrement(String id) {
        votes.computeIfPresent(id, (key, value) -> value <= 1 ? null : value - 1);
    }

    public synchronized void save() {
        YamlConfiguration yml = new YamlConfiguration();
        for (Map.Entry<UUID, String> entry : choices.entrySet()) {
            yml.set("choices." + entry.getKey(), entry.getValue());
        }

        try {
            plugin.getDataFolder().mkdirs();
            yml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().severe("No se pudo guardar data.yml: " + ex.getMessage());
        }
    }
}
