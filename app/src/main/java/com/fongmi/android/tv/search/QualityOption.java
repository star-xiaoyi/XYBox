package com.fongmi.android.tv.search;

import com.fongmi.android.tv.bean.*;
import java.util.Locale;

public final class QualityOption {
    public final VodSource source;
    public final Vod detail;
    public final Flag flag;
    public final Episode episode;
    public final String valueName;
    public String verifiedUrl = "";
    public java.util.Map<String, String> verifiedHeaders = java.util.Collections.emptyMap();
    public long verifiedAt = System.currentTimeMillis();
    public final int valueIndex, width, height;
    public final boolean verified;
    public final long speed;
    public final int bitrate;
    public long latencyMs = -1, measuredAt;
    public QualityOption(VodSource source, Vod detail, Flag flag, Episode episode, String valueName, int valueIndex, int width, int height, boolean verified) {
        this(source, detail, flag, episode, valueName, valueIndex, width, height, verified, 0, 0);
    }
    public QualityOption(VodSource source, Vod detail, Flag flag, Episode episode, String valueName, int valueIndex,
                         int width, int height, boolean verified, long speed, int bitrate) {
        this.speed = speed; this.bitrate = bitrate;
        this.source = source; this.detail = detail; this.flag = flag; this.episode = episode;
        this.valueName = valueName; this.valueIndex = valueIndex; this.width = width; this.height = height; this.verified = verified;
    }
    public int rank() { return verified && width > 0 && height > 0 ? Math.min(width, height) : 0; }
    public boolean isAvailable() { return !PlaybackRoutePolicy.isRestricted(this) && !source.isBroken()
            && com.fongmi.android.tv.api.config.VodConfig.isSiteEnabled(source.getSite())
            && !com.fongmi.android.tv.api.config.VodConfig.get().getSite(source.getSiteKey()).isEmpty(); }
    public boolean canAuto() { return isAvailable() && !source.getSite().isCloudDrive() && !flag.isCloudDrive(); }
    public String identity() { return source.getSiteKey() + "\n" + source.getVodId() + "\n" + flag.getFlag() + "\n" + valueIndex + "\n" + width + "x" + height; }
    /** A playback node can offer multiple resolutions; source menus group by node, not resolution. */
    public String route() { return source.getSiteKey() + "\n" + source.getVodId() + "\n" + flag.getFlag() + "\n" + valueIndex; }
    public String label() {
        return tier(rank()) + " · " + width + "×" + height;
    }
    public int tierHeight() {
        return tierHeight(rank());
    }
    public static int tierHeight(int value) {
        return value <= 0 ? 0 : value <= 480 ? 480 : value <= 720 ? 720 : value <= 1080 ? 1080
                : value <= 1440 ? 1440 : value <= 2160 ? 2160 : 4320;
    }
    public static String tier(int height) {
        if (height <= 0) return "画质未知";
        if (height <= 480) return "流畅";
        if (height <= 720) return "高清";
        if (height <= 1080) return "超清";
        if (height <= 1440) return "2K";
        if (height <= 2160) return "4K";
        return "8K";
    }
    public static int taggedHeight(String text) {
        String value = text.toLowerCase(Locale.ROOT);
        java.util.regex.Matcher resolution = java.util.regex.Pattern.compile("(\\d{3,5})[x×](\\d{3,5})").matcher(value);
        if (resolution.find()) return Math.min(Integer.parseInt(resolution.group(1)), Integer.parseInt(resolution.group(2)));
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(4320|2160|1440|1080|720|480|360)p").matcher(value);
        if (matcher.find()) return Integer.parseInt(matcher.group(1));
        if (value.contains("8k")) return 4320;
        if (value.contains("4k")) return 2160;
        if (value.contains("2k")) return 1440;
        if (value.contains("超清") || value.contains("蓝光")) return 1080;
        if (value.contains("高清")) return 720;
        if (value.contains("标清")) return 480;
        return 0;
    }
}
