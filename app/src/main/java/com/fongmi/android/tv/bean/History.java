package com.fongmi.android.tv.bean;
import com.github.catvod.utils.Logger;

import android.text.TextUtils;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.media3.common.C;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.Constant;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.db.AppDatabase;
import com.fongmi.android.tv.event.RefreshEvent;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Entity(primaryKeys = {"accountId", "key"})
public class History {
    @NonNull
    @androidx.room.ColumnInfo(name = "accountId")
    private transient String accountId = com.fongmi.android.tv.utils.LocalProfile.id();
    @NonNull public String getAccountId() { return accountId; }
    public void setAccountId(@NonNull String value) { accountId = value; }


    @NonNull
    @SerializedName("key")
    private String key;
    @SerializedName("vodPic")
    private String vodPic;
    @SerializedName("vodName")
    private String vodName;
    @SerializedName("filmId") private String filmId;
    @SerializedName("sourceKeys") private String sourceKeys;
    public String getFilmId() { return filmId == null ? "" : filmId; }
    public void setFilmId(String value) { filmId = value; }
    public String getSourceKeys() { return sourceKeys == null ? "" : sourceKeys; }
    public void setSourceKeys(String value) { sourceKeys = value; }
    @SerializedName("vodYear") private String vodYear;
    @SerializedName("vodType") private String vodType;
    @SerializedName("sharedProgress")
    @androidx.room.ColumnInfo(defaultValue = "0")
    private boolean sharedProgress;
    public String getVodYear() { return vodYear == null ? "" : vodYear; }
    public void setVodYear(String value) { vodYear = value; }
    public String getVodType() { return vodType == null ? "" : vodType; }
    public void setVodType(String value) { vodType = value; }
    public boolean isSharedProgress() { return sharedProgress; }
    public void setSharedProgress(boolean value) { sharedProgress = value; }
    @SerializedName("vodFlag")
    private String vodFlag;
    @SerializedName("vodRemarks")
    private String vodRemarks;
    @SerializedName("episodeUrl")
    private String episodeUrl;
    @SerializedName("episodeCount")
    @androidx.room.ColumnInfo(defaultValue = "0")
    private int episodeCount;
    @SerializedName("episodeNumber")
    @androidx.room.ColumnInfo(defaultValue = "0")
    private int episodeNumber;

    public int getEpisodeCount() { return episodeCount; }
    public void setEpisodeCount(int value) { episodeCount = value; }
    public int getEpisodeNumber() { return episodeNumber; }
    public void setEpisodeNumber(int value) { episodeNumber = value; }
    @SerializedName("revSort")
    private boolean revSort;
    @SerializedName("revPlay")
    private boolean revPlay;
    @SerializedName("createTime")
    private long createTime;
    @SerializedName("opening")
    private long opening;
    @SerializedName("ending")
    private long ending;
    @SerializedName("position")
    private long position;
    @SerializedName("duration")
    private long duration;
    @SerializedName("speed")
    private float speed;
    @SerializedName("scale")
    private int scale;
    @SerializedName("qualityHeight")
    @androidx.room.ColumnInfo(defaultValue = "0")
    private int qualityHeight;
    @SerializedName("cid")
    private int cid;

    public static History objectFrom(String str) {
        return App.gson().fromJson(str, History.class);
    }

    public static List<History> arrayFrom(String str) {
        Type listType = new TypeToken<List<History>>() {}.getType();
        List<History> items = App.gson().fromJson(str, listType);
        return items == null ? Collections.emptyList() : items;
    }

    public History() {
        this.speed = 1;
        this.scale = -1;
        this.ending = C.TIME_UNSET;
        this.opening = C.TIME_UNSET;
        this.position = C.TIME_UNSET;
        this.duration = C.TIME_UNSET;
    }

    @NonNull
    public String getKey() {
        return key;
    }

    public void setKey(@NonNull String key) {
        this.key = key;
    }

    public String getVodPic() {
        return vodPic;
    }

    public void setVodPic(String vodPic) {
        this.vodPic = vodPic;
    }

    public String getVodName() {
        return vodName;
    }

    public void setVodName(String vodName) {
        this.vodName = vodName;
    }

    public String getVodFlag() {
        return vodFlag;
    }

    public void setVodFlag(String vodFlag) {
        this.vodFlag = vodFlag;
    }

    public String getVodRemarks() {
        return vodRemarks == null ? "" : vodRemarks;
    }

    public void setVodRemarks(String vodRemarks) {
        this.vodRemarks = vodRemarks;
    }

    public String getEpisodeUrl() {
        return episodeUrl == null ? "" : episodeUrl;
    }

