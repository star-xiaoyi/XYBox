package com.fongmi.android.tv.bean;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import com.fongmi.android.tv.db.AppDatabase;
import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

import java.io.File;
import java.util.List;

@Entity
public class Download {

    public static final int STATUS_PENDING = 0;
    public static final int STATUS_DOWNLOADING = 1;
    public static final int STATUS_COMPLETED = 2;
    public static final int STATUS_FAILED = -1;

    @PrimaryKey
    @NonNull
    @SerializedName("id")
    private String id;
    @SerializedName("vodPic")
    private String vodPic;
    @SerializedName("vodName")
    private String vodName;
    @SerializedName("vodId")
    private String vodId;
    @SerializedName("url")
    private String url;
    @SerializedName("header")
    private String header;
    @SerializedName("createTime")
    private long createTime;
    @SerializedName("progress")
    private int progress;
    @SerializedName("status")
    private String status;
    @SerializedName("statusInt")
    private int statusInt;
    @SerializedName("duration")
    private long duration;
    @SerializedName("speed")
    private long speed;

    public static Download objectFrom(String str) {
        try {
            return new Gson().fromJson(str, Download.class);
        } catch (Exception e) {
            return new Download();
        }
    }

    public Download() {
        this.createTime = System.currentTimeMillis();
        this.progress = 0;
        this.status = "pending";
    }

    public Download(@NonNull String id, String vodPic, String vodName, String url) {
        this.id = id;
        this.vodPic = vodPic;
        this.vodName = vodName;
        this.url = url;
        this.createTime = System.currentTimeMillis();
        this.progress = 0;
        this.status = "pending";
    }

    public Download(@NonNull String id, String vodPic, String vodName, String url, String header) {
        this.id = id;
        this.vodPic = vodPic;
        this.vodName = vodName;
        this.url = url;
        this.header = header;
        this.createTime = System.currentTimeMillis();
        this.progress = 0;
        this.status = "pending";
    }

    @NonNull
    public String getId() {
        return id == null ? "" : id;
    }

    public void setId(@NonNull String id) {
        this.id = id;
    }

    public String getVodPic() {
        return vodPic == null ? "" : vodPic;
    }

    public void setVodPic(String vodPic) {
        this.vodPic = vodPic;
    }

    public String getVodName() {
        return vodName == null ? "" : vodName;
    }

    public void setVodName(String vodName) {
        this.vodName = vodName;
    }

    public String getVodId() {
        return vodId == null ? "" : vodId;
    }

    public void setVodId(String vodId) {
        this.vodId = vodId;
    }

    public String getUrl() {
        return url == null ? "" : url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getHeader() {
        return header == null ? "" : header;
    }

    public void setHeader(String header) {
        this.header = header;
    }

    public long getCreateTime() {
        return createTime;
    }

    public void setCreateTime(long createTime) {
        this.createTime = createTime;
    }

    public int getProgress() {
        return progress;
    }

    public void setProgress(int progress) {
        this.progress = progress;
    }

    public String getStatus() {
        return status == null ? "pending" : status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public int getStatusInt() {
        return statusInt;
    }

    public void setStatusInt(int statusInt) {
        this.statusInt = statusInt;
    }

    public long getDuration() {
        return duration;
    }

    public void setDuration(long duration) {
        this.duration = duration;
    }

    public long getSpeed() {
        return speed;
    }

    public void setSpeed(long speed) {
        this.speed = speed;
    }

    public Download save() {
        AppDatabase.get().getDownloadDao().insertOrUpdate(this);
        return this;
    }

    public void delete() {
        AppDatabase.get().getDownloadDao().delete(this);
        deleteVideoFile();
    }

    private String generateFileName(String vodName, String url) {
        String cleanName = vodName.replaceAll("[\\\\/:*?\"<>|]", "_");
        String extension = ".mp4";
        if (isM3U8Url(url)) {
            extension = ".mp4";
        } else if (url != null && url.contains(".")) {
            String urlExt = url.substring(url.lastIndexOf("."));
            if (urlExt.contains("?")) {
                urlExt = urlExt.substring(0, urlExt.indexOf("?"));
            }
            if (urlExt.matches("\\.(mp4|mkv|avi|mov|wmv|flv|webm)")) {
                extension = urlExt;
            }
        }
        return cleanName + extension;
    }

    private boolean isM3U8Url(String url) {
        if (url == null) return false;
        String lowerUrl = url.toLowerCase();
        return lowerUrl.contains(".m3u8") || lowerUrl.contains("m3u8") || lowerUrl.contains("playlist");
    }

    public static void clear() {
        List<Download> allDownloads = AppDatabase.get().getDownloadDao().getAll();
        for (Download download : allDownloads) {
            download.deleteVideoFile();
        }
        AppDatabase.get().getDownloadDao().clear();
    }

    public void deleteVideoFile() {
        try {
            File downloadDir = com.github.catvod.utils.Path.download();
            String fileName = generateFileName(this.vodName, this.url);
            File videoFile = new File(downloadDir, fileName);
            if (videoFile.exists()) {
                videoFile.delete();
            }
        } catch (Exception e) {
            com.github.catvod.utils.Logger.e("Download: 删除视频文件出错", e);
        }
    }

    public static List<Download> getAll() {
        return AppDatabase.get().getDownloadDao().getAll();
    }

    public static Download find(String id) {
        return AppDatabase.get().getDownloadDao().find(id);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Download)) return false;
        Download download = (Download) obj;
        return getId().equals(download.getId());
    }

    @Override
    public String toString() {
        return new Gson().toJson(this);
    }
}
