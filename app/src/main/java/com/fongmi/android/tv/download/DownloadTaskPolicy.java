package com.fongmi.android.tv.download;

import com.fongmi.android.tv.bean.Download;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** One visible task per film edition and episode, even after the source/line changes. */
public final class DownloadTaskPolicy {
    private DownloadTaskPolicy() {}

    public static List<Download> matching(List<Download> all, Download requested) {
        List<Download> matches = OfflinePlayback.matching(all, requested.getSiteKey(), requested.getVodId(),
                requested.getVodName(), requested.getVodYear(), requested.getVodType());
        String episode = Download.episodeKey(requested.getEpisodeName());
        matches.removeIf(item -> !episode.equals(Download.episodeKey(item.getEpisodeName())));
        return matches;
    }

    public static Download preferred(List<Download> matches) {
        Download best = null;
        for (Download item : matches) {
            if (best == null || rank(item) > rank(best)
                    || rank(item) == rank(best) && item.getDoneBytes() > best.getDoneBytes()) best = item;
        }
        return best;
    }

    private static int rank(Download item) {
        return item.isPlayable() ? 5 : item.isRunning() ? 4 : item.isPending() ? 3 : item.isPaused() ? 2 : item.isError() ? 1 : 0;
    }

    public static List<Download> active(List<Download> all) {
        List<Download> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Download item : all) {
            Download best = preferred(matching(all, item));
            if (best != null && best.isActive() && seen.add(best.getId())) result.add(best);
        }
        result.sort((a, b) -> Long.compare(a.getCreateTime(), b.getCreateTime()));
        return result;
    }
}