    public void setEpisodeUrl(String episodeUrl) {
        this.episodeUrl = episodeUrl;
    }

    public boolean isRevSort() {
        return revSort;
    }

    public void setRevSort(boolean revSort) {
        this.revSort = revSort;
    }

    public boolean isRevPlay() {
        return revPlay;
    }

    public void setRevPlay(boolean revPlay) {
        this.revPlay = revPlay;
    }

    public long getCreateTime() {
        return createTime;
    }

    public void setCreateTime(long createTime) {
        this.createTime = createTime;
    }

    public long getOpening() {
        return opening;
    }

    public void setOpening(long opening) {
        this.opening = opening;
    }

    public long getEnding() {
        return ending;
    }

    public void setEnding(long ending) {
        this.ending = ending;
    }

    public long getPosition() {
        return position;
    }

    public void setPosition(long position) {
        this.position = position;
    }

    public long getDuration() {
        return duration;
    }

    public void setDuration(long duration) {
        this.duration = duration;
    }

    public float getSpeed() {
        return speed;
    }

    public void setSpeed(float speed) {
        this.speed = speed;
    }

    public int getScale() {
        return scale;
    }

    public void setScale(int scale) {
        this.scale = scale;
    }

    public int getQualityHeight() {
        return qualityHeight;
    }

    public void setQualityHeight(int qualityHeight) {
        this.qualityHeight = Math.max(0, qualityHeight);
    }

    public int getCid() {
        return cid;
    }

    public void setCid(int cid) {
        this.cid = cid;
    }

    public String getSiteName() {
        return VodConfig.get().getSite(getCid(), getSiteKey()).getDisplayName();
    }

    public String getSiteKey() {
        return getKey().split(AppDatabase.SYMBOL)[0];
    }

    public String getVodId() {
        return getKey().split(AppDatabase.SYMBOL)[1];
    }

    public Flag getFlag() {
        return Flag.create(getVodFlag());
    }

    public Episode getEpisode() {
        return Episode.create(getVodRemarks(), getEpisodeUrl());
    }

    public int getSiteVisible() {
        return TextUtils.isEmpty(getSiteName()) ? View.GONE : View.VISIBLE;
    }

    public int getRevPlayText() {
        return isRevPlay() ? R.string.play_backward : R.string.play_forward;
    }

    public int getRevPlayHint() {
        return isRevPlay() ? R.string.play_backward_hint : R.string.play_forward_hint;
    }

    public static List<History> get() {
        return getAll();
    }

    public static List<History> get(int cid) {
        return AppDatabase.get().getHistoryDao().find(cid, System.currentTimeMillis() - Constant.HISTORY_TIME);
    }

    public static List<History> getAll() {
        return com.fongmi.android.tv.search.HistoryIdentity.group(AppDatabase.get().getHistoryDao().findAllRecent(System.currentTimeMillis() - Constant.HISTORY_TIME));
    }

    public static History find(String key) {
        return AppDatabase.get().getHistoryDao().findByKey(key);
    }

    public static void delete(int cid) {
        AppDatabase.get().getHistoryDao().delete(cid);
    }

    private void checkParam(History item) {
        if (getOpening() <= 0) setOpening(item.getOpening());
        if (getEnding() <= 0) setEnding(item.getEnding());
        if (getSpeed() == 1) setSpeed(item.getSpeed());
    }

    private void merge(List<History> items, boolean force) {
        preserveLegacy(items);
        com.fongmi.android.tv.search.HistoryIdentity.bind(this, items);
        int removed = 0;
        for (History item : items) {
            if (!force && getKey().equals(item.getKey())) continue;
            checkParam(item);
            item.deleteRecord();
            removed++;
        }
        if (removed > 0) Logger.i("PlayHistory: action=merge film=" + getFilmId() + " removed=" + removed
                + " episode=" + getVodRemarks() + " positionMs=" + getPosition());
    }

    public void update() {
        try {
            merge(find(), false);
            save();
        } catch (Exception e) {
            com.github.catvod.utils.Logger.e("History.update: 更新失败 - " + e.getMessage());
            Logger.e("Error", e);
        }
    }

    public History update(int cid) {
        return update(cid, find());
    }

    public History update(int cid, List<History> items) {
        setCid(cid);
        merge(items, true);
        return save();
    }

    /** Apply an already merged cloud snapshot without scheduling the same upload again. */
    public void applySynced(List<History> items) {
        merge(items, false);
        AppDatabase.get().getHistoryDao().insertOrUpdate(this);
    }

    public History save() {
        return save(false);
    }

