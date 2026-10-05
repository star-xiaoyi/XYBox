package com.fongmi.android.tv.search;

import com.fongmi.android.tv.bean.Flag;

import java.util.Locale;

/** Rules for provider lines that can answer like media but are not a complete playable video. */
public final class PlaybackRoutePolicy {

    private PlaybackRoutePolicy() {
    }

    public static boolean isRestricted(Flag flag) {
        return flag != null && isRestricted(flag.getFlag());
    }

    public static boolean isRestricted(QualityOption option) {
        return option != null && (isRestricted(option.flag) || isRestricted(option.valueName));
    }

    public static boolean isRestricted(String text) {
        if (text == null) return false;
        String value = text.trim().toLowerCase(Locale.ROOT)
                .replace('（', '(').replace('）', ')');
        if (value.isEmpty()) return false;
        for (String marker : new String[]{
                "试看", "试播", "扫码", "二维码", "限播", "限时观看", "仅限观看",
                "付费观看", "收费观看", "登录观看", "登陆观看", "关注公众号", "加群观看"
        }) if (value.contains(marker)) return true;
        // Several source packs explicitly suffix preview-only 4K/blue-ray routes with "-限".
        return value.matches(".*(?:[-_|·/\\s])限(?:$|[-_|·/\\s]).*")
                || value.endsWith("(限)");
    }
}
