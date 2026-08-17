package com.fongmi.android.tv.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.media.MediaMetadataRetriever;
import android.os.Build;
import android.util.Log;
import android.os.IBinder;
import androidx.core.app.NotificationCompat;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.Download;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.M3U8Downloader;
import com.fongmi.android.tv.utils.M3U8VideoDownloader;
import com.fongmi.android.tv.utils.SimpleAdvancedDownloadManager;
import com.github.catvod.utils.Path;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.Map;
import java.util.HashMap;

public class DownloadService extends Service {

    public static final String ACTION_START = "START_DOWNLOAD";
    public static final String ACTION_STOP = "CANCEL_DOWNLOAD";
    
    private static final String CHANNEL_ID = "download_channel";
    private static final int NOTIFICATION_ID = 1001;
    
    private ExecutorService executor;
    private NotificationManager notificationManager;
    private final Map<String, DownloadTask> activeTasks = new ConcurrentHashMap<>();

    public static void startDownload(Download download) {
        Intent intent = new Intent(App.get(), DownloadService.class);
        intent.setAction("START_DOWNLOAD");
        intent.putExtra("download", download.toString());
        App.get().startForegroundService(intent);
    }

    public static void cancelDownload(String downloadId) {
        Intent intent = new Intent(App.get(), DownloadService.class);
        intent.setAction("CANCEL_DOWNLOAD");
        intent.putExtra("download_id", downloadId);
        App.get().startService(intent);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        executor = Executors.newFixedThreadPool(3); // 最多3个并发下载
        notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_NOT_STICKY;

        String action = intent.getAction();
        if ("START_DOWNLOAD".equals(action)) {
            String downloadJson = intent.getStringExtra("download");
            Download download = Download.objectFrom(downloadJson);
            startForeground(NOTIFICATION_ID, createNotification("准备下载...", 0));
            startDownloadTask(download);
        } else if ("ACTION_PAUSE_DOWNLOAD".equals(action)) {
            // 暂停下载
            String downloadId = intent.getStringExtra("download_id");
            cancelDownloadTask(downloadId);
            
            // 更新数据库状态
            Download download = Download.find(downloadId);
            if (download != null) {
                download.setStatus("paused");
                download.save();
                // 通知UI刷新下载状态
                com.fongmi.android.tv.event.RefreshEvent.download();
            }
        } else if ("ACTION_RESUME_DOWNLOAD".equals(action)) {
            // 继续下载（类似重试，但保留进度）
            String downloadId = intent.getStringExtra("download_id");
            Download download = Download.find(downloadId);
            if (download != null) {
                download.setStatus("pending");
                download.save();
                // 通知UI刷新下载状态
                com.fongmi.android.tv.event.RefreshEvent.download();
                
                startForeground(NOTIFICATION_ID, createNotification("继续下载: " + download.getVodName(), download.getProgress()));
                startDownloadTask(download);
            }
        } else if ("ACTION_RETRY_DOWNLOAD".equals(action)) {
            // 重新下载逻辑
            String downloadId = intent.getStringExtra("download_id");
            String url = intent.getStringExtra("download_url");
            String name = intent.getStringExtra("download_name");
            String header = intent.getStringExtra("download_header");
            
            // 从数据库获取完整的Download对象
            Download download = Download.find(downloadId);
            if (download != null) {
                // 删除旧文件
                File downloadDir = com.github.catvod.utils.Path.download();
                String fileName = generateFileName(download.getVodName(), download.getUrl());
                File oldFile = new File(downloadDir, fileName);
                if (oldFile.exists()) {
                    oldFile.delete();
                }
                
                // 重置状态并重新下载
                download.setStatus("pending");
                download.setProgress(0);
                download.setSpeed(0);
                download.save();
                
                startForeground(NOTIFICATION_ID, createNotification("重新下载: " + name, 0));
                startDownloadTask(download);
            }
        } else if ("CANCEL_DOWNLOAD".equals(action)) {
            String downloadId = intent.getStringExtra("download_id");
            cancelDownloadTask(downloadId);
        }

        return START_STICKY;
    }

    private void startDownloadTask(Download download) {
        if (activeTasks.containsKey(download.getId())) {
            return; // 任务已存在
        }

        DownloadTask task = new DownloadTask(download);
        activeTasks.put(download.getId(), task);
        executor.execute(task);
    }

