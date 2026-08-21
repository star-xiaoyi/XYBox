package com.fongmi.android.tv.bean;

import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.db.AppDatabase;
import com.github.catvod.utils.Path;
import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 一条记录 = 一集离线缓存任务。
 * 同一部剧的多集通过 vodKey 聚合，vodKey 与 History 的 key 前半段同构（siteKey@@@vodId）。
 */
@Entity
public class Download {

    public static final int STATUS_PENDING = 0;
    public static final int STATUS_RUNNING = 1;
    public static final int STATUS_DONE = 2;
    public static final int STATUS_ERROR = 3;
    public static final int STATUS_PAUSED = 4;

    @PrimaryKey
    @NonNull
    @SerializedName("id")
    private String id = "";
    @SerializedName("vodKey")
    private String vodKey;
    @SerializedName("siteKey")
    private String siteKey;
    @SerializedName("vodId")
    private String vodId;
    @SerializedName("vodName")
    private String vodName;
    @SerializedName("vodPic")
    private String vodPic;
    /** 简介等元信息一起缓存下来，离线详情页才不是一片空白。 */
    @SerializedName("vodContent")
    private String vodContent;
    @SerializedName("vodYear")
    private String vodYear;
    @SerializedName("vodArea")
    private String vodArea;
    @SerializedName("vodType")
    private String vodType;
    @SerializedName("flag")
    private String flag;
    @SerializedName("episodeName")
    private String episodeName;
    @SerializedName("episodeUrl")
    private String episodeUrl;
    /** 落地后的可播放路径：HLS 为本地 index.m3u8，直链为单个视频文件。 */
    @SerializedName("localPath")
    private String localPath;
    @SerializedName("errorMsg")
    private String errorMsg;
    @SerializedName("status")
    private int status;
    @SerializedName("progress")
    private int progress;
    @SerializedName("speed")
    private long speed;
    @SerializedName("totalBytes")
    private long totalBytes;
    @SerializedName("doneBytes")
    private long doneBytes;
    @SerializedName("totalSeg")
    private int totalSeg;
    @SerializedName("doneSeg")
    private int doneSeg;
    @SerializedName("duration")
    private long duration;
    @SerializedName("createTime")
    private long createTime;
    @SerializedName("updateTime")
    private long updateTime;

    public Download() {
    }

    @Ignore
    public static Download create(String siteKey, String vodId, String vodName, String vodPic, String flag, Episode episode) {
        Download item = new Download();
        item.setVodKey(buildVodKey(siteKey, vodId));
        item.setId(buildId(siteKey, vodId, flag, episode.getName()));
        item.setSiteKey(siteKey);
        item.setVodId(vodId);
        item.setVodName(vodName);
        item.setVodPic(vodPic);
        item.setFlag(flag);
        item.setEpisodeName(episode.getName());
        item.setEpisodeUrl(episode.getUrl());
        item.setStatus(STATUS_PENDING);
        item.setCreateTime(System.currentTimeMillis());
        item.setUpdateTime(System.currentTimeMillis());
        return item;
    }

    public static String buildVodKey(String siteKey, String vodId) {
        return siteKey + AppDatabase.SYMBOL + vodId;
    }

    /**
     * 展示层的聚合键就是片名：和观看记录一样跨源合并，
     * 这个源下几集、那个源下几集，用户看到的是同一部剧。
     */
    public static String buildGroupKey(String vodName) {
        return vodName == null ? "" : vodName;
    }

    public String groupKey() {
        return getVodName();
    }

    /** 跨源比对用的集标识：能认出集号就用集号，认不出退回集名。 */
    public static String episodeKey(String episodeName) {
        int digit = com.fongmi.android.tv.utils.Util.getDigit(episodeName);
        return digit < 0 ? episodeName : String.valueOf(digit);
    }

    public static String buildId(String siteKey, String vodId, String flag, String episodeName) {
        return buildVodKey(siteKey, vodId) + AppDatabase.SYMBOL + flag + AppDatabase.SYMBOL + episodeName;
    }

    public static Download objectFrom(String str) {
        try {
            Download item = new Gson().fromJson(str, Download.class);
            return item == null ? new Download() : item;
        } catch (Exception e) {
            return new Download();
        }
    }

    @NonNull
    public String getId() {
        return TextUtils.isEmpty(id) ? "" : id;
    }

    public void setId(@NonNull String id) {
        this.id = id;
    }

    public String getVodKey() {
        return vodKey == null ? "" : vodKey;
    }

    public void setVodKey(String vodKey) {
        this.vodKey = vodKey;
    }

    public String getSiteKey() {
        return siteKey == null ? "" : siteKey;
    }

    public void setSiteKey(String siteKey) {
        this.siteKey = siteKey;
    }

