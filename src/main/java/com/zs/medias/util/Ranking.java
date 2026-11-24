package com.zs.medias.util;

import com.zs.medias.ZSMedias;
import com.zs.medias.managers.Creator;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class Ranking {

    private Ranking() {
    }

    /** Creadores ordenados por votos (de mayor a menor). Si empatan, respeta el orden de creadores.yml. */
    public static List<Creator> top(ZSMedias plugin) {
        List<Creator> list = new ArrayList<>(plugin.getCreators().getAll());
        list.sort(Comparator.comparingInt((Creator creator) -> plugin.getData().getVotes(creator.id())).reversed());
        return list;
    }
}
