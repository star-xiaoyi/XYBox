package com.fongmi.android.tv.utils;

import com.fongmi.android.tv.App;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Logger;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import okhttp3.Headers;
import okhttp3.Response;

/**
 * M3U8视频下载器
 * 使用简单拼接方式合并TS片段
 */
public class M3U8VideoDownloader {

    private static final String TAG = "M3U8VideoDownloader";
    private final ExecutorService executorService = Executors.newFixedThreadPool(3);
    private volatile boolean cancelled = false;

    public interface M3U8DownloadCallback {
        void onStart(String title);
        void onProgress(float progress, int downloadedSegments, int totalSegments, long speed);
        void onSuccess(File outputFile);
        void onError(String error);
    }

    public void startDownload(String m3u8Url, String title, String referer,
                            File outputDir, M3U8DownloadCallback callback) {

        executorService.submit(() -> {
            try {
                Logger.d("M3U8下载开始: " + title);

                if (callback != null) {
                    App.post(() -> callback.onStart(title));
                }

                // 1. 获取M3U8播放列表
                String m3u8Content = fetchM3U8Content(m3u8Url, referer);
                if (m3u8Content == null) {
                    throw new Exception("无法获取M3U8内容");
                }

                // 2. 解析TS片段列表
                List<String> tsUrls = parseM3U8Content(m3u8Content, m3u8Url);
                if (tsUrls.isEmpty()) {
                    throw new Exception("M3U8中没有找到TS片段");
                }

                Logger.d("解析到 " + tsUrls.size() + " 个TS片段");

                // 3. 下载所有TS片段
                List<File> tsFiles = downloadTsSegments(tsUrls, referer, outputDir,
                    tsUrls.size(), callback);

                if (cancelled) {
                    cleanupTsFiles(tsFiles);
                    return;
                }

                // 4. 合并TS片段为MP4（简单拼接方式）
                File outputFile = mergeTs2Mp4(tsFiles, outputDir, title);

                // 5. 清理临时文件
                cleanupTsFiles(tsFiles);

                if (callback != null) {
                    App.post(() -> callback.onSuccess(outputFile));
                }

                Logger.d("M3U8下载完成: " + outputFile.getAbsolutePath());

            } catch (Exception e) {
                Logger.e("M3U8下载失败: " + e.getMessage());
                if (callback != null) {
                    App.post(() -> callback.onError("下载失败: " + e.getMessage()));
                }
            }
        });
    }

    private String fetchM3U8Content(String m3u8Url, String referer) throws Exception {
        Headers.Builder headersBuilder = new Headers.Builder();
        if (referer != null && !referer.isEmpty()) {
            headersBuilder.add("Referer", referer);
        }
        headersBuilder.add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");

        try (Response response = OkHttp.newCall(m3u8Url, headersBuilder.build()).execute()) {
            if (!response.isSuccessful()) {
                throw new Exception("HTTP " + response.code() + ": " + response.message());
            }
            return response.body().string();
        }
    }

    private List<String> parseM3U8Content(String m3u8Content, String baseUrl) {
        List<String> tsUrls = new ArrayList<>();
        String[] lines = m3u8Content.split("\n");

        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;

            String tsUrl;
            if (line.startsWith("http")) {
                tsUrl = line;
            } else {
                tsUrl = resolveRelativeUrl(baseUrl, line);
            }

            if (tsUrl != null) {
                tsUrls.add(tsUrl);
            }
        }
        return tsUrls;
    }

    private String resolveRelativeUrl(String baseUrl, String relativeUrl) {
        try {
            if (relativeUrl.startsWith("http")) return relativeUrl;
            String baseDir = baseUrl.substring(0, baseUrl.lastIndexOf('/') + 1);
            return baseDir + relativeUrl;
        } catch (Exception e) {
            return null;
        }
    }

    private List<File> downloadTsSegments(List<String> tsUrls, String referer,
                                         File outputDir, int totalSegments,
                                         M3U8DownloadCallback callback) throws Exception {

        List<File> tsFiles = new ArrayList<>();
        AtomicInteger downloadedCount = new AtomicInteger(0);

        Headers.Builder headersBuilder = new Headers.Builder();
        if (referer != null && !referer.isEmpty()) {
            headersBuilder.add("Referer", referer);
        }
        headersBuilder.add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
        Headers headers = headersBuilder.build();

        // 创建临时目录
        File tempDir = new File(outputDir, "temp_" + System.currentTimeMillis());
        if (!tempDir.exists()) {
            tempDir.mkdirs();
        }

        for (int i = 0; i < tsUrls.size(); i++) {
            if (cancelled) break;

            String tsUrl = tsUrls.get(i);
            File tsFile = new File(tempDir, "segment_" + String.format("%04d", i) + ".ts");

            try {
                downloadSingleTs(tsUrl, tsFile, headers);
                tsFiles.add(tsFile);

                int downloaded = downloadedCount.incrementAndGet();
                float progress = (float) downloaded / totalSegments * 100;

                if (callback != null) {
                    App.post(() -> callback.onProgress(progress, downloaded, totalSegments, 0));
                }
            } catch (Exception e) {
                Logger.w("下载TS片段失败: " + tsUrl);
            }
        }
        return tsFiles;
    }

    private void downloadSingleTs(String tsUrl, File tsFile, Headers headers) throws Exception {
        try (Response response = OkHttp.newCall(tsUrl, headers).execute()) {
            if (!response.isSuccessful()) {
                throw new Exception("HTTP " + response.code());
            }
            try (InputStream inputStream = response.body().byteStream();
                 FileOutputStream outputStream = new FileOutputStream(tsFile)) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    if (cancelled) break;
                    outputStream.write(buffer, 0, bytesRead);
                }
            }
        }
    }

    /**
     * 简单拼接方式合并TS片段为MP4
     */
    private File mergeTs2Mp4(List<File> tsFiles, File outputDir, String title) throws Exception {
        String fileName = sanitizeFileName(title) + ".mp4";
        File outputFile = new File(outputDir, fileName);

        Logger.d("简单拼接合并 " + tsFiles.size() + " 个TS片段");

        try (FileOutputStream outputStream = new FileOutputStream(outputFile)) {
            for (File tsFile : tsFiles) {
                if (tsFile.exists() && tsFile.length() > 0) {
                    try (InputStream inputStream = new java.io.FileInputStream(tsFile)) {
                        byte[] buffer = new byte[8192];
                        int bytesRead;
                        while ((bytesRead = inputStream.read(buffer)) != -1) {
                            if (cancelled) break;
                            outputStream.write(buffer, 0, bytesRead);
                        }
                    }
                }
            }
        }

        Logger.d("合并完成，文件大小: " + outputFile.length() + " bytes");
        return outputFile;
    }

    private void cleanupTsFiles(List<File> tsFiles) {
        if (tsFiles == null || tsFiles.isEmpty()) return;

        File tempDir = tsFiles.get(0).getParentFile();
        if (tempDir != null && tempDir.exists()) {
            deleteDirRecursively(tempDir);
        }
    }

    private void deleteDirRecursively(File dir) {
        if (!dir.isDirectory()) {
            dir.delete();
            return;
        }
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    deleteDirRecursively(file);
                } else {
                    file.delete();
                }
            }
        }
        dir.delete();
    }

    private String sanitizeFileName(String fileName) {
        return fileName.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    public void cancel() {
        cancelled = true;
        Logger.d("M3U8下载已取消");
    }

    public void shutdown() {
        cancel();
        if (!executorService.isShutdown()) {
            executorService.shutdown();
        }
    }
}
