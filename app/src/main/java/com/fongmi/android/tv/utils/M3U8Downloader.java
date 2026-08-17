package com.fongmi.android.tv.utils;

import android.util.Log;

import com.fongmi.android.tv.App;
import com.github.catvod.net.OkHttp;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import okhttp3.Response;

/**
 * M3U8视频下载器，支持HLS流媒体下载
 */
public class M3U8Downloader {
    
    private static final String TAG = "M3U8Downloader";
    private static final int MAX_RETRY_COUNT = 3;
    private static final int THREAD_POOL_SIZE = 4;
    
    public interface ProgressCallback {
        void onProgress(int progress, long downloadedSize, long totalSize);
        void onSuccess(File outputFile);
        void onError(String error);
    }
    
    private final String m3u8Url;
    private final File outputFile;
    private final Map<String, String> headers;
    private final ProgressCallback callback;
    private volatile boolean cancelled = false;
    
    public M3U8Downloader(String m3u8Url, File outputFile, Map<String, String> headers, ProgressCallback callback) {
        this.m3u8Url = m3u8Url;
        this.outputFile = outputFile;
        this.headers = headers;
        this.callback = callback;
    }
    
    public void cancel() {
        cancelled = true;
    }
    
    public void start() {
        App.execute(this::downloadM3U8);
    }
    
    private void downloadM3U8() {
        try {
            Log.d(TAG, "开始下载M3U8: " + m3u8Url);
            
            // 1. 获取m3u8播放列表
            String m3u8Content = getM3U8Content();
            if (cancelled) return;
            
            // 2. 解析ts分段列表
            List<String> tsUrls = parseM3U8(m3u8Content);
            if (tsUrls.isEmpty()) {
                notifyError("解析M3U8文件失败，没有找到视频分段");
                return;
            }
            
            Log.d(TAG, "解析到 " + tsUrls.size() + " 个视频分段");
            
            // 3. 下载所有ts分段并合并
            downloadAndMergeSegments(tsUrls);
            
        } catch (Exception e) {
            Log.e(TAG, "下载失败", e);
            notifyError("下载失败: " + e.getMessage());
        }
    }
    
