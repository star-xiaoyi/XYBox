package com.fongmi.android.tv.search;

import com.fongmi.android.tv.bean.Config;
import com.github.catvod.utils.Util;

/** Stable across devices: database ids are local, configuration addresses are not. */
public final class SourceIdentity {
    private SourceIdentity() { }
    public static String prefix(Config config) { return "xy:" + Util.md5(config.getUrl()) + ":"; }
    public static String key(Config config, String site) { return site == null || site.isEmpty() ? "" : site.startsWith(prefix(config)) ? site : prefix(config) + site; }
    public static String original(String site) {
        return site != null && site.startsWith("xy:") && site.length() > 36 ? site.substring(36) : site;
    }
}
