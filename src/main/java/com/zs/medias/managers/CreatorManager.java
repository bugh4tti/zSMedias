package com.zs.medias.managers;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class CreatorManager {

    private final JavaPlugin plugin;
    private final File file;
    private FileConfiguration yml;
    private final Map<String, Creator> creators = new LinkedHashMap<>();

    public CreatorManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "creadores.yml");
    }

    public void load() {
        if (!file.exists()) {
            plugin.saveResource("creadores.yml", false);
        }

        yml = YamlConfiguration.loadConfiguration(file);
        creators.clear();

        ConfigurationSection root = yml.getConfigurationSection("creators");
        if (root == null) {
            return;
        }

        for (String key : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(key);
            if (section == null) {
                continue;
            }

            String id = key.toLowerCase();

            Map<String, String> links = new LinkedHashMap<>();
            ConfigurationSection linkSection = section.getConfigurationSection("links");
            if (linkSection != null) {
                for (String linkKey : linkSection.getKeys(false)) {
                    String value = linkSection.getString(linkKey, "");
                    if (value != null && !value.trim().isEmpty()) {
                        links.put(linkKey.toLowerCase(), value.trim());
                    }
                }
            }

            List<Milestone> milestones = new ArrayList<>();
            ConfigurationSection milestoneSection = section.getConfigurationSection("milestones");
            if (milestoneSection != null) {
                for (String votesKey : milestoneSection.getKeys(false)) {
                    int votes;
                    try {
                        votes = Integer.parseInt(votesKey.trim());
                    } catch (NumberFormatException ex) {
                        plugin.getLogger().warning("Hito inválido en creadores.yml (" + key + "): " + votesKey);
                        continue;
                    }
                    if (votes <= 0) {
                        continue;
                    }

                    ConfigurationSection hito = milestoneSection.getConfigurationSection(votesKey);
                    if (hito == null) {
                        continue;
                    }

                    milestones.add(new Milestone(
                            votes,
                            hito.getStringList("rewards"),
                            hito.getStringList("message")
                    ));
                }
                milestones.sort(Comparator.comparingInt(Milestone::votes));
            }

            creators.put(id, new Creator(
                    id,
                    section.getString("name", key),
                    section.getString("color", "#FFFFFF"),
                    section.getInt("slot", -1),
                    section.getString("texture", ""),
                    section.getString("display-name", "{creator_color}&l{creator}"),
                    section.getStringList("lore"),
                    links,
                    section.getStringList("rewards"),
                    section.getStringList("message"),
                    section.getString("title"),
                    section.getString("subtitle"),
                    section.getString("player", ""),
                    section.getString("tag", ""),
                    section.getStringList("daily"),
                    milestones
            ));
        }
    }

    public FileConfiguration getYml() {
        return yml;
    }

    public Creator get(String id) {
        return id == null ? null : creators.get(id.toLowerCase());
    }

    public boolean exists(String id) {
        return get(id) != null;
    }

    public Collection<Creator> getAll() {
        return Collections.unmodifiableCollection(creators.values());
    }

    /** Agrega un creador nuevo y lo guarda en creadores.yml. Devuelve false si el id ya existe. */
    public boolean add(String id, String name, String texture) {
        String lowerId = id.toLowerCase();
        if (creators.containsKey(lowerId)) {
            return false;
        }

        String path = "creators." + lowerId;
        yml.set(path + ".name", name);
        yml.set(path + ".color", "#FFFFFF");
        yml.set(path + ".slot", nextFreeSlot());
        yml.set(path + ".texture", texture == null ? "" : texture);
        yml.set(path + ".display-name", "{creator_color}&l{creator}");
        yml.set(path + ".lore", new ArrayList<>(Arrays.asList(
                "&8Creador de contenido",
                " ",
                "&f{votes} {votes_text} &7a {creator_color}{creator}",
                " ",
                "{status}",
                "&eClick derecho &7para ver sus canales"
        )));
        yml.set(path + ".player", "");
        yml.set(path + ".tag", "");
        yml.set(path + ".links.youtube", "");
        yml.set(path + ".links.twitch", "");
        yml.set(path + ".links.tiktok", "");
        yml.set(path + ".links.kick", "");
        yml.set(path + ".rewards", new ArrayList<String>());
        yml.set(path + ".daily", new ArrayList<String>());

        save();
        load();
        return true;
    }

    /** Elimina un creador de creadores.yml. Devuelve false si no existía. */
    public boolean remove(String id) {
        ConfigurationSection root = yml.getConfigurationSection("creators");
        if (root == null) {
            return false;
        }

        String found = null;
        for (String key : root.getKeys(false)) {
            if (key.equalsIgnoreCase(id)) {
                found = key;
                break;
            }
        }
        if (found == null) {
            return false;
        }

        yml.set("creators." + found, null);
        save();
        load();
        return true;
    }

    private int nextFreeSlot() {
        int rows = Math.max(1, Math.min(6, yml.getInt("menu.rows", 4)));
        int closeSlot = yml.getInt("menu.close-button.slot", -1);
        int claimSlot = yml.getInt("menu.claim-button.slot", -1);

        Set<Integer> used = new HashSet<>();
        for (Creator creator : creators.values()) {
            used.add(creator.slot());
        }

        for (int slot = 10; slot < rows * 9; slot++) {
            int column = slot % 9;
            if (column == 0 || column == 8 || slot == closeSlot || slot == claimSlot) {
                continue;
            }
            if (!used.contains(slot)) {
                return slot;
            }
        }
        return 10;
    }

    private void save() {
        try {
            plugin.getDataFolder().mkdirs();
            yml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().severe("No se pudo guardar creadores.yml: " + ex.getMessage());
        }
    }
}
