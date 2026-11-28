package com.zs.medias.managers;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class HistoryManager {

    public record Entry(long time, String type, String player, String detail) {
    }

    private final JavaPlugin plugin;
    private final File file;
    // El más nuevo va primero
    private final List<Entry> entries = new ArrayList<>();

    public HistoryManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "history.yml");
    }

    public synchronized void load() {
        entries.clear();
        if (!file.exists()) {
            return;
        }

        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        for (Map<?, ?> map : yml.getMapList("entries")) {
            Object time = map.get("time");
            Object type = map.get("type");
            Object player = map.get("player");
            Object detail = map.get("detail");

            entries.add(new Entry(
                    time instanceof Number number ? number.longValue() : 0L,
                    type == null ? "?" : String.valueOf(type),
                    player == null ? "?" : String.valueOf(player),
                    detail == null ? "" : String.valueOf(detail)
            ));
        }
    }

    public synchronized void add(String type, String player, String detail) {
        entries.add(0, new Entry(System.currentTimeMillis(), type.toUpperCase(), player, detail == null ? "" : detail));

        int max = Math.max(1, plugin.getConfig().getInt("history.max-entries", 100));
        while (entries.size() > max) {
            entries.remove(entries.size() - 1);
        }
        save();
    }

    public synchronized int total() {
        return entries.size();
    }

    public synchronized int pages(int size) {
        return Math.max(1, (int) Math.ceil(entries.size() / (double) Math.max(1, size)));
    }

    /** Página (desde 1) de entradas, de la más nueva a la más vieja. */
    public synchronized List<Entry> getPage(int page, int size) {
        int from = Math.max(0, (page - 1) * size);
        if (from >= entries.size()) {
            return Collections.emptyList();
        }
        int to = Math.min(entries.size(), from + size);
        return new ArrayList<>(entries.subList(from, to));
    }

    private void save() {
        YamlConfiguration yml = new YamlConfiguration();

        List<Map<String, Object>> list = new ArrayList<>();
        for (Entry entry : entries) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("time", entry.time());
            map.put("type", entry.type());
            map.put("player", entry.player());
            map.put("detail", entry.detail());
            list.add(map);
        }
        yml.set("entries", list);

        try {
            plugin.getDataFolder().mkdirs();
            yml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().severe("No se pudo guardar history.yml: " + ex.getMessage());
        }
    }
}
