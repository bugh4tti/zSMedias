package com.zs.medias.managers;

import com.zs.medias.ZSMedias;
import com.zs.medias.util.CommandUtil;
import com.zs.medias.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Maneja las sesiones de directo (recordatorios), la bossbar y el bonus de los fans.
 * Una sesión empieza con /directo y termina sola: al pasar live.duration o cuando el streamer se desconecta.
 */
public class LiveManager {

    private static final class Session {
        private final UUID uuid;
        private final String name;
        private final String platformName;
        private final String platformColor;
        private final String link;
        private final long startedAt;
        private final long endsAt;
        private long nextReminderAt;
        private int remindersSent;

        private Session(UUID uuid, String name, String platformName, String platformColor,
                        String link, long startedAt, long endsAt, long nextReminderAt) {
            this.uuid = uuid;
            this.name = name;
            this.platformName = platformName;
            this.platformColor = platformColor;
            this.link = link;
            this.startedAt = startedAt;
            this.endsAt = endsAt;
            this.nextReminderAt = nextReminderAt;
        }
    }

    private final ZSMedias plugin;
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();
    // fan -> nombre del creador cuyo bonus tiene activo
    private final Map<UUID, String> bonusActive = new ConcurrentHashMap<>();
    private final BukkitTask ticker;

    private BossBar bossBar;
    private BukkitTask bossTask;
    private int bossTotal;
    private int bossLeft;

