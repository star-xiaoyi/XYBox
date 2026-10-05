package com.fongmi.android.tv.utils;

import android.content.SharedPreferences;
import com.fongmi.android.tv.App;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.search.SourceIdentity;
import com.fongmi.android.tv.bean.SyncSourcePreference;
import com.github.catvod.utils.Prefers;
import java.util.Map;
import java.util.TreeMap;

/** Account-scoped switches; older global switches are copied once when an existing account is first used. */
public final class SourcePreferences {
    private static final String SOURCE = "vod_disabled_";
    private static final String SITE = "vod_site_disabled_";
    private static final String PRIORITY = "vod_site_priority_";
    private static final String TIME = "_modified_at";

    private SourcePreferences() { }

    private static String preference(String key) {
        if (key.startsWith("priority:")) return PRIORITY + key.substring(9);
        return key.startsWith("source:") ? SOURCE + key.substring(7) : SITE + key.substring(5);
    }

    public static synchronized void initialize(String profile) {
        Prefers.getPrefers().edit().putBoolean(LocalProfile.keyFor(profile, "source_switches_scoped"), true).commit();
    }

    private static void migrate(String profile) {
        String marker = LocalProfile.keyFor(profile, "source_switches_scoped");
        SharedPreferences prefs = Prefers.getPrefers();
        if (prefs.getBoolean(marker, false)) return;
        SharedPreferences.Editor editor = prefs.edit();
        if (!LocalProfile.DEFAULT.equals(profile)) for (Map.Entry<String, ?> entry : prefs.getAll().entrySet()) {
            String key = entry.getKey();
            if ((key.startsWith(SOURCE) || key.startsWith(SITE) || key.startsWith(PRIORITY)) && entry.getValue() instanceof Boolean
                    && !prefs.contains(LocalProfile.keyFor(profile, key)))
                editor.putBoolean(LocalProfile.keyFor(profile, key), (Boolean) entry.getValue());
        }
        for (Map.Entry<String, ?> entry : App.get().getSharedPreferences("site-health", 0).getAll().entrySet()) {
            if (!entry.getKey().startsWith("priority:") || !(entry.getValue() instanceof Boolean)) continue;
            String key = LocalProfile.keyFor(profile, PRIORITY + entry.getKey().substring(9));
            if (!prefs.contains(key)) editor.putBoolean(key, (Boolean) entry.getValue());
        }
        editor.putBoolean(marker, true).commit();
    }

    public static synchronized boolean isEnabled(String key) {
        String profile = LocalProfile.id(); migrate(profile);
        return !Prefers.getPrefers().getBoolean(LocalProfile.keyFor(profile, preference(key)), false);
    }

    public static void setEnabled(String key, boolean enabled) {
        setValue(key, !enabled);
    }

    public static synchronized boolean isPriority(String site) {
        String profile = LocalProfile.id(); migrate(profile);
        return Prefers.getPrefers().getBoolean(LocalProfile.keyFor(profile, PRIORITY + site), false);
    }

    public static void setPriority(String site, boolean priority) { setValue("priority:" + site, priority); }

    private static void setValue(String key, boolean value) {
        String profile = LocalProfile.id();
        synchronized (SourcePreferences.class) {
            migrate(profile);
            String preference = LocalProfile.keyFor(profile, preference(key));
            SharedPreferences prefs = Prefers.getPrefers();
            if (prefs.getBoolean(preference, false) == value) return;
            long previous = time(prefs, preference);
            prefs.edit().putBoolean(preference, value)
                    .putLong(preference + TIME, Math.max(System.currentTimeMillis(), previous + 1)).commit();
        }
        WebDAVSyncManager.get().requestSync(profile);
    }

    private static long time(SharedPreferences prefs, String key) { return prefs.getLong(key + TIME, 1); }

    public static Map<String, SyncSourcePreference> capture(String profile) { return capture(profile, false); }

    public static synchronized Map<String, SyncSourcePreference> capture(String profile, boolean seed) {
        migrate(profile);
        Map<String, SyncSourcePreference> result = new TreeMap<>();
        SharedPreferences prefs = Prefers.getPrefers();
        String scope = LocalProfile.DEFAULT.equals(profile) ? "" : "profile_" + profile + "_";
        for (Map.Entry<String, ?> entry : prefs.getAll().entrySet()) {
            String stored = entry.getKey();
            if (!stored.startsWith(scope) || !(entry.getValue() instanceof Boolean)) continue;
            String key = stored.substring(scope.length()), identity;
            if (key.startsWith(SITE)) identity = "site:" + key.substring(SITE.length());
            else if (key.startsWith(SOURCE)) identity = "source:" + key.substring(SOURCE.length());
            else if (key.startsWith(PRIORITY)) identity = "priority:" + key.substring(PRIORITY.length());
            else continue;
            long updatedAt = prefs.contains(stored + TIME) ? time(prefs, stored) : (Boolean) entry.getValue() ? 1 : 0;
            if (SyncSourcePreference.validKey(identity) && updatedAt > 0)
                result.put(identity, new SyncSourcePreference(identity.startsWith("priority:")
                        ? (Boolean) entry.getValue() : !(Boolean) entry.getValue(), updatedAt));
        }
        // Publish defaults on the first upload too, so a stale second device cannot seed opposing values.
        if (seed && profile.equals(LocalProfile.id())) for (Config config : Config.getAll(0)) {
            seed(result, profile, "source:" + SourceIdentity.prefix(config));
            for (Site site : VodConfig.configuredSites(config)) {
                seed(result, profile, "site:" + site.getKey());
                seed(result, profile, "priority:" + site.getKey());
            }
        }
        return result;
    }

    private static void seed(Map<String, SyncSourcePreference> result, String profile, String identity) {
        if (!SyncSourcePreference.validKey(identity) || result.containsKey(identity)) return;
        String key = LocalProfile.keyFor(profile, preference(identity));
        boolean stored = Prefers.getPrefers().getBoolean(key, false);
        result.put(identity, new SyncSourcePreference(identity.startsWith("priority:") ? stored : !stored, 1));
    }

    public static synchronized boolean apply(String profile, Map<String, SyncSourcePreference> incoming) {
        Map<String, SyncSourcePreference> current = capture(profile);
        Map<String, SyncSourcePreference> merged = SyncSourcePreference.merge(incoming, current);
        SharedPreferences prefs = Prefers.getPrefers(); SharedPreferences.Editor editor = prefs.edit();
        boolean changed = false;
        for (Map.Entry<String, SyncSourcePreference> entry : merged.entrySet()) {
            String key = LocalProfile.keyFor(profile, preference(entry.getKey()));
            SyncSourcePreference value = entry.getValue();
            boolean stored = entry.getKey().startsWith("priority:") ? value.enabled : !value.enabled;
            changed |= prefs.getBoolean(key, false) != stored;
            editor.putBoolean(key, stored).putLong(key + TIME, value.updatedAt);
        }
        editor.commit();
        return changed;
    }
}
