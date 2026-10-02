package com.fongmi.android.tv.download;

import android.util.AtomicFile;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.bean.Download;
import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.bean.Flag;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.search.TitleKey;
import com.fongmi.android.tv.utils.Util;
import com.github.catvod.utils.Logger;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Local media backs ordinary episodes; it is not a separate playback line. */
public final class OfflinePlayback {
    private OfflinePlayback() {}

    public static List<Download> matching(String site, String id, String name, String year, String type) {
        return matching(Download.getAll(), site, id, name, year, type);
    }

    public static List<Download> matching(List<Download> all, String site, String id, String name, String year, String type) {
        int expectedYear = TitleKey.year(year);
        int expectedKind = TitleKey.kind(type);
        // An exact source identity disambiguates an old history entry with no year/type metadata.
        for (Download item : all) {
            if (!item.getSiteKey().equals(site) || !item.getVodId().equals(id)) continue;
            if (expectedYear == 0) expectedYear = TitleKey.year(item.getVodYear());
            if (expectedKind == 0) expectedKind = TitleKey.kind(item.getVodType());
        }
        List<Download> matches = new ArrayList<>();
        String title = TitleKey.normalize(name);
        if (title.isEmpty()) return matches;
        for (Download item : all) {
            if (!title.equals(TitleKey.normalize(item.getVodName()))) continue;
            if (!TitleKey.sameYear(expectedYear, TitleKey.year(item.getVodYear()))) continue;
            if (!TitleKey.sameKind(expectedKind, TitleKey.kind(item.getVodType()))) continue;
            matches.add(item);
        }
        // With no identifying metadata, do not silently play a different remake of the same title.
        for (Download a : matches) for (Download b : matches) {
            if (!TitleKey.sameYear(TitleKey.year(a.getVodYear()), TitleKey.year(b.getVodYear()))
                    || !TitleKey.sameKind(TitleKey.kind(a.getVodType()), TitleKey.kind(b.getVodType()))) {
                matches.removeIf(item -> !item.getSiteKey().equals(site) || !item.getVodId().equals(id));
                Download.sort(matches);
                return matches;
            }
        }
        Download.sort(matches);
        return matches;
    }

    public static Download find(List<Download> items, String name, String site, String id, String flag, Set<String> rejected) {
        Download best = null;
        int rank = -1;
        String episode = Download.episodeKey(name);
        for (Download item : items) {
            if (!item.isPlayable() || rejected.contains(item.getLocalPath())
                    || !episode.equals(Download.episodeKey(item.getEpisodeName()))) continue;
            int candidate = item.getSiteKey().equals(site) && item.getVodId().equals(id) ? 2 : 0;
            if (candidate > 0 && item.getFlag().equals(flag)) candidate++;
            if (candidate > rank) { best = item; rank = candidate; }
        }
        return best;
    }

    /** Add locally available episodes missing from this source without replacing its full catalog. */
    public static void merge(Vod vod, List<Download> downloads) {
        if (downloads.stream().noneMatch(Download::isPlayable)) return;
        if (vod.getVodFlags().isEmpty()) vod.getVodFlags().add(Flag.create("剧集"));
        for (Flag flag : vod.getVodFlags()) {
            if (flag.isCloudDrive()) continue;
            Set<String> keys = new HashSet<>();
            for (Episode episode : flag.getEpisodes()) keys.add(Download.episodeKey(episode.getName()));
            boolean added = false;
            for (Download item : downloads) {
                if (!item.isPlayable() || !keys.add(Download.episodeKey(item.getEpisodeName()))) continue;
                flag.getEpisodes().add(Episode.create(item.getEpisodeName(), localUrl(item)));
                added = true;
            }
            if (added && flag.getEpisodes().stream().allMatch(e -> Util.getDigit(e.getName()) >= 0)) {
                flag.getEpisodes().sort((a, b) -> Integer.compare(Util.getDigit(a.getName()), Util.getDigit(b.getName())));
            }
        }
    }

    public static String localUrl(Download item) { return android.net.Uri.fromFile(new File(item.getLocalPath())).toString(); }

    /** Keep catalog metadata alongside app data, not in the disposable image/network cache. */
    public static synchronized void remember(String site, String id, Vod vod) {
        if (vod == null || vod.getVodFlags().isEmpty()) return;
        AtomicFile file = catalogFile(site, id);
        FileOutputStream stream = null;
        try {
            // Copy only stable metadata and original episode URLs; no activated UI state or site secrets.
            Vod copy = copyMetadata(vod);
            for (Flag flag : vod.getVodFlags()) {
                Flag saved = Flag.create(flag.getFlag());
                for (Episode episode : flag.getEpisodes()) {
                    if (isLocal(episode.getUrl())) continue;
                    saved.getEpisodes().add(Episode.create(episode.getName(), episode.getDesc(), episode.getUrl()));
                }
                if (!saved.getEpisodes().isEmpty()) copy.getVodFlags().add(saved);
            }
            if (copy.getVodFlags().isEmpty()) return;
            stream = file.startWrite();
            stream.write(App.gson().toJson(copy).getBytes(StandardCharsets.UTF_8));
            file.finishWrite(stream);
        } catch (Exception error) {
            if (stream != null) file.failWrite(stream);
            Logger.e("Offline catalog", error);
        }
    }

    public static synchronized Vod detail(String site, String id, String name, String year, String type) {
        List<Download> items = matching(site, id, name, year, type);
        Download head = null;
        for (Download item : items) if (item.isPlayable()) { head = item; break; }
        if (head == null) return null;
        Vod vod = null;
        AtomicFile file = catalogFile(site, id);
        try {
            if (file.getBaseFile().exists()) vod = App.gson().fromJson(new String(file.readFully(), StandardCharsets.UTF_8), Vod.class);
        } catch (Exception error) { Logger.e("Offline catalog", error); }
        if (vod != null && (!TitleKey.normalize(name).equals(TitleKey.normalize(vod.getVodName()))
                || !TitleKey.sameYear(TitleKey.year(head.getVodYear()), TitleKey.year(vod.getVodYear()))
                || !TitleKey.sameKind(TitleKey.kind(head.getVodType()), TitleKey.kind(vod.getTypeName())))) vod = null;
        if (vod == null) {
            vod = new Vod();
            vod.setVodId(id);
            vod.setVodName(head.getVodName());
            vod.setVodPic(head.getVodPic());
            vod.setVodContent(head.getVodContent());
            vod.setVodYear(head.getVodYear());
            vod.setVodArea(head.getVodArea());
            vod.setTypeName(head.getVodType());
        }
        merge(vod, items);
        return vod;
    }

    public static boolean isLocal(String url) { return url != null && url.startsWith("file:"); }

    private static AtomicFile catalogFile(String site, String id) {
        String key = com.github.catvod.utils.Util.md5(Download.buildVodKey(site, id));
        return new AtomicFile(new File(new File(App.get().getFilesDir(), "download_catalogs"), key + ".json"));
    }

    private static Vod copyMetadata(Vod vod) {
        Vod copy = new Vod();
        copy.setVodId(vod.getVodId()); copy.setVodName(vod.getVodName()); copy.setVodPic(vod.getVodPic());
        copy.setVodYear(vod.getVodYear()); copy.setVodArea(vod.getVodArea()); copy.setTypeName(vod.getTypeName());
        copy.setVodContent(vod.getVodContent()); copy.setVodRemarks(vod.getVodRemarks());
        return copy;
    }
}
