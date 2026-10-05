package com.fongmi.android.tv.search;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.bean.Site;
import android.content.SharedPreferences;
import java.util.*;

/** Stable site keys, bounded local learning, and user-controlled search priority. */
public final class SiteHealth {
    private static SharedPreferences prefs() { return App.get().getSharedPreferences("site-health", 0); }
    private static String key(Site site) { return site.getKey(); }
    public static boolean isPriority(Site site) { return prefs().getBoolean("priority:" + key(site), false); }
    public static void setPriority(Site site, boolean value) { prefs().edit().putBoolean("priority:" + key(site), value).apply(); }
    public static synchronized void search(Site site, String keyword, long elapsed, boolean failed, boolean found) {
        SharedPreferences p = prefs(); String key = key(site);
        long average = p.getLong("search:" + key, elapsed);
        int failures = p.getInt("failure:" + key, 0);
        p.edit().putLong("search:" + key, (average * 3 + Math.min(30000, elapsed)) / 4)
                .putInt("failure:" + key, failed ? Math.min(5, failures + 1) : Math.max(0, failures - 1))
                .putLong("time:" + key, System.currentTimeMillis()).apply();
        if (!found) return;
        String query = "query:" + com.github.catvod.utils.Util.md5(TitleKey.normalize(keyword));
        Set<String> hits = new HashSet<>(p.getStringSet(query, Collections.emptySet())); hits.add(key);
        List<String> recent = new ArrayList<>(Arrays.asList(p.getString("recent", "").split("\\n")));
        recent.remove(""); recent.remove(query); recent.add(query);
        SharedPreferences.Editor editor = p.edit().putStringSet(query, hits);
        while (recent.size() > 32) editor.remove(recent.remove(0));
        editor.putString("recent", String.join("\n", recent)).apply();
    }
    public static synchronized void playback(Site site, boolean success, long startupMs) {
        String key = key(site); SharedPreferences p = prefs();
        int failures = p.getInt("play-failure:" + key, 0);
        p.edit().putInt("play-failure:" + key, success ? Math.max(0, failures - 1) : Math.min(5, failures + 1))
                .putLong("play-start:" + key, Math.max(0, startupMs)).putLong("play-time:" + key, System.currentTimeMillis()).apply();
    }
    public static long playbackPenalty(Site site) {
        SharedPreferences p = prefs(); String key = key(site);
        if (System.currentTimeMillis() - p.getLong("play-time:" + key, 0) > 24 * 3600_000L) return 5000;
        return p.getInt("play-failure:" + key, 0) * 10000L + Math.min(10000, p.getLong("play-start:" + key, 0));
    }
    public static List<Site> order(List<Site> sites, String keyword) {
        SharedPreferences p = prefs();
        Set<String> hits = p.getStringSet("query:" + com.github.catvod.utils.Util.md5(TitleKey.normalize(keyword)), Collections.emptySet());
        Map<Site, Long> scores = new HashMap<>();
        for (Site site : sites) {
            String key = key(site);
            boolean fresh = System.currentTimeMillis() - p.getLong("time:" + key, 0) < 7 * 24 * 3600_000L;
            long score = (isPriority(site) ? 0 : 1_000_000L) + (hits.contains(key) ? 0 : 100_000L)
                    + (fresh ? p.getLong("search:" + key, 3000) + p.getInt("failure:" + key, 0) * 10000L : 3000) + playbackPenalty(site);
            scores.put(site, score);
        }
        List<Site> ordered = new ArrayList<>(sites);
        ordered.sort(Comparator.comparingLong(scores::get));
        // Round-robin the whole ordered list, not just one seat per config.
        // Manual priority stays first inside each config, without starving the others.
        Map<Integer, Deque<Site>> groups = new LinkedHashMap<>();
        for (Site site : ordered) groups.computeIfAbsent(site.getSourceId(), id -> new ArrayDeque<>()).add(site);
        List<Site> result = new ArrayList<>();
        while (result.size() < ordered.size()) for (Deque<Site> group : groups.values()) if (!group.isEmpty()) result.add(group.removeFirst());
        return result;
    }
}
