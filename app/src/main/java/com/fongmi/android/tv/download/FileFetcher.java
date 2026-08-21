package com.fongmi.android.tv.download;

import com.github.catvod.utils.Logger;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import okhttp3.HttpUrl;

/** 直链视频缓存：单文件落盘，断点续传靠 Range。 */
public class FileFetcher {

    private static final String TAG = "FileFetcher";
    private static final int MAX_RETRY = 3;

    private final Map<String, String> headers;
    private final Progress progress;
    private final File dir;

    public FileFetcher(Map<String, String> headers, File dir, Progress progress) {
        this.headers = headers == null ? new HashMap<>() : headers;
        this.progress = progress;
        this.dir = dir;
    }

    public File download(String url) throws Exception {
        dir.mkdirs();
        File target = new File(dir, "video" + extension(url));
        long[] window = {System.currentTimeMillis(), 0, 0};
        long[] total = new long[1];
        IOException last = null;
        for (int retry = 0; retry < MAX_RETRY; retry++) {
            if (progress.isCancelled()) throw new Http.CancelException();
            File part = new File(target.getAbsolutePath() + ".part");
            long[] done = {part.exists() ? part.length() : 0};
            try {
                Http.download(url, headers, target, null, new Http.Counter() {
                    @Override
                    public boolean onRead(int bytes) {
                        if (progress.isCancelled()) return false;
                        done[0] += bytes;
                        tick(window, bytes);
                        report(done[0], total[0], window[2]);
                        return true;
                    }

                    @Override
                    public void onTotal(long value) {
                        if (value > 0) total[0] = value;
                    }
                });
                last = null;
                break;
            } catch (Http.CancelException e) {
                throw e;
            } catch (IOException e) {
                last = e;
                Logger.e(TAG, e);
                sleep(1000L * (retry + 1));
            }
        }
        if (last != null) throw new Exception("下载失败：" + last.getMessage());
        if (!target.exists() || target.length() == 0) throw new Exception("下载结果为空文件");
        progress.onProgress(100, target.length(), target.length(), 0, 0, 0);
        return target;
    }

    private void tick(long[] window, long bytes) {
        window[1] += bytes;
        long now = System.currentTimeMillis();
        long elapsed = now - window[0];
        if (elapsed < 1000) return;
        window[2] = window[1] * 1000 / elapsed;
        window[0] = now;
        window[1] = 0;
    }

    private long lastReport;

    private void report(long done, long total, long speed) {
        long now = System.currentTimeMillis();
        if (now - lastReport < 400) return;
        lastReport = now;
        int percent = total > 0 ? (int) (done * 100 / total) : 0;
        progress.onProgress(percent, done, total, 0, 0, speed);
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static String extension(String url) {
        try {
            HttpUrl parsed = HttpUrl.parse(url);
            String path = parsed == null ? url : parsed.encodedPath();
            int dot = path.lastIndexOf('.');
            if (dot == -1 || path.length() - dot > 6) return ".mp4";
            String ext = path.substring(dot).toLowerCase();
            return ext.matches("\\.(mp4|mkv|avi|mov|wmv|flv|webm|ts|m4v|mp3|m4a)") ? ext : ".mp4";
        } catch (Exception e) {
            return ".mp4";
        }
    }
}
