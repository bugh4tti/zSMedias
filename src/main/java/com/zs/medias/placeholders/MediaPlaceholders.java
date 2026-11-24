package com.zs.medias.placeholders;

import com.zs.medias.ZSMedias;
import com.zs.medias.managers.Creator;
import com.zs.medias.util.Ranking;
import com.zs.medias.util.Text;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;

import java.util.List;
import java.util.Map;

/**
 * Placeholders:
 *  %zsmedias_creator%            nombre del creador elegido ("Ninguno" si no eligió)
 *  %zsmedias_creator_id%         id del creador elegido
 *  %zsmedias_creator_color%      color del creador elegido
 *  %zsmedias_has_creator%        true / false
 *  %zsmedias_votes_<id>%         votos de un creador (ej: %zsmedias_votes_domix%)
 *  %zsmedias_total_votes%        votos totales
 *  %zsmedias_top_<n>%            nombre del creador en el puesto n
 *  %zsmedias_top_<n>_votes%      votos del puesto n
 *  %zsmedias_top_<n>_color%      color del puesto n
 *  %zsmedias_cooldown_<tipo>%    cooldown restante (directo, video, anuncio) o "Listo"
 */
public class MediaPlaceholders extends PlaceholderExpansion {

    private final ZSMedias plugin;

    public MediaPlaceholders(ZSMedias plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "zsmedias";
    }

    @Override
    public String getAuthor() {
        return "Bughatti";
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        String param = params.toLowerCase();

        switch (param) {
            case "creator": {
                Creator creator = chosen(player);
                return creator == null ? "Ninguno" : creator.name();
            }
            case "creator_id": {
                Creator creator = chosen(player);
                return creator == null ? "" : creator.id();
            }
            case "creator_color": {
                Creator creator = chosen(player);
                return creator == null ? "" : Text.color(creator.color());
            }
            case "has_creator":
                return String.valueOf(player != null && plugin.getData().hasChoice(player.getUniqueId()));
            case "total_votes": {
                int total = 0;
                for (Map.Entry<String, Integer> entry : plugin.getData().getAllVotes().entrySet()) {
                    total += entry.getValue();
                }
                return String.valueOf(total);
            }
            default:
                break;
        }

        if (param.startsWith("votes_")) {
            return String.valueOf(plugin.getData().getVotes(param.substring("votes_".length())));
        }

        if (param.startsWith("top_")) {
            return top(param.substring("top_".length()));
        }

        if (param.startsWith("cooldown_")) {
            if (player == null) {
                return "";
            }
            long remaining = plugin.getCooldowns().getRemaining(player.getUniqueId(), param.substring("cooldown_".length()));
            return remaining > 0 ? Text.formatTime(remaining) : "Listo";
        }

        return null;
    }

    private Creator chosen(OfflinePlayer player) {
        if (player == null) {
            return null;
        }
        return plugin.getCreators().get(plugin.getData().getChoice(player.getUniqueId()));
    }

    /** params: "<n>", "<n>_votes" o "<n>_color" */
    private String top(String params) {
        String[] parts = params.split("_");

        int position;
        try {
            position = Integer.parseInt(parts[0]);
        } catch (NumberFormatException ex) {
            return null;
        }

        String what = parts.length > 1 ? parts[1] : "name";

        List<Creator> ranking = Ranking.top(plugin);
        if (position < 1 || position > ranking.size()) {
            return what.equals("votes") ? "0" : "-";
        }

        Creator creator = ranking.get(position - 1);
        switch (what) {
            case "votes":
                return String.valueOf(plugin.getData().getVotes(creator.id()));
            case "color":
                return Text.color(creator.color());
            case "name":
                return creator.name();
            default:
                return null;
        }
    }
}
