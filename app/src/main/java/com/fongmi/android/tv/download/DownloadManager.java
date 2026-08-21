package com.fongmi.android.tv.download;

import android.media.MediaMetadataRetriever;
import android.text.TextUtils;

import com.fongmi.android.tv.Setting;
import com.fongmi.android.tv.bean.Download;
import com.fongmi.android.tv.db.AppDatabase;
import com.fongmi.android.tv.event.RefreshEvent;
import com.fongmi.android.tv.service.DownloadService;
import com.github.catvod.utils.Logger;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 离线缓存队列：同时最多跑 {@link Setting#getDownloadTask()} 集，其余排队。
 * 状态全部落库，界面靠 {@link RefreshEvent#download()} 刷新，进程被杀后重进也能接着看到进度。
 */
public class DownloadManager {

    private static final String TAG = "DownloadManager";
    /**
     * 进度落库的最小间隔。跑满带宽时进度回调每秒能来上百次，
     * 而 SQLite 一次写盘就是几毫秒——全写下去光等磁盘就把速度吃掉了。
     * 内存里的值随时是新的，一秒落一次库足够界面和通知栏用。
     */
    private static final long PERSIST_INTERVAL = 1000;

    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final Map<String, Task> running = new ConcurrentHashMap<>();
    private final LinkedList<String> queue = new LinkedList<>();
    private final Set<String> removed = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private long lastNotify;

    private static class Loader {
        static volatile DownloadManager INSTANCE = new DownloadManager();
    }

    public static DownloadManager get() {
        return Loader.INSTANCE;
    }

    /** 进程重启后把上次没跑完的任务重新排上，暂停和失败的保持原状等用户点。 */
    public synchronized void restore() {
        // 早期版本离线播放会写伪站源的观看记录，把同名的在线记录挤掉，顺手清一次
        AppDatabase.get().getHistoryDao().deleteOffline();
        for (Download item : Download.getActive()) {
            if (item.isPaused() || item.isError()) continue;
            item.setStatus(Download.STATUS_PENDING);
            item.setSpeed(0);
            item.save();
            enqueue(item.getId());
        }
        if (!queue.isEmpty()) DownloadService.ensure();
        schedule();
    }

    public synchronized void add(List<Download> items) {
        List<Download> added = new ArrayList<>();
        for (Download item : items) {
            Download exist = Download.find(item.getId());
            if (exist != null && exist.isPlayable()) continue;
            Download target = exist == null ? item : exist;
            target.setEpisodeUrl(item.getEpisodeUrl());
            target.setVodPic(item.getVodPic());
            target.setVodName(item.getVodName());
            target.setStatus(Download.STATUS_PENDING);
            target.setErrorMsg("");
            target.setSpeed(0);
            target.save();
            removed.remove(target.getId());
            enqueue(target.getId());
            added.add(target);
        }
        if (!added.isEmpty()) DownloadService.ensure();
        schedule();
        notifyChanged(true);
    }

    public synchronized void resume(String id) {
        Download item = Download.find(id);
        if (item == null || item.isDone() || running.containsKey(id)) return;
        item.setStatus(Download.STATUS_PENDING);
        item.setErrorMsg("");
        item.save();
        removed.remove(id);
        enqueue(id);
        DownloadService.ensure();
        schedule();
        notifyChanged(true);
    }

    public synchronized void pause(String id) {
        Download item = Download.find(id);
        if (item == null || item.isDone()) return;
        queue.remove(id);
        Task task = running.get(id);
        if (task != null) task.cancel();
        item.setStatus(Download.STATUS_PAUSED);
        item.setSpeed(0);
        item.save();
        notifyChanged(true);
    }

    public synchronized void pauseAll() {
        for (Download item : Download.getActive()) if (!item.isPaused()) pause(item.getId());
    }

    public synchronized void resumeAll() {
        for (Download item : Download.getActive()) if (item.isPaused() || item.isError()) resume(item.getId());
    }

    /** 删除任务：停掉在跑的、清掉排队的、连带删除已经落盘的文件。 */
    public synchronized void remove(Download item) {
        if (item == null) return;
        String id = item.getId();
        queue.remove(id);
        removed.add(id);
        Task task = running.get(id);
        if (task != null) task.cancel();
        item.delete();
        notifyChanged(true);
    }

    public synchronized void removeGroup(String groupKey) {
        for (Download item : Download.getByGroup(groupKey)) remove(item);
    }

    public synchronized void removeAll() {
        for (Download item : Download.getAll()) {
            queue.remove(item.getId());
            removed.add(item.getId());
            Task task = running.get(item.getId());
            if (task != null) task.cancel();
        }
        Download.clear();
        notifyChanged(true);
    }

    public boolean isBusy() {
        return !running.isEmpty() || !queue.isEmpty();
    }

    public int getRunningCount() {
        return running.size();
    }

    private void enqueue(String id) {
        if (!queue.contains(id) && !running.containsKey(id)) queue.add(id);
    }

    private synchronized void schedule() {
        while (running.size() < Setting.getDownloadTask() && !queue.isEmpty()) {
            String id = queue.poll();
            Download item = Download.find(id);
            if (item == null || !item.isPending()) continue;
            Task task = new Task(item);
            running.put(id, task);
            executor.execute(task);
        }
    }

    /** 设置里改了同时下载数：调大就把排队的补上来；调小不动已经在跑的，等它们自己下完。 */
    public synchronized void applyLimit() {
        schedule();
        if (isBusy()) DownloadService.ensure();
        notifyChanged(true);
    }

    /**
     * 单集能开几条连接。设置里那个值是上限，实际还要按当前在跑的集数摊薄：
     * 5 集各开 16 条就是 80 个并发请求，手机扛得住但源站不会给好脸色，
     * 总量压在预算内既保住了单集速度，也不至于被当成刷流量。
     */
    private int threads() {
        return Math.max(1, Math.min(Setting.getDownloadThread(), Setting.DOWNLOAD_BUDGET / Math.max(1, running.size())));
    }

    private void notifyChanged(boolean force) {
        long now = System.currentTimeMillis();
        if (!force && now - lastNotify < 500) return;
        lastNotify = now;
        RefreshEvent.download();
        DownloadService.update();
    }

    private boolean isRemoved(String id) {
        return removed.contains(id);
    }

    private void persist(Download item) {
        if (isRemoved(item.getId())) return;
        item.save();
    }

    private void onFinish(String id) {
        synchronized (this) {
            running.remove(id);
            removed.remove(id);
            // 这一集跑着的时候被删掉又重新加过：那次 enqueue 会因为它还在 running 里被丢掉，
            // 不在这儿补一次的话它就会永远停在"等待中"，直到重启 App 才被 restore 捡回来
            Download item = Download.find(id);
            if (item != null && item.isPending()) enqueue(id);
            schedule();
        }
        notifyChanged(true);
        if (!isBusy()) DownloadService.done();
    }

    /** 通知栏要展示的当前任务，没有在跑的就返回 null。 */
    public Download getCurrent() {
        for (Task task : running.values()) return task.item;
        return null;
    }

    /** 通知栏关心的是"这会儿总共跑多快"，单看某一集的速率会让人以为带宽没吃满。 */
    public long getTotalSpeed() {
        long speed = 0;
        for (Task task : running.values()) speed += task.item.getSpeed();
        return speed;
    }

    /** 同时跑多集时通知栏只有一条进度条，取平均值才对得上"整体下到哪了"。 */
    public int getAverageProgress() {
        int count = 0;
        int total = 0;
        for (Task task : running.values()) {
            total += task.item.getProgress();
            count++;
        }
        return count == 0 ? 0 : total / count;
    }

    public int getPendingCount() {
        return queue.size();
    }

    private class Task implements Runnable, Progress {

        private final Download item;
        private volatile boolean cancelled;
        private volatile long lastPersist;

        Task(Download item) {
            this.item = item;
        }

        void cancel() {
            cancelled = true;
        }

        @Override
        public boolean isCancelled() {
            return cancelled || isRemoved(item.getId());
        }

        @Override
        public void onProgress(int percent, long doneBytes, long totalBytes, int doneSeg, int totalSeg, long speed) {
            // 已经被暂停/删除时不要再写库，否则会把刚落下的 PAUSED 状态又盖回 RUNNING
            if (isCancelled()) return;
            item.setProgress(percent);
            item.setDoneBytes(doneBytes);
            item.setTotalBytes(totalBytes);
            item.setDoneSeg(doneSeg);
            item.setTotalSeg(totalSeg);
            item.setSpeed(speed);
            // 这里跑在下载线程上，落库和刷界面都得让路，节流后才不会拖慢下载
            long now = System.currentTimeMillis();
            if (now - lastPersist < PERSIST_INTERVAL) return;
            lastPersist = now;
            persist(item);
            notifyChanged(false);
        }

        @Override
        public void run() {
            try {
                item.setStatus(Download.STATUS_RUNNING);
                item.setErrorMsg("");
                persist(item);
                notifyChanged(true);
                download();
            } catch (Http.CancelException e) {
                Logger.d(TAG + " 已取消 " + item.getEpisodeName());
            } catch (Throwable e) {
                Logger.e(TAG, e);
                if (!isCancelled()) {
                    item.setStatus(Download.STATUS_ERROR);
                    item.setErrorMsg(message(e));
                    item.setSpeed(0);
                    persist(item);
                }
            } finally {
                settle();
                onFinish(item.getId());
            }
        }

        /**
         * 收尾对账：进度回调和暂停/删除是两个线程，可能有一次写库擦肩而过，
         * 这里以数据库里的实际记录为准把状态摆正。
         */
        private void settle() {
            Download current = Download.find(item.getId());
            if (current == null) {
                // 记录已被删除，把可能又写出来的文件清干净
                item.clearFiles();
                return;
            }
            if (!cancelled || !current.isRunning()) return;
            current.setStatus(Download.STATUS_PAUSED);
            current.setSpeed(0);
            current.save();
        }

        private void download() throws Exception {
            long began = System.currentTimeMillis();
            Resolver.Address address = Resolver.resolve(item);
            DownloadLog.d("任务 %s 解析用时=%dms 在跑=%d", item.getEpisodeName(), System.currentTimeMillis() - began, running.size());
            if (isCancelled()) throw new Http.CancelException();
            File dir = item.dir();
            File output;
            long duration;
            int threads = threads();
            boolean hls = address.isHls() || Http.isPlaylist(address.getUrl(), address.getHeaders());
            DownloadLog.d("任务 %s 类型=%s 线程=%d 设置(集=%d,连接=%d)", item.getEpisodeName(), hls ? "HLS" : "直链",
                    threads, Setting.getDownloadTask(), Setting.getDownloadThread());
            if (hls) {
                HlsFetcher fetcher = new HlsFetcher(address.getHeaders(), dir, threads, this);
                output = fetcher.download(address.getUrl());
                duration = fetcher.getDuration();
            } else {
                output = new FileFetcher(address.getHeaders(), dir, threads, this).download(address.getUrl());
                duration = duration(output);
            }
            if (isCancelled()) throw new Http.CancelException();
            item.setLocalPath(output.getAbsolutePath());
            item.setStatus(Download.STATUS_DONE);
            item.setProgress(100);
            item.setSpeed(0);
            item.setDuration(duration);
            persist(item);
        }
    }

    private static long duration(File file) {
        MediaMetadataRetriever retriever = null;
        try {
            retriever = new MediaMetadataRetriever();
            retriever.setDataSource(file.getAbsolutePath());
            String value = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            return TextUtils.isEmpty(value) ? 0 : Long.parseLong(value) / 1000;
        } catch (Exception e) {
            return 0;
        } finally {
            try {
                if (retriever != null) retriever.release();
            } catch (Exception ignored) {
            }
        }
    }

    private static String message(Throwable e) {
        String message = e.getMessage();
        return TextUtils.isEmpty(message) ? e.getClass().getSimpleName() : message;
    }
}
