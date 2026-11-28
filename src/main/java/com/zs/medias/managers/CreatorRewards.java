package com.zs.medias.managers;

import com.zs.medias.ZSMedias;
import com.zs.medias.util.CommandUtil;
import com.zs.medias.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

/** Recompensa diaria (/creadores claim) e hitos de votos de los creadores. */
public class CreatorRewards {

    private final ZSMedias plugin;

    public CreatorRewards(ZSMedias plugin) {
        this.plugin = plugin;
    }

    // =====================================================
    //  Recompensa diaria
    // =====================================================

    /** Segundos que faltan para poder reclamar de nuevo (0 = disponible). */
    public long dailyRemaining(Player player) {
        if (player.hasPermission("zsmedias.cooldown.bypass")) {
            return 0;
        }

        long last = plugin.getData().getLastClaim(player.getUniqueId());
        if (last <= 0) {
            return 0;
        }

        long cooldownMs = Math.max(0L, plugin.getConfig().getLong("creadores.daily-cooldown", 86400L)) * 1000L;
        long left = last + cooldownMs - System.currentTimeMillis();
        return left <= 0 ? 0 : (long) Math.ceil(left / 1000.0);
    }

    /** Intenta reclamar la recompensa diaria. Envía los mensajes y devuelve true si se entregó. */
    public boolean claimDaily(Player player) {
        MessageManager msg = plugin.getMessages();

        Creator creator = plugin.getCreators().get(plugin.getData().getChoice(player.getUniqueId()));
        if (creator == null) {
            msg.send(player, "creadores.claim-no-creator");
            return false;
        }

        String[] pairs = pairs(player, creator, plugin.getData().getVotes(creator.id()));

        if (creator.daily().isEmpty()) {
            msg.send(player, "creadores.claim-no-reward", pairs);
            return false;
        }

        long remaining = dailyRemaining(player);
        if (remaining > 0) {
            msg.send(player, "creadores.claim-cooldown", "{time}", Text.formatTime(remaining));
            return false;
        }

        plugin.getData().setLastClaim(player.getUniqueId(), System.currentTimeMillis());
        CommandUtil.run(creator.daily(), pairs);

        msg.send(player, "creadores.claim-success", pairs);
        plugin.getAnnouncer().playSound(player, "creadores-claim");
        return true;
    }

    // =====================================================
    //  Hitos de votos
    // =====================================================

    /** Revisa los hitos de todos los creadores (al iniciar y al recargar). */
    public void checkAll() {
        for (Creator creator : plugin.getCreators().getAll()) {
            checkMilestones(creator);
        }
    }

    /** Marca los hitos nuevos del creador, los anuncia y los entrega a sus fans conectados. */
    public void checkMilestones(Creator creator) {
        int votes = plugin.getData().getVotes(creator.id());

        for (Milestone milestone : creator.milestones()) {
            if (votes >= milestone.votes() && plugin.getData().markReached(creator.id(), milestone.votes())) {
                announceMilestone(creator, milestone);
            }
        }

        for (Player online : Bukkit.getOnlinePlayers()) {
            claimPendingMilestones(online);
        }
    }

    /** Entrega al jugador los hitos de su creador que ya se alcanzaron y todavía no cobró. */
    public void claimPendingMilestones(Player player) {
        UUID uuid = player.getUniqueId();

        Creator creator = plugin.getCreators().get(plugin.getData().getChoice(uuid));
        if (creator == null) {
            return;
        }

        for (Milestone milestone : creator.milestones()) {
            if (!plugin.getData().isReached(creator.id(), milestone.votes())) {
                continue;
            }
            if (plugin.getData().isMilestoneClaimed(uuid, creator.id(), milestone.votes())) {
                continue;
            }

            // Se marca antes de entregar para que nunca se pague dos veces
            plugin.getData().markMilestoneClaimed(uuid, creator.id(), milestone.votes());

            String[] pairs = pairs(player, creator, milestone.votes());
            CommandUtil.run(milestone.rewards(), pairs);

            List<String> lines = milestone.message().isEmpty()
                    ? plugin.getMessages().rawList("creadores.milestone-reward")
                    : milestone.message();
            for (String line : lines) {
                player.sendMessage(Text.line(Text.replace(line, pairs)));
            }

            plugin.getAnnouncer().playSound(player, "creadores-hito");
        }
    }

    private void announceMilestone(Creator creator, Milestone milestone) {
        String[] pairs = {
                "{creator}", creator.name(),
                "{creator_color}", creator.color(),
                "{votes}", String.valueOf(milestone.votes())
        };

        String line = Text.color(Text.replace(plugin.getMessages().raw("creadores.milestone-broadcast"), pairs));

        for (Player online : Bukkit.getOnlinePlayers()) {
            if (!plugin.getData().isMuted(online.getUniqueId())) {
                online.sendMessage(line);
            }
        }
        Bukkit.getConsoleSender().sendMessage(line);
    }

    private String[] pairs(Player player, Creator creator, int votes) {
        return new String[]{
                "{player}", player.getName(),
                "{creator}", creator.name(),
                "{creator_color}", creator.color(),
                "{votes}", String.valueOf(votes)
        };
    }
}
