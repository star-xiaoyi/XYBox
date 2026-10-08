package com.fongmi.android.tv.search;

import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.bean.Flag;
import com.fongmi.android.tv.bean.Vod;

/** Reject empty-address and wrong-episode catalogs before replacing the player. */
public final class SourceDetailPolicy {
    private SourceDetailPolicy() { }
    public static boolean playable(Vod vod, boolean allowCloud, String episode, boolean movie) {
        if (vod == null) return false;
        for (Flag flag : vod.getVodFlags()) {
            if (PlaybackRoutePolicy.isRestricted(flag) || !allowCloud && flag.isCloudDrive()) continue;
            if (episode != null && !episode.isEmpty()) {
                Episode matched = EpisodeSelection.match(flag, episode, movie);
                if (matched != null && !matched.getUrl().trim().isEmpty()) return true;
            } else for (Episode item : flag.getEpisodes()) if (!item.getUrl().trim().isEmpty()) return true;
        }
        return false;
    }
}
