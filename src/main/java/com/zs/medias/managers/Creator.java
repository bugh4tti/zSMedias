package com.zs.medias.managers;

import java.util.List;
import java.util.Map;

/**
 * Un creador del menú /creadores.
 * message, title y subtitle son opcionales (message vacío / title null = se usa el del idioma).
 * player: nick de Minecraft del creador (vacío si no aplica).
 * tag: tag opcional (vacío = se usa creadores.default-tag).
 * daily: comandos de la recompensa diaria (vacío = sin recompensa diaria).
 */
public record Creator(
        String id,
        String name,
        String color,
        int slot,
        String texture,
        String displayName,
        List<String> lore,
        Map<String, String> links,
        List<String> rewards,
        List<String> message,
        String title,
        String subtitle,
        String player,
        String tag,
        List<String> daily,
        List<Milestone> milestones
) {
}