    private String getM3U8Content() throws IOException {
        Log.d(TAG, "获取M3U8内容: " + m3u8Url);
        
        try (Response response = OkHttp.newCall(m3u8Url).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("获取M3U8失败: HTTP " + response.code());
            }
            return response.body().string();
        }
    }
    
    private List<String> parseM3U8(String content) {
        List<String> tsUrls = new ArrayList<>();
        String[] lines = content.split("\\n");
        
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            
            // 处理相对URL
            String tsUrl = resolveUrl(line);
            tsUrls.add(tsUrl);
        }
        
        return tsUrls;
    }
    
    private String resolveUrl(String url) {
        if (url.startsWith("http://") || url.startsWith("https://")) {
            return url;
        }
        
        // 处理相对URL
        String baseUrl = m3u8Url.substring(0, m3u8Url.lastIndexOf("/") + 1);
        return baseUrl + url;
    }
    
    private void downloadAndMergeSegments(List<String> tsUrls) throws IOException {
        File tempDir = new File(outputFile.getParent(), "temp_" + System.currentTimeMillis());
        if (!tempDir.mkdirs()) {
            throw new IOException("创建临时目录失败");
        }
        
        try {
            // 下载所有分段
            List<File> segmentFiles = downloadSegments(tsUrls, tempDir);
            if (cancelled) return;
            
            // 合并分段
            mergeSegments(segmentFiles, outputFile);
            if (cancelled) return;
            
            notifySuccess();
            
        } finally {
            // 清理临时文件
            cleanupTempFiles(tempDir);
        }
    }
    
    private List<File> downloadSegments(List<String> tsUrls, File tempDir) throws IOException {
        List<File> segmentFiles = new ArrayList<>();
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        AtomicInteger completedCount = new AtomicInteger(0);
        AtomicInteger failedCount = new AtomicInteger(0);
        
        try {
            for (int i = 0; i < tsUrls.size(); i++) {
                if (cancelled) break;
                
                final int index = i;
                final String tsUrl = tsUrls.get(i);
                final File segmentFile = new File(tempDir, String.format("segment_%04d.ts", i));
                segmentFiles.add(segmentFile);
                
                executor.submit(() -> {
                    try {
                        downloadSegment(tsUrl, segmentFile);
                        int completed = completedCount.incrementAndGet();
                        
                        // 更新进度
                        int progress = (completed * 90) / tsUrls.size(); // 下载占90%
                        App.post(() -> {
                            if (callback != null) {
                                callback.onProgress(progress, completed * 1024L, tsUrls.size() * 1024L);
                            }
                        });
                        
                    } catch (Exception e) {
                        Log.e(TAG, "下载分段失败: " + tsUrl, e);
                        failedCount.incrementAndGet();
                    }
                });
            }
            
            executor.shutdown();
            if (!executor.awaitTermination(30, TimeUnit.MINUTES)) {
                Log.w(TAG, "下载超时");
                executor.shutdownNow();
            }
            
            if (failedCount.get() > 0) {
                throw new IOException("有 " + failedCount.get() + " 个分段下载失败");
            }
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("下载被中断");
        }
        
        return segmentFiles;
    }
    
    private void downloadSegment(String tsUrl, File segmentFile) throws IOException {
        int retryCount = 0;
        IOException lastException = null;
        
        while (retryCount < MAX_RETRY_COUNT) {
            try {
                Log.d(TAG, "下载分段: " + tsUrl + " -> " + segmentFile.getName());
                
                URL url = new URL(tsUrl);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                
                // 设置请求头
                for (Map.Entry<String, String> header : headers.entrySet()) {
                    connection.setRequestProperty(header.getKey(), header.getValue());
                }
                connection.setRequestProperty("User-Agent", 
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
                
                connection.connect();
                
                if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                    throw new IOException("HTTP错误: " + connection.getResponseCode());
                }
                
                try (InputStream inputStream = connection.getInputStream();
                     FileOutputStream outputStream = new FileOutputStream(segmentFile)) {
                    
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                        if (cancelled) {
                            connection.disconnect();
                            return;
                        }
                        outputStream.write(buffer, 0, bytesRead);
                    }
                }
                
                connection.disconnect();
                return; // 下载成功
                
            } catch (IOException e) {
                lastException = e;
                retryCount++;
                Log.w(TAG, "下载分段失败，重试 " + retryCount + "/" + MAX_RETRY_COUNT + ": " + tsUrl);
                
                if (retryCount < MAX_RETRY_COUNT) {
                    try {
                        Thread.sleep(1000 * retryCount); // 递增等待时间
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new IOException("下载被中断");
                    }
                }
            }
        }
        
        throw new IOException("下载分段失败，已重试 " + MAX_RETRY_COUNT + " 次: " + tsUrl, lastException);
    }
    
    private void mergeSegments(List<File> segmentFiles, File outputFile) throws IOException {
        Log.d(TAG, "合并视频分段到: " + outputFile.getAbsolutePath());
        
        try (FileOutputStream outputStream = new FileOutputStream(outputFile)) {
            byte[] buffer = new byte[8192];
            
            for (int i = 0; i < segmentFiles.size(); i++) {
                if (cancelled) return;
                
                File segmentFile = segmentFiles.get(i);
                if (!segmentFile.exists()) {
                    throw new IOException("分段文件不存在: " + segmentFile.getName());
                }
                
                try (InputStream inputStream = segmentFile.toURI().toURL().openStream()) {
                    int bytesRead;
                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                        if (cancelled) return;
                        outputStream.write(buffer, 0, bytesRead);
                    }
                }
                
                // 更新合并进度 (90% + 10%)
                int progress = 90 + (i * 10) / segmentFiles.size();
                App.post(() -> {
                    if (callback != null) {
                        callback.onProgress(progress, outputFile.length(), outputFile.length());
                    }
                });
            }
        }
        
        Log.d(TAG, "视频合并完成: " + outputFile.length() + " bytes");
    }
    
    private void cleanupTempFiles(File tempDir) {
        try {
            if (tempDir.exists()) {
                File[] files = tempDir.listFiles();
                if (files != null) {
                    for (File file : files) {
                        if (!file.delete()) {
                            Log.w(TAG, "删除临时文件失败: " + file.getAbsolutePath());
                        }
                    }
                }
                if (!tempDir.delete()) {
                    Log.w(TAG, "删除临时目录失败: " + tempDir.getAbsolutePath());
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "清理临时文件失败", e);
        }
    }
    
    private void notifyProgress(int progress, long downloadedSize, long totalSize) {
        App.post(() -> {
            if (callback != null && !cancelled) {
                callback.onProgress(progress, downloadedSize, totalSize);
            }
        });
    }
    
    private void notifySuccess() {
        App.post(() -> {
            if (callback != null && !cancelled) {
                callback.onSuccess(outputFile);
            }
        });
    }
    
    private void notifyError(String error) {
        App.post(() -> {
            if (callback != null && !cancelled) {
                callback.onError(error);
            }
        });
    }
    
    /**
     * 检查URL是否为M3U8格式
     */
    public static boolean isM3U8Url(String url) {
        if (url == null || url.isEmpty()) {
            return false;
        }
        
        String lowerUrl = url.toLowerCase();
        return lowerUrl.contains(".m3u8") || 
               lowerUrl.contains("m3u8") ||
               lowerUrl.contains("hls");
    }
}
