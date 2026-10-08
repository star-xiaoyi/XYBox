package com.fongmi.android.tv.search;

import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.bean.Flag;
import com.fongmi.android.tv.bean.Vod;
import java.util.*;

/** Count distinct numbered episodes, rather than duplicate entries or promotional clips. */
public final class EpisodeCoverage {
    private EpisodeCoverage() {}
    public static int count(Collection<String> names) {
        Set<String> episodes = new HashSet<>();
        for (String name : names) if (EpisodeKey.number(name) > 0) episodes.add(EpisodeKey.key(name));
        return episodes.size();
    }
    public static int count(Flag flag) {
        List<String> names = new ArrayList<>();
        for (Episode episode : flag.getEpisodes()) names.add(episode.getName());
        return count(names);
    }
    public static int countMatching(Collection<String> names, String name) {
        if (name == null || name.isEmpty()) return count(names);
        String key = EpisodeKey.key(name);
        for (String episode : names) if (key.equals(EpisodeKey.key(episode))) return count(names);
        return 0;
    }
    public static int count(Vod vod) {
        int count = 0;
        for (Flag flag : vod.getVodFlags()) if (!flag.isCloudDrive() && !PlaybackRoutePolicy.isRestricted(flag))
            count = Math.max(count, count(flag));
        return count;
    }
    public static int countMatching(Vod vod, String name) {
        int count = 0;
        for (Flag flag : vod.getVodFlags()) if (!flag.isCloudDrive() && !PlaybackRoutePolicy.isRestricted(flag)) {
            List<String> names = new ArrayList<>();
            for (Episode episode : flag.getEpisodes()) names.add(episode.getName());
            count = Math.max(count, countMatching(names, name));
        }
        return count;
    }
    public static boolean accepts(int knownCount, int candidateCount) {
        // Zero means "unknown" (date/title episode labels), not "empty". Matching the
        // requested episode is handled separately and must never be rejected by this hint.
        return knownCount <= 1 || candidateCount == 0 || candidateCount >= knownCount;
    }
    public static boolean acceptsCurrent(int knownCount, int candidateCount, boolean currentEpisodeMatched) {
        return currentEpisodeMatched || accepts(knownCount, candidateCount);
    }
}
