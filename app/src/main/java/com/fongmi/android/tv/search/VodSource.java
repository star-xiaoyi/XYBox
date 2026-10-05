package com.fongmi.android.tv.search;

import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.bean.Vod;

import java.util.Comparator;

/**
 * 同一部片在某个站点上的一份资源。
 * <p>
 * 测速结果只在主线程写入：排序时字段若被后台线程改掉，TimSort 会直接抛
 * "Comparison method violates its general contract"。
 */
public class VodSource {

    public static final int IDLE = 0;
    public static final int TESTING = 1;
    public static final int OK = 2;
    public static final int SKIP = 3;
    public static final int TIMEOUT = 4;
    public static final int FAIL = 5;

    /**
     * 排序：实测能播的按速度排最前；没测或测不了的按搜索接口快慢排中间；
     * 测速失败的和播放失败过的垫底，但仍保留，别的源都不行时还能拿来兜底。
     */
    public static final Comparator<VodSource> RANK = (a, b) -> {
        int grade = Integer.compare(a.grade(), b.grade());
        if (grade != 0) return grade;
        if (a.state == OK && b.state == OK) return Long.compare(b.speed, a.speed);
        int health = Long.compare(a.playbackPenalty, b.playbackPenalty);
        if (health != 0) return health;
        int priority = Boolean.compare(b.priority, a.priority);
        return priority != 0 ? priority : Long.compare(a.cost, b.cost);
    };

    private final Vod vod;
    private final long cost;
    private final long playbackPenalty;
    private final boolean priority;
    private boolean broken;
    private long speed;
    private int state;

    public VodSource(Vod vod, long cost) {
        this.vod = vod;
        this.cost = cost;
        this.playbackPenalty = SiteHealth.playbackPenalty(getSite());
        this.priority = SiteHealth.isPriority(getSite());
    }

    public Vod getVod() {
        return vod;
    }

    public Site getSite() {
        Site site = vod.getSite();
        return site != null ? site : VodConfig.get().getSite(vod.getSiteKey());
    }

    public String getSiteKey() {
        return vod.getSiteKey();
    }

    public String getSiteName() {
        return getSite().getDisplayName();
    }

    public String getVodId() {
        return vod.getVodId();
    }

    public long getCost() {
        return cost;
    }

    public int getState() {
        return state;
    }

    public void setState(int state) {
        this.state = state;
    }

    public long getSpeed() {
        return speed;
    }

    public void setResult(int state, long speed) {
        this.state = state;
        this.speed = speed;
    }

    public boolean isBroken() {
        return broken;
    }

    public void setBroken(boolean broken) {
        this.broken = broken;
    }

    public boolean isFailed() {
        return state == TIMEOUT || state == FAIL;
    }

    public boolean same(String key, String id) {
        return getSiteKey().equals(key) && getVodId().equals(id);
    }

    private int grade() {
        if (broken) return 3;
        if (isFailed()) return 2;
        if (state == OK) return 0;
        return 1;
    }
}
