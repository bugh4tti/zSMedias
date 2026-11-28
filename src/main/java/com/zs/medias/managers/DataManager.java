package com.zs.medias.managers;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class DataManager {

    private final JavaPlugin plugin;
    private final File file;

    // jugador -> id del creador elegido
    private final Map<UUID, String> choices = new ConcurrentHashMap<>();
    // id del creador -> cantidad de votos
    private final Map<String, Integer> votes = new ConcurrentHashMap<>();
    // jugadores que silenciaron los anuncios (/media toggle)
    private final Set<UUID> muted = ConcurrentHashMap.newKeySet();
    // jugador -> (plataforma -> link del canal) (/media vincular)
    private final Map<UUID, Map<String, String>> channels = new ConcurrentHashMap<>();
    // jugador -> último /creadores claim (milisegundos)
    private final Map<UUID, Long> claims = new ConcurrentHashMap<>();
    // creador -> hitos de votos alcanzados
    private final Map<String, Set<Integer>> reached = new ConcurrentHashMap<>();
    // jugador -> hitos ya cobrados ("creador:votos")
    private final Map<UUID, Set<String>> claimedMilestones = new ConcurrentHashMap<>();

    public DataManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "data.yml");
    }

    // =====================================================
    //  Carga y guardado
    // =====================================================

    public synchronized void load() {
        choices.clear();
        votes.clear();
        muted.clear();
        channels.clear();
        claims.clear();
        reached.clear();
        claimedMilestones.clear();

        if (!file.exists()) {
            return;
        }

        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);

        ConfigurationSection choiceSection = yml.getConfigurationSection("choices");
        if (choiceSection != null) {
            for (String key : choiceSection.getKeys(false)) {
                UUID uuid = parseUuid(key);
                String id = choiceSection.getString(key);
                if (uuid == null || id == null || id.isEmpty()) {
                    continue;
                }
                id = id.toLowerCase();
                choices.put(uuid, id);
                votes.merge(id, 1, Integer::sum);
            }
        }

        for (String raw : yml.getStringList("muted")) {
            UUID uuid = parseUuid(raw);
            if (uuid != null) {
                muted.add(uuid);
            }
        }

        ConfigurationSection channelSection = yml.getConfigurationSection("channels");
        if (channelSection != null) {
            for (String key : channelSection.getKeys(false)) {
                UUID uuid = parseUuid(key);
                ConfigurationSection playerSection = channelSection.getConfigurationSection(key);
                if (uuid == null || playerSection == null) {
                    continue;
                }
                Map<String, String> map = new ConcurrentHashMap<>();
                for (String platform : playerSection.getKeys(false)) {
                    String link = playerSection.getString(platform);
                    if (link != null && !link.isEmpty()) {
                        map.put(platform.toLowerCase(), link);
                    }
                }
                if (!map.isEmpty()) {
                    channels.put(uuid, map);
                }
            }
        }

        ConfigurationSection claimSection = yml.getConfigurationSection("claims");
        if (claimSection != null) {
            for (String key : claimSection.getKeys(false)) {
                UUID uuid = parseUuid(key);
                if (uuid != null) {
                    claims.put(uuid, claimSection.getLong(key));
                }
            }
        }

        ConfigurationSection reachedSection = yml.getConfigurationSection("milestones.reached");
        if (reachedSection != null) {
            for (String creator : reachedSection.getKeys(false)) {
                Set<Integer> set = ConcurrentHashMap.newKeySet();
                set.addAll(reachedSection.getIntegerList(creator));
                if (!set.isEmpty()) {
                    reached.put(creator.toLowerCase(), set);
                }
            }
        }

        ConfigurationSection claimedSection = yml.getConfigurationSection("milestones.claimed");
        if (claimedSection != null) {
            for (String key : claimedSection.getKeys(false)) {
                UUID uuid = parseUuid(key);
                if (uuid == null) {
                    continue;
                }
                Set<String> set = ConcurrentHashMap.newKeySet();
                set.addAll(claimedSection.getStringList(key));
                if (!set.isEmpty()) {
                    claimedMilestones.put(uuid, set);
                }
            }
        }
    }

    public synchronized void save() {
        YamlConfiguration yml = new YamlConfiguration();

        for (Map.Entry<UUID, String> entry : choices.entrySet()) {
            yml.set("choices." + entry.getKey(), entry.getValue());
        }

        if (!muted.isEmpty()) {
            List<String> list = new ArrayList<>();
            for (UUID uuid : muted) {
                list.add(uuid.toString());
            }
            yml.set("muted", list);
        }

        for (Map.Entry<UUID, Map<String, String>> entry : channels.entrySet()) {
            for (Map.Entry<String, String> channel : entry.getValue().entrySet()) {
                yml.set("channels." + entry.getKey() + "." + channel.getKey(), channel.getValue());
            }
        }

        for (Map.Entry<UUID, Long> entry : claims.entrySet()) {
            yml.set("claims." + entry.getKey(), entry.getValue());
        }

        for (Map.Entry<String, Set<Integer>> entry : reached.entrySet()) {
            if (!entry.getValue().isEmpty()) {
                yml.set("milestones.reached." + entry.getKey(), new ArrayList<>(new TreeSet<>(entry.getValue())));
            }
        }

        for (Map.Entry<UUID, Set<String>> entry : claimedMilestones.entrySet()) {
            if (!entry.getValue().isEmpty()) {
                yml.set("milestones.claimed." + entry.getKey(), new ArrayList<>(entry.getValue()));
            }
        }

        try {
            plugin.getDataFolder().mkdirs();
            yml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().severe("No se pudo guardar data.yml: " + ex.getMessage());
        }
    }

    private UUID parseUuid(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    // =====================================================
    //  Creadores elegidos
    // =====================================================

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

    /** Libera los votos y los hitos de un creador eliminado. Devuelve cuántos jugadores quedaron libres. */
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
        reached.remove(id);
        for (Set<String> set : claimedMilestones.values()) {
            set.removeIf(key -> key.startsWith(id + ":"));
        }

        save();
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

    // =====================================================
    //  Silenciar anuncios (/media toggle)
    // =====================================================

    public boolean isMuted(UUID uuid) {
        return muted.contains(uuid);
    }

    /** Alterna el silencio. Devuelve true si quedó silenciado. */
    public synchronized boolean toggleMuted(UUID uuid) {
        boolean nowMuted;
        if (muted.contains(uuid)) {
            muted.remove(uuid);
            nowMuted = false;
        } else {
            muted.add(uuid);
            nowMuted = true;
        }
        save();
        return nowMuted;
    }

    // =====================================================
    //  Canales vinculados (/media vincular)
    // =====================================================

    /** Link guardado del jugador para esa plataforma, o null. */
    public String getChannel(UUID uuid, String platform) {
        Map<String, String> map = channels.get(uuid);
        return map == null ? null : map.get(platform.toLowerCase());
    }

    public Map<String, String> getChannels(UUID uuid) {
        Map<String, String> map = channels.get(uuid);
        return map == null ? Collections.emptyMap() : Collections.unmodifiableMap(new HashMap<>(map));
    }

    public synchronized void setChannel(UUID uuid, String platform, String link) {
        channels.computeIfAbsent(uuid, key -> new ConcurrentHashMap<>()).put(platform.toLowerCase(), link);
        save();
    }

    /** Desvincula un canal. Devuelve false si no había uno vinculado. */
    public synchronized boolean removeChannel(UUID uuid, String platform) {
        Map<String, String> map = channels.get(uuid);
        if (map == null || map.remove(platform.toLowerCase()) == null) {
            return false;
        }
        if (map.isEmpty()) {
            channels.remove(uuid);
        }
        save();
        return true;
    }

    // =====================================================
    //  Recompensa diaria (/creadores claim)
    // =====================================================

    /** Momento (ms) del último claim, o 0 si nunca reclamó. */
    public long getLastClaim(UUID uuid) {
        return claims.getOrDefault(uuid, 0L);
    }

    public synchronized void setLastClaim(UUID uuid, long millis) {
        claims.put(uuid, millis);
        save();
    }

    // =====================================================
    //  Hitos de votos
    // =====================================================

    public boolean isReached(String creatorId, int threshold) {
        Set<Integer> set = reached.get(creatorId.toLowerCase());
        return set != null && set.contains(threshold);
    }

    /** Marca un hito como alcanzado. Devuelve true si era nuevo. */
    public synchronized boolean markReached(String creatorId, int threshold) {
        boolean added = reached.computeIfAbsent(creatorId.toLowerCase(), key -> ConcurrentHashMap.newKeySet()).add(threshold);
        if (added) {
            save();
        }
        return added;
    }

    public boolean isMilestoneClaimed(UUID uuid, String creatorId, int threshold) {
        Set<String> set = claimedMilestones.get(uuid);
        return set != null && set.contains(creatorId.toLowerCase() + ":" + threshold);
    }

    public synchronized void markMilestoneClaimed(UUID uuid, String creatorId, int threshold) {
        claimedMilestones.computeIfAbsent(uuid, key -> ConcurrentHashMap.newKeySet())
                .add(creatorId.toLowerCase() + ":" + threshold);
        save();
    }
                             }