    public String getVodId() {
        return vodId == null ? "" : vodId;
    }

    public void setVodId(String vodId) {
        this.vodId = vodId;
    }

    public String getVodName() {
        return vodName == null ? "" : vodName;
    }

    public void setVodName(String vodName) {
        this.vodName = vodName;
    }

    public String getVodPic() {
        return vodPic == null ? "" : vodPic;
    }

    public void setVodPic(String vodPic) {
        this.vodPic = vodPic;
    }

    public String getVodContent() {
        return vodContent == null ? "" : vodContent;
    }

    public void setVodContent(String vodContent) {
        this.vodContent = vodContent;
    }

    public String getVodYear() {
        return vodYear == null ? "" : vodYear;
    }

    public void setVodYear(String vodYear) {
        this.vodYear = vodYear;
    }

    public String getVodArea() {
        return vodArea == null ? "" : vodArea;
    }

    public void setVodArea(String vodArea) {
        this.vodArea = vodArea;
    }

    public String getVodType() {
        return vodType == null ? "" : vodType;
    }

    public void setVodType(String vodType) {
        this.vodType = vodType;
    }

    public String getFlag() {
        return flag == null ? "" : flag;
    }

    public void setFlag(String flag) {
        this.flag = flag;
    }

    public String getEpisodeName() {
        return episodeName == null ? "" : episodeName;
    }

    public void setEpisodeName(String episodeName) {
        this.episodeName = episodeName;
    }

    public String getEpisodeUrl() {
        return episodeUrl == null ? "" : episodeUrl;
    }

    public void setEpisodeUrl(String episodeUrl) {
        this.episodeUrl = episodeUrl;
    }

    public String getLocalPath() {
        return localPath == null ? "" : localPath;
    }

    public void setLocalPath(String localPath) {
        this.localPath = localPath;
    }

    public String getErrorMsg() {
        return errorMsg == null ? "" : errorMsg;
    }