    /** 保存不可变的播放快照；云端已写入较新版本时，拒绝旧播放器内存覆盖。 */
    public boolean savePlayback(long knownVersion) {
        boolean[] written = {false};
        AppDatabase.get().runInTransaction(() -> {
            List<History> records = find();
            for (History current : records) {
                if (!com.fongmi.android.tv.utils.PlaybackProgressPolicy.canWrite(knownVersion, current.getCreateTime())) {
                    Logger.i("PlayHistory: action=reject-stale film=" + getFilmId() + " known=" + knownVersion + " stored=" + current.getCreateTime());
                    return;
                }
            }
            merge(records, false);
            setSharedProgress(true);
            AppDatabase.get().getHistoryDao().insertOrUpdate(this);
            written[0] = true;
        });
        if (written[0] && getAccountId().equals(com.fongmi.android.tv.utils.LocalProfile.id())) com.fongmi.android.tv.utils.WebDAVSyncManager.get().requestProgressSync();
        return written[0];
    }

    /** 播放进度更新：本地照常落库，云端按节流上传。 */
    public void updateProgress() {
        try {
            // createTime 同时用于历史排序和 WebDAV 冲突比较；每次有效进度都要刷新。
            setCreateTime(System.currentTimeMillis());
            merge(find(), false);
            save(true);
        } catch (Exception e) {
            com.github.catvod.utils.Logger.e("History.updateProgress: 更新失败 - " + e.getMessage());
        }
    }

    private History save(boolean progressOnly) {
        boolean isNew = AppDatabase.get().getHistoryDao().findForAccount(getAccountId(), getCid(), getKey()) == null;
        AppDatabase.get().getHistoryDao().insertOrUpdate(this);
        com.fongmi.android.tv.utils.WebDAVSyncManager manager = com.fongmi.android.tv.utils.WebDAVSyncManager.get();
        if (progressOnly && !isNew) manager.requestProgressSync();
        else manager.requestSync();
        return this;
    }

    public History delete() {
        for (History item : find()) if (!item.getKey().equals(getKey())) item.deleteRecord();
        return deleteRecord();
    }

    private void preserveLegacy(List<History> items) {
        if (items.stream().noneMatch(item -> item.getFilmId().isEmpty())) return;
        java.io.File file = new java.io.File(App.get().getFilesDir(), "history-before-film-id-" + com.github.catvod.utils.Util.md5(getAccountId()) + ".json");
        if (file.isFile() && file.length() > 0) return;
        try {
            java.nio.file.Path temporary = new java.io.File(file.getParentFile(), file.getName() + ".tmp").toPath();
            java.nio.file.Files.write(temporary, App.gson().toJson(AppDatabase.get().getHistoryDao().findAllForAccount(getAccountId())).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            java.nio.file.Files.move(temporary, file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception error) { throw new IllegalStateException("无法备份旧观看记录，暂不合并", error); }
    }

    private History deleteRecord() {
        com.fongmi.android.tv.utils.WebDAVSyncManager.get().markHistoryDeleted(this);
        AppDatabase.get().getHistoryDao().deleteForAccount(getAccountId(), getCid(), getKey());
        AppDatabase.get().getTrackDao().delete(getKey());
        return this;
    }

    public List<History> find() {
        return com.fongmi.android.tv.search.HistoryIdentity.matching(this, AppDatabase.get().getHistoryDao().findAllForAccount(getAccountId()));
    }

    public void findEpisode(List<Flag> flags) {
        if (!flags.isEmpty()) {
            setVodFlag(flags.get(0).getFlag());
            if (!flags.get(0).getEpisodes().isEmpty()) {
                setVodRemarks(flags.get(0).getEpisodes().get(0).getName());
            }
        }
        for (History item : find()) {
            if (getPosition() > 0) break;
            for (Flag flag : flags) {
                Episode episode = flag.find(item.getVodRemarks(), true);
                if (episode == null) continue;
                setVodFlag(flag.getFlag());
                setPosition(item.getPosition());
                setVodRemarks(episode.getName());
                checkParam(item);
                break;
            }
        }
    }

    private static void startSync(List<History> targets) {
        for (History target : targets) {
            List<History> items = target.find();
            if (items.isEmpty()) {
                target.update(VodConfig.getCid(), items);
                continue;
            }
            for (History item : items) {
                if (target.getCreateTime() > item.getCreateTime()) {
                    target.update(VodConfig.getCid(), items);
                    break;
                }
            }
        }
    }

    public static void sync(List<History> targets) {
        App.execute(() -> {
            startSync(targets);
            RefreshEvent.history();
        });
    }

    @NonNull
    @Override
    public String toString() {
        return App.gson().toJson(this);
    }
}