    private void cancelDownloadTask(String downloadId) {
        DownloadTask task = activeTasks.get(downloadId);
        if (task != null) {
            task.cancel();
            activeTasks.remove(downloadId);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (executor != null) {
            executor.shutdownNow();
        }
        for (DownloadTask task : activeTasks.values()) {
            task.cancel();
        }
        activeTasks.clear();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "视频下载",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("视频下载进度通知");
            notificationManager.createNotificationChannel(channel);
        }
    }

    private Notification createNotification(String title, int progress) {
        // Intent intent = new Intent(this, DownloadActivity.class); // DownloadActivity只在mobile版本可用
        Intent intent = new Intent(); // 空Intent，避免编译错误
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, intent, PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_action_download)
                .setContentTitle(title)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW);

        if (progress > 0) {
            builder.setProgress(100, progress, false)
                   .setContentText(progress + "%");
        } else {
            builder.setProgress(0, 0, true);
        }

        return builder.build();
    }

    private void updateNotification(String title, int progress) {
        Notification notification = createNotification(title, progress);
        notificationManager.notify(NOTIFICATION_ID, notification);
    }

    /**
     * 生成文件名（提取到Service级别供重试功能使用）
     */
    private String generateFileName(String vodName, String url) {
        String cleanName = vodName.replaceAll("[\\\\/:*?\"<>|]", "_");
        String extension = ".mp4";
        
        if (url != null && url.toLowerCase().contains(".m3u8")) {
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

    private class DownloadTask implements Runnable {
        private final Download download;
        private volatile boolean cancelled = false;
        private File outputFile;
        private M3U8Downloader m3u8Downloader;
        private M3U8VideoDownloader m3u8VideoDownloader; // 复刻base_extracted的M3U8下载器

        public DownloadTask(Download download) {
            this.download = download;
        }

        public void cancel() {
            cancelled = true;
            if (m3u8Downloader != null) {
                m3u8Downloader.cancel();
            }
            if (m3u8VideoDownloader != null) {
                m3u8VideoDownloader.cancel();
            }
        }

        @Override
        public void run() {
            try {
                
                // 准备下载目录
                File downloadDir = Path.download();
                
                if (!downloadDir.exists()) {
                    boolean created = downloadDir.mkdirs();
                }

                // 生成文件名
                String fileName = generateFileName(download.getVodName(), download.getUrl());
                outputFile = new File(downloadDir, fileName);

                // 检查URL类型
                boolean isM3U8 = isM3U8Url(download.getUrl());
                boolean hasM3U8Header = download.getHeader() != null && download.getHeader().contains("M3U8-Content");
                

                // 优先使用复刻的M3U8下载器
                if (isM3U8 || hasM3U8Header) {
                    downloadWithM3U8VideoDownloader();
                } else {
                    downloadWithAdvancedManager();
                }

            } catch (Exception e) {
                e.printStackTrace();
                onError(e.getMessage());
            } finally {
                activeTasks.remove(download.getId());
                if (activeTasks.isEmpty()) {
                    stopForeground(true);
                    stopSelf();
                }
            }
        }

        private void downloadWithAdvancedManager() {
            // 解析请求头
            Map<String, String> headers = new HashMap<>();
            if (download.getHeader() != null && !download.getHeader().isEmpty()) {
                String[] headerLines = download.getHeader().split("\n");
                for (String header : headerLines) {
                    String[] parts = header.split(":", 2);
                    if (parts.length == 2) {
                        headers.put(parts[0].trim(), parts[1].trim());
                    }
                }
            }
            
            // 使用简化版高级下载管理器进行下载
            SimpleAdvancedDownloadManager.getInstance().startAdvancedDownload(
                download.getUrl(),
                download.getVodName(),
                headers,
                new SimpleAdvancedDownloadManager.AdvancedDownloadCallback() {
                    @Override
                    public void onDownloadStart(String taskId, String title) {
                        updateNotification(title + " - 准备下载", 0);
                    }
                    
                    @Override
                    public void onDownloadProgress(String taskId, float progress, long speed) {
                        // 更新数据库中的进度和速度
                        download.setProgress((int) progress);
                        download.setSpeed(speed); // 更新下载速度（字节/秒）
                        download.setStatus("downloading");
                        download.save();
                        
                        updateNotification(download.getVodName(), (int) progress);
                        
                        // 检测进度卡住的情况
                        checkProgressStuck(progress, download);
                    }
                    
                    @Override
                    public void onDownloadSuccess(String taskId, File outputFile) {
                        // 更新为完成状态
                        download.setProgress(100);
                        download.setStatus("completed");
                        
                // 设置真实时长（从文件信息获取）
                long fileDuration = getVideoDuration(outputFile);
                if (fileDuration > 0) {
                    download.setDuration(fileDuration);
                } else {
                    // 如果无法获取时长，使用默认值
                    download.setDuration(45 * 60);
                }
                        
                        download.save();
                        
                        DownloadTask.this.outputFile = outputFile;
                        onSuccess();
                    }
                    
                    @Override
                    public void onDownloadError(String taskId, String error) {
                        // 更新为失败状态
                        download.setStatus("failed");
                        download.save();
                        
                        DownloadTask.this.onError("高级下载失败: " + error);
                    }
                }
            );
        }
        
        private void downloadNativeM3U8File() {
            // 解析请求头
            Map<String, String> headers = new HashMap<>();
            if (download.getHeader() != null && !download.getHeader().isEmpty()) {
                String[] headerLines = download.getHeader().split("\n");
                for (String header : headerLines) {
                    String[] parts = header.split(":", 2);
                    if (parts.length == 2) {
                        headers.put(parts[0].trim(), parts[1].trim());
                    }
                }
            }
            
            // 确保文件扩展名为.mp4
            String fileName = outputFile.getName();
            if (!fileName.toLowerCase().endsWith(".mp4")) {
                fileName = fileName.replaceAll("\\.[^.]*$", "") + ".mp4";
                outputFile = new File(outputFile.getParent(), fileName);
            }
            
            // 使用复刻的M3U8VideoDownloader进行下载
            downloadWithM3U8VideoDownloader();
        }
        
        private void downloadWithM3U8VideoDownloader() {
            // 直接使用Java M3U8下载器，复刻base_extracted的策略
            android.util.Log.i("DownloadService", "使用Java M3U8下载器");
            
            // 解析请求头
            String referer = null;
            if (download.getHeader() != null && !download.getHeader().isEmpty()) {
                String[] headerLines = download.getHeader().split("\n");
                for (String header : headerLines) {
                    if (header.toLowerCase().startsWith("referer:")) {
                        referer = header.substring(8).trim();
                        break;
                    }
                }
            }
            
            // 创建M3U8下载器
            m3u8VideoDownloader = new M3U8VideoDownloader();
            
            // 开始下载
            m3u8VideoDownloader.startDownload(
                download.getUrl(),
                download.getVodName(),
                referer,
                outputFile.getParentFile(),
                new M3U8VideoDownloader.M3U8DownloadCallback() {
                    @Override
                    public void onStart(String title) {
                        updateNotification(title + " - 开始下载", 0);
                    }
                    
                    @Override
                    public void onProgress(float progress, int downloadedSegments, int totalSegments, long speed) {
                        // 更新数据库中的进度和速度
                        download.setProgress((int) progress);
                        download.setSpeed(speed); // 更新下载速度（字节/秒）
                        download.setStatus("downloading");
                        download.save();
                        
                        updateNotification(download.getVodName() + " - " + downloadedSegments + "/" + totalSegments, (int) progress);
                    }
                    
                    @Override
                    public void onSuccess(File outputFile) {
                        // 使用VideoProcessManager进行后处理
                        processDownloadedFile(download, outputFile);
                    }
                    
                    @Override
                    public void onError(String error) {
                        // 更新为失败状态
                        download.setStatus("failed");
                        download.save();
                        
                        DownloadTask.this.onError("M3U8下载失败: " + error);
                    }
                }
            );
        }
        
        /**
         * 处理下载完成的文件（简化版，不使用VideoProcessManager）
         */
        private void processDownloadedFile(Download download, File outputFile) {
            try {
                // 直接完成，不需要转换
                download.setProgress(100);
                download.setStatus("completed");
                
                // 设置真实时长（从文件信息获取）
                long fileDuration = getVideoDuration(outputFile);
                if (fileDuration > 0) {
                    download.setDuration(fileDuration);
                } else {
                    download.setDuration(45 * 60);
                }
                
                download.save();
                
                DownloadTask.this.outputFile = outputFile;
                DownloadTask.this.onSuccess();
                
                updateNotification("下载完成: " + download.getVodName(), 100);
                
            } catch (Exception e) {
                download.setProgress(100);
                download.setStatus("completed");
                download.setDuration(45 * 60);
                download.save();
                
                DownloadTask.this.outputFile = outputFile;
                DownloadTask.this.onSuccess();
                
                updateNotification("下载完成: " + download.getVodName(), 100);
                android.util.Log.w("DownloadService", "处理下载文件失败", e);
            }
        }
        
        /**
         * 生成最终输出路径
         */
        private String generateFinalOutputPath(Download download, File outputFile) {
            String fileName = download.getVodName();
            if (fileName == null || fileName.isEmpty()) {
                fileName = "download";
            }
            
            // 清理文件名
            fileName = fileName.replaceAll("[^a-zA-Z0-9\\u4e00-\\u9fa5]", "_");
            
            File outputDir = outputFile.getParentFile();
            return new File(outputDir, fileName + ".mp4").getAbsolutePath();
        }
        
        private void downloadM3U8File() {
            // 保留原有方法作为备用方案
            // 解析请求头
            Map<String, String> headers = new HashMap<>();
            if (download.getHeader() != null && !download.getHeader().isEmpty()) {
                String[] headerLines = download.getHeader().split("\n");
                for (String header : headerLines) {
                    String[] parts = header.split(":", 2);
                    if (parts.length == 2) {
                        headers.put(parts[0].trim(), parts[1].trim());
                    }
                }
            }
            
            // 确保文件扩展名为.mp4
            String fileName = outputFile.getName();
            if (!fileName.toLowerCase().endsWith(".mp4")) {
                fileName = fileName.replaceAll("\\.[^.]*$", "") + ".mp4";
                outputFile = new File(outputFile.getParent(), fileName);
            }
            
            m3u8Downloader = new M3U8Downloader(download.getUrl(), outputFile, headers, 
                new M3U8Downloader.ProgressCallback() {
                    @Override
                    public void onProgress(int progress, long downloadedSize, long totalSize) {
                        updateNotification(download.getVodName(), progress);
                        // 可以在这里添加更详细的进度通知
                    }

                    @Override
                    public void onSuccess(File outputFile) {
                        DownloadTask.this.outputFile = outputFile;
                        DownloadTask.this.onSuccess();
                    }

                    @Override
                    public void onError(String error) {
                        DownloadTask.this.onError("M3U8下载失败: " + error);
                    }
                });
            
            m3u8Downloader.start();
        }

        private void downloadFile() throws Exception {
            URL url = new URL(download.getUrl());
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();

            // 设置请求头
            if (download.getHeader() != null && !download.getHeader().isEmpty()) {
                String[] headers = download.getHeader().split("\n");
                for (String header : headers) {
                    String[] parts = header.split(":", 2);
                    if (parts.length == 2) {
                        connection.setRequestProperty(parts[0].trim(), parts[1].trim());
                    }
                }
            }

            connection.setRequestProperty("User-Agent", 
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
            connection.connect();

            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw new Exception("HTTP错误: " + responseCode);
            }

            long totalSize = connection.getContentLengthLong();
            InputStream inputStream = connection.getInputStream();
            FileOutputStream outputStream = new FileOutputStream(outputFile);

            byte[] buffer = new byte[8192];
            long downloadedSize = 0;
            int bytesRead;
            int lastProgress = 0;

            while ((bytesRead = inputStream.read(buffer)) != -1) {
                if (cancelled) {
                    inputStream.close();
                    outputStream.close();
                    if (outputFile.exists()) {
                        outputFile.delete();
                    }
                    return;
                }

                outputStream.write(buffer, 0, bytesRead);
                downloadedSize += bytesRead;

                // 更新进度
                if (totalSize > 0) {
                    int progress = (int) (downloadedSize * 100 / totalSize);
                    if (progress != lastProgress && progress % 5 == 0) { // 每5%更新一次
                        lastProgress = progress;
                        updateNotification(download.getVodName(), progress);
                        App.post(() -> Notify.show("下载进度: " + progress + "%"));
                    }
                }
            }

            inputStream.close();
            outputStream.close();
            connection.disconnect();

            // 下载完成
            onSuccess();
        }

        private boolean isM3U8Url(String url) {
            if (url == null || url.isEmpty()) {
                return false;
            }
            String lowerUrl = url.toLowerCase();
            return lowerUrl.contains(".m3u8") || 
                   lowerUrl.contains("m3u8") ||
                   lowerUrl.contains("hls");
        }

        private void onSuccess() {
            App.post(() -> {
                updateNotification(download.getVodName() + " 下载完成", 100);
                Notify.show("下载完成: " + download.getVodName());
                // 通知UI刷新下载状态
                com.fongmi.android.tv.event.RefreshEvent.download();
            });
        }

        private void onError(String error) {
            App.post(() -> {
                updateNotification("下载失败", 0);
                Notify.show("下载失败: " + error);
                // 通知UI刷新下载状态
                com.fongmi.android.tv.event.RefreshEvent.download();
            });
        }
    }
    
    private long getVideoDuration(File videoFile) {
        if (!videoFile.exists() || videoFile.length() == 0) {
            Log.w("DownloadService", "视频文件不存在或为空: " + videoFile.getAbsolutePath());
            return 0;
        }
        
        MediaMetadataRetriever retriever = null;
        try {
            retriever = new MediaMetadataRetriever();
            retriever.setDataSource(videoFile.getAbsolutePath());
            
            String durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            
            if (durationStr != null && !durationStr.isEmpty()) {
                long duration = Long.parseLong(durationStr);
                long durationInSeconds = duration / 1000; // 转换为秒
                Log.i("DownloadService", "成功获取视频时长: " + durationInSeconds + "秒, 文件: " + videoFile.getName());
                return durationInSeconds;
            } else {
                Log.w("DownloadService", "无法从元数据获取时长: " + videoFile.getName());
                // 尝试使用文件大小估算时长（作为备用方案）
                return estimateDurationFromFileSize(videoFile);
            }
        } catch (Exception e) {
            Log.e("DownloadService", "获取视频时长失败: " + videoFile.getName(), e);
            // 尝试使用文件大小估算时长（作为备用方案）
            return estimateDurationFromFileSize(videoFile);
        } finally {
            if (retriever != null) {
                try {
                    retriever.release();
                } catch (Exception e) {
                    Log.e("DownloadService", "释放MediaMetadataRetriever失败", e);
                }
            }
        }
    }
    
    /**
     * 根据文件大小估算视频时长（备用方案）
     */
    private long estimateDurationFromFileSize(File videoFile) {
        try {
            long fileSizeInMB = videoFile.length() / (1024 * 1024);
            if (fileSizeInMB > 0) {
                // 假设平均码率为1Mbps，这是一个粗略估算
                // 实际应用中可以根据视频质量调整这个值
                long estimatedDuration = fileSizeInMB * 8; // 秒
                // 限制在合理范围内 (5分钟到3小时)
                estimatedDuration = Math.max(300, Math.min(estimatedDuration, 10800));
                Log.i("DownloadService", "根据文件大小估算时长: " + estimatedDuration + "秒, 文件大小: " + fileSizeInMB + "MB");
                return estimatedDuration;
            }
        } catch (Exception e) {
            Log.e("DownloadService", "估算视频时长失败", e);
        }
        return 0;
    }
    
    // 进度卡住检测相关变量
    private float lastProgress = 0;
    private long lastProgressTime = 0;
    private static final long PROGRESS_TIMEOUT = 30000; // 30秒超时
    
    /**
     * 检测下载进度是否卡住
     */
    private void checkProgressStuck(float currentProgress, Download download) {
        long currentTime = System.currentTimeMillis();
        
        // 如果进度没有变化且超过30秒，认为卡住了
        if (Math.abs(currentProgress - lastProgress) < 0.1f && 
            lastProgressTime > 0 && 
            currentTime - lastProgressTime > PROGRESS_TIMEOUT) {
            
            Log.w("DownloadService", "检测到下载进度卡住: " + currentProgress + "%, 超过" + PROGRESS_TIMEOUT/1000 + "秒无变化");
            
            // 尝试重新启动下载
            App.post(() -> {
                Notify.show("下载进度卡住，正在重试...");
                restartDownload(download);
            });
        }
        
        // 更新进度记录
        if (Math.abs(currentProgress - lastProgress) >= 0.1f) {
            lastProgress = currentProgress;
            lastProgressTime = currentTime;
        }
    }
    
    /**
     * 重新启动下载
     */
    private void restartDownload(Download download) {
        try {
            // 将状态设为失败，然后重新开始
            download.setStatus("failed");
            download.save();
            
            // 延迟2秒后重新开始下载
            App.post(() -> {
                download.setStatus("pending");
                download.setProgress(0);
                download.save();
                startDownloadTask(download);
            }, 2000);
            
        } catch (Exception e) {
            Log.e("DownloadService", "重启下载失败", e);
        }
    }
}