    public LiveManager(ZSMedias plugin) {
        this.plugin = plugin;
        this.ticker = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 100L, 100L);
    }

    public void shutdown() {
        ticker.cancel();
        hideBossBar();
        sessions.clear();
        bonusActive.clear();
    }

    // =====================================================
    //  Sesiones de directo
    // =====================================================

    public void startSession(Player player, String platformName, String platformColor, String link) {
        FileConfiguration config = plugin.getConfig();
        long now = System.currentTimeMillis();
        long durationMs = Math.max(1L, config.getLong("live.duration", 120L)) * 60_000L;
        long intervalMs = Math.max(1L, config.getLong("live.reminders.interval", 15L)) * 60_000L;

        sessions.put(player.getUniqueId(), new Session(
                player.getUniqueId(),
                player.getName(),
                platformName,
                platformColor,
                link,
                now,
                now + durationMs,
                now + intervalMs
        ));

        refreshAllBonuses();
    }

    public int getLiveCount() {
        return sessions.size();
    }

    /** true si el jugador enlazado a ese creador (player: en creadores.yml) está en directo. */
    public boolean isLive(Creator creator) {
        String streamer = creator.player();
        if (streamer == null || streamer.isBlank()) {
            return false;
        }
        for (Session session : sessions.values()) {
            if (session.name.equalsIgnoreCase(streamer)) {
                return true;
            }
        }
        return false;
    }

    private void tick() {
        if (sessions.isEmpty()) {
            return;
        }

        FileConfiguration config = plugin.getConfig();
        long now = System.currentTimeMillis();
        boolean remindersOn = config.getBoolean("live.reminders.enabled", true);
        int maxReminders = config.getInt("live.reminders.max", 3);
        long intervalMs = Math.max(1L, config.getLong("live.reminders.interval", 15L)) * 60_000L;

        boolean ended = false;
        for (Session session : new ArrayList<>(sessions.values())) {
            Player player = Bukkit.getPlayer(session.uuid);
            if (player == null || now >= session.endsAt) {
                sessions.remove(session.uuid);
                ended = true;
                continue;
            }

            if (remindersOn && session.remindersSent < maxReminders && now >= session.nextReminderAt) {
                session.remindersSent++;
                session.nextReminderAt = now + intervalMs;
                sendReminder(session, now);
            }
        }

        if (ended) {
            refreshAllBonuses();
        }
    }

    private void sendReminder(Session session, long now) {
        MessageManager msg = plugin.getMessages();

        String[] pairs = {
                "{player}", session.name,
                "{platform}", session.platformName,
                "{platform_upper}", session.platformName.toUpperCase(),
                "{platform_color}", session.platformColor,
                "{link}", session.link,
                "{time}", Text.formatTime((now - session.startedAt) / 1000L)
        };

        List<String> lines = Text.replace(msg.rawList("directo.reminder.lines"), pairs);
        String button = Text.replace(msg.raw("directo.button.text"), pairs);
        List<String> hover = Text.replace(msg.rawList("directo.button.hover"), pairs);

        plugin.getAnnouncer().broadcast(lines, button, hover, session.link);
        plugin.getAnnouncer().soundAll("directo-reminder");
    }

    // =====================================================
    //  Bonus de fans
    // =====================================================

    /** Activa o desactiva el bonus del jugador según si su creador está en directo. */
    public void refreshBonus(Player player) {
        FileConfiguration config = plugin.getConfig();
        UUID uuid = player.getUniqueId();

        Creator creator = plugin.getCreators().get(plugin.getData().getChoice(uuid));
        boolean should = config.getBoolean("bonus.live.enabled", true) && creator != null && isLive(creator);
        boolean active = bonusActive.containsKey(uuid);

        if (should == active) {
            return;
        }

        String multiplier = String.valueOf(config.getDouble("bonus.live.multiplier", 1.5));

        if (should) {
            bonusActive.put(uuid, creator.name());
            String[] pairs = {
                    "{player}", player.getName(),
                    "{creator}", creator.name(),
                    "{creator_color}", creator.color(),
                    "{multiplier}", multiplier
            };
            CommandUtil.run(config.getStringList("bonus.live.on-start"), pairs);
            plugin.getMessages().send(player, "creadores.bonus-start", pairs);
        } else {
            String creatorName = bonusActive.remove(uuid);
            String[] pairs = {
                    "{player}", player.getName(),
                    "{creator}", creatorName == null ? "" : creatorName,
                    "{multiplier}", multiplier
            };
            CommandUtil.run(config.getStringList("bonus.live.on-end"), pairs);
            plugin.getMessages().send(player, "creadores.bonus-end", pairs);
        }
    }

    public void refreshAllBonuses() {
        refreshAllBonuses(null);
    }

    private void refreshAllBonuses(Player exclude) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (exclude != null && player.getUniqueId().equals(exclude.getUniqueId())) {
                continue;
            }
            refreshBonus(player);
        }
    }

    public void handleJoin(Player player) {
        refreshBonus(player);
        addToBossBar(player);
    }

    public void handleQuit(Player player) {
        UUID uuid = player.getUniqueId();

        // Si el que se va estaba en directo, termina su sesión y se actualiza a los demás fans
        if (sessions.remove(uuid) != null) {
            refreshAllBonuses(player);
        }

        // Si el que se va tenía un bonus activo, se ejecuta on-end sin mensaje
        String creatorName = bonusActive.remove(uuid);
        if (creatorName != null) {
            String[] pairs = {
                    "{player}", player.getName(),
                    "{creator}", creatorName,
                    "{multiplier}", String.valueOf(plugin.getConfig().getDouble("bonus.live.multiplier", 1.5))
            };
            CommandUtil.run(plugin.getConfig().getStringList("bonus.live.on-end"), pairs);
        }
    }

    // =====================================================
    //  Bossbar
    // =====================================================

    /** Muestra una bossbar temporal a todos (salvo a quienes silenciaron los anuncios). */
    public void showBossBar(String rawTitle) {
        FileConfiguration config = plugin.getConfig();
        if (!config.getBoolean("live.bossbar.enabled", true)) {
            return;
        }

        hideBossBar();

        int duration = Math.max(1, config.getInt("live.bossbar.duration", 60));

        BarColor color;
        try {
            color = BarColor.valueOf(config.getString("live.bossbar.color", "RED").toUpperCase());
        } catch (IllegalArgumentException ex) {
            color = BarColor.RED;
        }

        BarStyle style;
        try {
            style = BarStyle.valueOf(config.getString("live.bossbar.style", "SOLID").toUpperCase());
        } catch (IllegalArgumentException ex) {
            style = BarStyle.SOLID;
        }

        bossBar = Bukkit.createBossBar(Text.color(rawTitle), color, style);
        bossBar.setProgress(1.0);
        bossTotal = duration;
        bossLeft = duration;

        for (Player player : Bukkit.getOnlinePlayers()) {
            addToBossBar(player);
        }

        bossTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            bossLeft--;
            if (bossLeft <= 0 || bossBar == null) {
                hideBossBar();
                return;
            }
            bossBar.setProgress(Math.max(0.0, Math.min(1.0, bossLeft / (double) bossTotal)));
        }, 20L, 20L);
    }

    private void addToBossBar(Player player) {
        if (bossBar != null && !plugin.getData().isMuted(player.getUniqueId())) {
            bossBar.addPlayer(player);
        }
    }

    private void hideBossBar() {
        if (bossTask != null) {
            bossTask.cancel();
            bossTask = null;
        }
        if (bossBar != null) {
            bossBar.removeAll();
            bossBar = null;
        }
    }
}
