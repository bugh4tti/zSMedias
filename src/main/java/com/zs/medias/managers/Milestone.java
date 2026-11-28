package com.zs.medias.managers;

import java.util.List;

/** Hito de votos de un creador: al llegar a 'votes', todos sus fans cobran 'rewards'. */
public record Milestone(
        int votes,
        List<String> rewards,
        List<String> message
) {
}
