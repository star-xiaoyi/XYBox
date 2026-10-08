package com.fongmi.android.tv.search;

import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.bean.Flag;

/** One semantic match rule for preflight validation and playback/quality selection. */
public final class EpisodeSelection {
    private EpisodeSelection() { }
    public static Episode match(Flag flag, String name, boolean movie) {
        if (movie && flag.getEpisodes().size() == 1) return flag.getEpisodes().get(0);
        String key = EpisodeKey.key(name);
        for (Episode item : flag.getEpisodes()) if (!key.isEmpty() && key.equals(EpisodeKey.key(item.getName()))) return item;
        if (flag.getEpisodes().size() == 1 && EpisodeKey.movieLabel(name) && EpisodeKey.movieLabel(flag.getEpisodes().get(0).getName())) return flag.getEpisodes().get(0);
        return name.isEmpty() && !flag.getEpisodes().isEmpty() ? flag.getEpisodes().get(0) : null;
    }
}