    public void setErrorMsg(String errorMsg) {
        this.errorMsg = errorMsg;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public int getProgress() {
        return progress;
    }

    public void setProgress(int progress) {
        this.progress = Math.max(0, Math.min(100, progress));
    }

    public long getSpeed() {
        return speed;
    }

    public void setSpeed(long speed) {
        this.speed = speed;
    }

    public long getTotalBytes() {
        return totalBytes;
    }

    public void setTotalBytes(long totalBytes) {
        this.totalBytes = totalBytes;
    }

    public long getDoneBytes() {
        return doneBytes;
    }

    public void setDoneBytes(long doneBytes) {
        this.doneBytes = doneBytes;
    }

    public int getTotalSeg() {
        return totalSeg;
    }

    public void setTotalSeg(int totalSeg) {
        this.totalSeg = totalSeg;
    }

    public int getDoneSeg() {
        return doneSeg;
    }

    public void setDoneSeg(int doneSeg) {
        this.doneSeg = doneSeg;
    }

    public long getDuration() {
        return duration;
    }

    public void setDuration(long duration) {
        this.duration = duration;
    }

    public long getCreateTime() {
        return createTime;
    }

    public void setCreateTime(long createTime) {
        this.createTime = createTime;
    }

    public long getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(long updateTime) {
        this.updateTime = updateTime;
    }

    public boolean isDone() {
        return status == STATUS_DONE;
    }

    public boolean isError() {
        return status == STATUS_ERROR;
    }

    public boolean isPaused() {
        return status == STATUS_PAUSED;
    }

    public boolean isRunning() {
        return status == STATUS_RUNNING;
    }

    public boolean isPending() {
        return status == STATUS_PENDING;
    }

    /** 未完成（含排队、下载中、暂停、失败），即「下载中」任务视图要展示的集合。 */
    public boolean isActive() {
        return status != STATUS_DONE;
    }

    /** 文件真实存在才算能离线播放，避免用户手动清了目录后点了播不出来。 */
    public boolean isPlayable() {
        return isDone() && !getLocalPath().isEmpty() && new File(getLocalPath()).exists();
    }

    /** 每集一个独立目录，HLS 分片和直链文件都落在里面，删除时整个目录清掉。 */
    public File dir() {
        return new File(Path.download(), safe(getVodKey()) + File.separator + safe(getFlag() + "_" + getEpisodeName()));
    }

    /**
     * 文件名清洗。除了文件系统禁用字符，# 和 $ 也要去掉：
     * 本地路径会被拼进 flag 的剧集串，它俩在那里是分隔符。
     */
    private static String safe(String name) {
        if (TextUtils.isEmpty(name)) return "unnamed";
        String clean = name.replaceAll("[\\\\/:*?\"<>|#$&%\\s]+", "_");
        return clean.length() > 80 ? clean.substring(0, 80) + clean.hashCode() : clean;
    }

    public Download save() {
        setUpdateTime(System.currentTimeMillis());
        AppDatabase.get().getDownloadDao().insertOrUpdate(this);
        return this;
    }

    public void delete() {
        AppDatabase.get().getDownloadDao().delete(this);
        clearFiles();
    }

    /** 一集可能有上百个分片，删文件放后台，别卡住点删除的那一下。 */
    public void clearFiles() {
        File dir = dir();
        App.execute(() -> {
            Path.clear(dir);
            File parent = dir.getParentFile();
            if (parent == null || !parent.exists()) return;
            String[] children = parent.list();
            if (children == null || children.length == 0) parent.delete();
        });
    }

    public static List<Download> getAll() {
        return AppDatabase.get().getDownloadDao().getAll();
    }

    public static List<Download> getActive() {
        return AppDatabase.get().getDownloadDao().getActive();
    }

    public static List<Download> getByVod(String vodKey) {
        return AppDatabase.get().getDownloadDao().getByVod(vodKey);
    }

    /** 取一部剧（可能横跨多个站源、多个版本）的全部缓存记录。 */
    public static List<Download> getByGroup(String groupKey) {
        List<Download> items = AppDatabase.get().getDownloadDao().getByName(groupKey);
        sort(items);
        return items;
    }

    /** 按集号排序，没有数字的排在后面按名字排——不能用下载先后，否则第8集会排在第1集前面。 */
    public static void sort(List<Download> items) {
        Collections.sort(items, (a, b) -> {
            int x = com.fongmi.android.tv.utils.Util.getDigit(a.getEpisodeName());
            int y = com.fongmi.android.tv.utils.Util.getDigit(b.getEpisodeName());
            if (x != y) return Integer.compare(x < 0 ? Integer.MAX_VALUE : x, y < 0 ? Integer.MAX_VALUE : y);
            return a.getEpisodeName().compareTo(b.getEpisodeName());
        });
    }

    public static Download find(String id) {
        return AppDatabase.get().getDownloadDao().find(id);
    }

    public static void deleteByGroup(String groupKey) {
        for (Download item : getByGroup(groupKey)) item.delete();
    }

    public static void clear() {
        AppDatabase.get().getDownloadDao().clear();
        App.execute(() -> Path.clear(Path.download()));
    }

    /**
     * 按剧聚合，保留数据库给出的时间倒序：每部剧取最近一条记录的时间作为排序依据。
     */
    public static List<Group> group(List<Download> items) {
        Map<String, Group> map = new LinkedHashMap<>();
        for (Download item : items) {
            Group group = map.get(item.groupKey());
            if (group == null) map.put(item.groupKey(), group = new Group(item));
            group.add(item);
        }
        for (Group group : map.values()) sort(group.getItems());
        return new ArrayList<>(map.values());
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Download)) return false;
        return getId().equals(((Download) obj).getId());
    }

    @Override
    public int hashCode() {
        return getId().hashCode();
    }

    @NonNull
    @Override
    public String toString() {
        return new Gson().toJson(this);
    }

    /** 一部剧的聚合视图，供首页卡片和缓存列表页使用。 */
    public static class Group {

        private final List<Download> items = new ArrayList<>();
        private final Download head;

        public Group(Download head) {
            this.head = head;
        }

        public void add(Download item) {
            items.add(item);
        }

        public List<Download> getItems() {
            return items;
        }

        public String getKey() {
            return head.groupKey();
        }

        /** 同一集可能来自两个源，算集数时按集去重，否则会数成两集。 */
        private int distinct(boolean doneOnly) {
            List<String> keys = new ArrayList<>();
            for (Download item : items) {
                if (doneOnly != item.isDone()) continue;
                String key = episodeKey(item.getEpisodeName());
                if (!keys.contains(key)) keys.add(key);
            }
            return keys.size();
        }

        public String getVodName() {
            return head.getVodName();
        }

        public String getVodPic() {
            return head.getVodPic();
        }

        public String getSiteKey() {
            return head.getSiteKey();
        }

        public String getVodId() {
            return head.getVodId();
        }

        public int getDoneCount() {
            return distinct(true);
        }

        public int getActiveCount() {
            return distinct(false);
        }

        /** 未完成任务的平均进度，全部完成时为 100。 */
        public int getProgress() {
            int total = 0;
            int count = 0;
            for (Download item : items) {
                total += item.isDone() ? 100 : item.getProgress();
                ++count;
            }
            return count == 0 ? 0 : total / count;
        }

        public List<Download> getPlayable() {
            List<Download> list = new ArrayList<>();
            for (Download item : items) if (item.isPlayable()) list.add(item);
            return list;
        }
    }
}
