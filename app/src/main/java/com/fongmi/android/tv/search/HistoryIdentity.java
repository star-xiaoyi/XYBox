package com.fongmi.android.tv.search;

import com.fongmi.android.tv.bean.History;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.Map;
import java.util.IdentityHashMap;
import com.fongmi.android.tv.db.AppDatabase;
import com.github.catvod.utils.Util;

/** Film progress is independent of configuration ids and expiring episode URLs. */
public final class HistoryIdentity {
    private HistoryIdentity() { }
    public static boolean same(History a, History b) {
        return a.getAccountId().equals(b.getAccountId()) && FilmIdentity.same(a.getFilmId(), aliases(a), b.getFilmId(), aliases(b),
                sameTitle(a.getVodName(), b.getVodName()), metadata(a, b));
    }
    public static boolean sameTitle(String a, String b) {
        String title = TitleKey.normalize(a);
        return !title.isEmpty() && title.equals(TitleKey.normalize(b)) && edition(a).equals(edition(b));
    }
    private static boolean metadata(History a, History b) {
        // Episode runtimes vary within the same series. They never define a series identity.
        return FilmIdentity.compatibleMetadata(TitleKey.year(a.getVodYear()), TitleKey.year(b.getVodYear()), kind(a), kind(b),
                a.getEpisodeCount() > 1 || b.getEpisodeCount() > 1, a.getDuration(), b.getDuration());
    }
    public static int kind(History item) {
        int kind = TitleKey.kind(item.getVodType());
        return item.getEpisodeCount() > 1 && kind == TitleKey.KIND_MOVIE ? TitleKey.KIND_UNKNOWN : kind;
    }
    public static String edition(String title) {
        String text = title == null ? "" : com.github.catvod.utils.Trans.t2s(title);
        for (String tag : new String[]{"导演剪辑版", "加长版", "未删减版", "剪辑版"}) if (text.contains(tag)) return tag;
        return "";
    }
    public static String sourceToken(String key) {
        if (key == null || key.isEmpty()) return "";
        int suffix = key.lastIndexOf(AppDatabase.SYMBOL);
        String provider = suffix >= 0 && key.substring(suffix + AppDatabase.SYMBOL.length()).matches("[0-9]+") ? key.substring(0, suffix) : key;
        return "source:" + Util.md5(provider);
    }
    private static String aliases(History item) {
        String source = sourceToken(item.getKey());
        return item.getSourceKeys() + (source.isEmpty() ? "" : "\n" + source);
    }
    public static void remember(History item, String key) {
        Set<String> values = FilmIdentity.aliases(aliases(item));
        String source = sourceToken(key);
        if (!source.isEmpty()) values.add(source);
        item.setSourceKeys(FilmIdentity.join(values));
    }
    public static void bind(History target, List<History> matches) {
        Set<String> values = new LinkedHashSet<>(FilmIdentity.aliases(aliases(target)));
        String id = target.getFilmId();
        for (History item : matches) {
            values.addAll(FilmIdentity.aliases(aliases(item)));
            if (TitleKey.year(target.getVodYear()) == 0 && TitleKey.year(item.getVodYear()) > 0) target.setVodYear(item.getVodYear());
            if (TitleKey.kind(target.getVodType()) == TitleKey.KIND_UNKNOWN && TitleKey.kind(item.getVodType()) != TitleKey.KIND_UNKNOWN)
                target.setVodType(item.getVodType());
            if (!item.getFilmId().isEmpty()) {
                values.add("film:" + item.getFilmId());
                if (id.isEmpty() || item.getFilmId().compareTo(id) < 0) id = item.getFilmId();
            }
        }
        if (!target.getFilmId().isEmpty()) values.add("film:" + target.getFilmId());
        if (id.isEmpty()) id = "f-" + Util.md5(target.getKey() == null ? target.getVodName() : target.getKey());
        values.add("film:" + id);
        target.setFilmId(id); target.setSourceKeys(FilmIdentity.join(values));
    }
    private static boolean linked(History a, History b) {
        return FilmIdentity.same(a.getFilmId(), aliases(a), b.getFilmId(), aliases(b), sameTitle(a.getVodName(), b.getVodName()), false);
    }
    public static History prefer(History a, History b) {
        return prefer(a, b, unified(a), unified(b));
    }
    private static boolean unified(History item) { return item != null && !item.getFilmId().isEmpty() && item.isSharedProgress(); }
    private static History prefer(History a, History b, boolean leftUnified, boolean rightUnified) {
        if (a == null) return b;
        if (leftUnified != rightUnified) return leftUnified ? a : b;
        if (!leftUnified) {
            // One-time legacy recovery: source resets must not replace the already watched later episode.
            int left = episode(a), right = episode(b);
            if (left != right) return left > right ? a : b;
        }
        return a.getCreateTime() >= b.getCreateTime() ? a : b;
    }
    private static int episode(History item) {
        return item.getEpisodeNumber() > 0 ? item.getEpisodeNumber() : Math.max(0, EpisodeKey.number(item.getVodRemarks()));
    }
    public static List<History> matching(History target, List<History> values) {
        List<History> result = new ArrayList<>();
        for (History item : values) if (same(target, item)) result.add(item);
        // An unknown year/type must not bridge two explicitly different editions.
        for (History a : result) for (History b : result) if (!same(a, b) && !(linked(target, a) && linked(target, b))) {
            result.removeIf(item -> !linked(target, item));
            return result;
        }
        return result;
    }
    public static List<History> group(List<History> values) {
        List<History> result = new ArrayList<>();
        List<History> ordered = new ArrayList<>(values);
        Map<History, Boolean> persisted = new IdentityHashMap<>();
        for (History item : ordered) persisted.put(item, unified(item));
        // Establish known editions before considering incomplete legacy rows.
        // Otherwise an unknown-year row can first merge a remake and then bridge its original.
        ordered.sort((a, b) -> Integer.compare(confidence(b), confidence(a)));
        for (History item : ordered) {
            List<History> matches = matching(item, result);
            History chosen = item;
            for (History member : matches) chosen = prefer(chosen, member, persisted.get(chosen), persisted.get(member));
            List<History> identities = new ArrayList<>(matches); identities.add(item);
            // Grouping a read result does not fabricate a new watch time or change progress.
            bind(chosen, identities); result.removeAll(matches); result.add(chosen);
        }
        result.sort((a, b) -> Long.compare(b.getCreateTime(), a.getCreateTime()));
        return result;
    }
    private static int confidence(History item) {
        return (TitleKey.year(item.getVodYear()) > 0 ? 2 : 0) + (kind(item) != TitleKey.KIND_UNKNOWN ? 1 : 0);
    }
}
