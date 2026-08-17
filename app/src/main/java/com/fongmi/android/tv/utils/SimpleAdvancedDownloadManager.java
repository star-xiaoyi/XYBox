package com.fongmi.android.tv.utils;

import android.content.Context;
import android.util.Log;

import com.fongmi.android.tv.App;
import com.github.catvod.net.OkHttp;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.Request;
import okhttp3.Response;

/**
 * 简化版高级下载管理器
 * 使用现有的OkHttp库进行下载
 */
public class SimpleAdvancedDownloadManager {
    
    private static final String TAG = "SimpleAdvancedDownloadManager";
    private static SimpleAdvancedDownloadManager instance;
    private final Map<String, DownloadTask> activeTasks = new ConcurrentHashMap<>();
    private final ExecutorService downloadExecutor = Executors.newFixedThreadPool(3);
    private boolean initialized = false;
    
    public interface AdvancedDownloadCallback {
        void onDownloadStart(String taskId, String title);
        void onDownloadProgress(String taskId, float progress, long speed);
        void onDownloadSuccess(String taskId, File outputFile);
        void onDownloadError(String taskId, String error);
    }
    
    public static synchronized SimpleAdvancedDownloadManager getInstance() {
        if (instance == null) {
            instance = new SimpleAdvancedDownloadManager();
        }
        return instance;
    }
    
    private SimpleAdvancedDownloadManager() {
        // 私有构造函数
    }
    
    /**
     * 初始化高级下载管理器
     */
    public void init(Context context) {
        if (initialized) return;
        
        try {
            initialized = true;
            Log.d(TAG, "简化版高级下载管理器初始化成功");
            
        } catch (Exception e) {
            Log.e(TAG, "简化版高级下载管理器初始化失败", e);
        }
    }
    
    /**
     * 开始高级下载
     */
    public void startAdvancedDownload(String url, String title, Map<String, String> headers, 
                                    AdvancedDownloadCallback callback) {
        if (!initialized) {
            Log.e(TAG, "下载管理器未初始化");
            if (callback != null) {
                callback.onDownloadError(url, "下载管理器未初始化");
            }
            return;
        }
        
        // 记录任务
        DownloadTask task = new DownloadTask(title, callback);
        activeTasks.put(url, task);
        
        // 提交下载任务
        downloadExecutor.submit(() -> downloadFile(url, title, headers, callback));
    }
    
    private void downloadFile(String url, String title, Map<String, String> headers, 
                            AdvancedDownloadCallback callback) {
        try {
            if (callback != null) {
                App.post(() -> callback.onDownloadStart(url, title));
            }
            
            // 构建请求
            Request.Builder requestBuilder = new Request.Builder().url(url);
            
            // 添加请求头
            if (headers != null) {
                for (Map.Entry<String, String> header : headers.entrySet()) {
                    requestBuilder.addHeader(header.getKey(), header.getValue());
                }
            }
            
            Request request = requestBuilder.build();
            
            // 执行请求
            try (Response response = OkHttp.newCall(url, okhttp3.Headers.of()).execute()) {
                if (!response.isSuccessful()) {
                    throw new IOException("下载失败: " + response.code());
                }
                
                // 获取文件大小
                long contentLength = response.body().contentLength();
                
                // 创建输出文件
                File downloadDir = new File(App.get().getExternalFilesDir(null), "downloads");
                if (!downloadDir.exists()) {
                    downloadDir.mkdirs();
                }
                
                String fileName = generateFileName(title, url);
                File outputFile = new File(downloadDir, fileName);
                
                // 下载文件
                try (InputStream inputStream = response.body().byteStream();
                     FileOutputStream outputStream = new FileOutputStream(outputFile)) {
                    
                    byte[] buffer = new byte[8192];
                    long downloadedBytes = 0;
                    int bytesRead;
                    long lastProgressTime = System.currentTimeMillis();
                    
                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, bytesRead);
                        downloadedBytes += bytesRead;
                        
                        // 更新进度 (每200ms更新一次，提高实时性)
                        long currentTime = System.currentTimeMillis();
                        if (currentTime - lastProgressTime > 200 && contentLength > 0) {
                            float progress = (float) downloadedBytes / contentLength * 100;
                            long speed = downloadedBytes * 1000 / (currentTime - lastProgressTime + 1);
                            
                            if (callback != null) {
                                App.post(() -> callback.onDownloadProgress(url, progress, speed));
                            }
                            lastProgressTime = currentTime;
                        }
                    }
                    
                    // 下载完成
                    if (callback != null) {
                        App.post(() -> callback.onDownloadSuccess(url, outputFile));
                    }
                    
                    Log.d(TAG, "下载完成: " + outputFile.getAbsolutePath());
                }
            }
            
        } catch (Exception e) {
            Log.e(TAG, "下载失败", e);
            if (callback != null) {
                App.post(() -> callback.onDownloadError(url, "下载失败: " + e.getMessage()));
            }
        } finally {
            activeTasks.remove(url);
        }
    }
    
    /**
     * 取消下载
     */
    public void cancelDownload(String url) {
        activeTasks.remove(url);
        // 这里可以添加更复杂的取消逻辑
    }
    
    /**
     * 生成文件名
     */
    private String generateFileName(String title, String url) {
        // 清理文件名中的非法字符
        String cleanName = title.replaceAll("[\\\\/:*?\"<>|]", "_");
        
        // 根据URL类型确定扩展名
        String extension = ".mp4"; // 默认扩展名
        
        if (url.toLowerCase().contains(".m3u8") || url.toLowerCase().contains("m3u8")) {
            extension = ".m3u8"; // M3U8文件先下载为m3u8，后续可转换
        } else if (url.contains(".")) {
            String urlExt = url.substring(url.lastIndexOf("."));
            if (urlExt.contains("?")) {
                urlExt = urlExt.substring(0, urlExt.indexOf("?"));
            }
            if (urlExt.length() <= 5 && urlExt.matches("\\.[a-zA-Z0-9]+")) {
                extension = urlExt;
            }
        }
        
        return cleanName + extension;
    }
    
    /**
     * 检查是否为M3U8链接
     */
    public boolean isM3U8Url(String url) {
        if (url == null || url.isEmpty()) {
            return false;
        }
        
        String lowerUrl = url.toLowerCase();
        return lowerUrl.contains(".m3u8") || 
               lowerUrl.contains("m3u8") ||
               lowerUrl.contains("hls");
    }
    
    /**
     * 获取活跃下载任务数
     */
    public int getActiveTaskCount() {
        return activeTasks.size();
    }
    
    /**
     * 清理资源
     */
    public void cleanup() {
        activeTasks.clear();
        if (!downloadExecutor.isShutdown()) {
            downloadExecutor.shutdown();
        }
    }
    
    /**
     * 下载任务内部类
     */
    private static class DownloadTask {
        final String title;
        final AdvancedDownloadCallback callback;
        
        DownloadTask(String title, AdvancedDownloadCallback callback) {
            this.title = title;
            this.callback = callback;
        }
    }
}
