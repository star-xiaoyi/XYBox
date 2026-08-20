package com.fongmi.android.tv.utils;

import com.fongmi.android.tv.App;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Logger;
import com.github.catvod.utils.Path;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;

import okhttp3.Response;

public class Download {

    private final File file;
    private final String url;
    private final String fallbackUrl;
    private Callback callback;
    private static final int MAX_RETRY_COUNT = 3; // 最大重试次数

    public static Download create(String url, File file) {
        return create(url, file, null);
    }

    public static Download create(String url, File file, Callback callback) {
        return create(url, file, null, callback);
    }

    public static Download create(String url, File file, String fallbackUrl, Callback callback) {
        return new Download(url, file, fallbackUrl, callback);
    }

    public Download(String url, File file, Callback callback) {
        this(url, file, null, callback);
    }

    public Download(String url, File file, String fallbackUrl, Callback callback) {
        this.url = url;
        this.file = file;
        this.fallbackUrl = fallbackUrl;
        this.callback = callback;
    }

    public void start() {
        if (url == null || url.isEmpty()) {
            if (callback != null) {
                App.post(() -> callback.error("下载URL为空"));
            }
            return;
        }
        if (url.startsWith("file")) return;
        if (file == null) {
            if (callback != null) {
                App.post(() -> callback.error("保存文件路径为空"));
            }
            return;
        }
        if (callback == null) {
            // 无回调时，直接执行（同步）
            doInBackgroundWithFallback();
        } else {
            // 有回调时，异步执行
            App.execute(this::doInBackgroundWithFallback);
        }
    }

    /**
     * 带智能回退的下载方法
     * 先尝试主URL（通常是jsDelivr CDN），失败后回退到备用URL
     */
    private void doInBackgroundWithFallback() {
        // 先尝试主URL
        boolean mainSuccess = doInBackground(url, "主URL");
        if (mainSuccess) {
            return;
        }

        // 主URL失败，如果有回退URL，尝试回退URL
        if (fallbackUrl != null && !fallbackUrl.equals(url)) {
            Logger.d("Download: 主URL下载失败，回退到备用URL: " + fallbackUrl);
            doInBackground(fallbackUrl, "备用URL");
        }
    }

    /**
     * 使用指定URL下载文件（带重试机制）
     */
    private boolean doInBackground(String downloadUrl, String source) {
        Exception lastException = null;

        for (int attempt = 1; attempt <= MAX_RETRY_COUNT; attempt++) {
            try {
                // 这里原本会把进度打回 0：重试一次进度条就退回原点，看着就是"下到 1% 又归零"
                if (callback != null && attempt > 1) {
                    App.post(() -> callback.retry());
                }

                boolean success = downloadWithUrl(downloadUrl, source, attempt);
                if (success) {
                    return true;
                }
            } catch (Exception e) {
                lastException = e;
                Logger.w("Download: 下载失败 (来源: " + source + ", 尝试 " + attempt + "/" + MAX_RETRY_COUNT + "): " + e.getMessage());

                // 如果不是最后一次尝试，等待后重试
                if (attempt < MAX_RETRY_COUNT) {
                    try {
                        long retryDelay = 500L * attempt; // 递增延迟
                        Thread.sleep(retryDelay);
                        Logger.d("Download: 等待 " + retryDelay + "ms 后重试...");
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        // 所有尝试都失败
        if (callback != null && lastException != null) {
            String errorMsg = lastException.getMessage();
            App.post(() -> callback.error(errorMsg != null ? errorMsg : "下载失败"));
        }
        return false;
    }

    /**
     * 使用指定URL下载文件
     */
    private boolean downloadWithUrl(String downloadUrl, String source, int attempt) throws Exception {
        if (downloadUrl == null || downloadUrl.isEmpty()) {
            throw new Exception("下载URL为空");
        }
        if (file == null) {
            throw new Exception("保存文件路径为空");
        }

        Response res = null;
        InputStream inputStream = null;
        try {
            res = OkHttp.newCall(downloadUrl, downloadUrl).execute();

            // 检查HTTP响应状态码
            if (!res.isSuccessful()) {
                throw new Exception("下载失败: HTTP " + res.code() + " " + (res.message() != null ? res.message() : "未知错误"));
            }

            // 检查响应体是否存在
            if (res.body() == null) {
                throw new Exception("下载失败: 响应体为空");
            }

            // 获取输入流
            inputStream = res.body().byteStream();
            if (inputStream == null) {
                throw new Exception("下载失败: 无法获取输入流");
            }

            Path.create(file);

            // 用 body 的长度而不是原始 Content-Length 头：响应被 gzip 压缩时头里是压缩后的字节数，
            // 拿它去校验解压后的文件必然对不上，会误判成"文件损坏"并一直重试。
            long expectedLength = res.body().contentLength();
            if (expectedLength <= 0) expectedLength = -1;

            // 下载文件
            download(inputStream, expectedLength);

            // 验证下载的文件（如果知道预期大小）
            if (expectedLength > 0 && !verifyDownloadedFile(file, expectedLength)) {
                throw new Exception("下载的文件可能已损坏，请重试");
            }

            Logger.d("Download: 下载成功 (来源: " + source + ", 尝试 " + attempt + "/" + MAX_RETRY_COUNT + ")");
            if (callback != null) {
                App.post(() -> callback.success(file));
            }
            return true;
        } catch (Exception e) {
            // 如果下载失败，删除可能不完整的文件
            if (file != null && file.exists()) {
                try {
                    file.delete();
                } catch (Exception ignored) {
                }
            }
            throw e;
        } finally {
            // 关闭输入流
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (Exception ignored) {
                }
            }
            // 关闭响应
            if (res != null) {
                try {
                    res.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    public void cancel() {
        OkHttp.cancel(url);
        if (fallbackUrl != null) {
            OkHttp.cancel(fallbackUrl);
        }
        Path.clear(file);
        callback = null;
    }

    private void download(InputStream is, long length) throws Exception {
        if (is == null) {
            throw new Exception("输入流为空，无法下载");
        }

        try (BufferedInputStream input = new BufferedInputStream(is); FileOutputStream os = new FileOutputStream(file)) {
            byte[] buffer = new byte[8192];
            int readBytes;
            long totalBytes = 0;
            int lastPercent = -1;
            while ((readBytes = input.read(buffer)) != -1) {
                totalBytes += readBytes;
                os.write(buffer, 0, readBytes);
                if (length <= 0 || callback == null) continue;
                // 按整数百分比节流：37MB 的包按 8KB 回调会往主线程丢四千多条消息，
                // 进度条光排队就跟不上，看着像卡住甚至往回跳。
                int percent = Math.min((int) (totalBytes * 100 / length), 100);
                if (percent == lastPercent) continue;
                lastPercent = percent;
                App.post(() -> callback.progress(percent));
            }

            // 不知道文件大小时全程走不确定态，结束补一个 100
            if (length <= 0 && callback != null) {
                App.post(() -> callback.progress(100));
            }
        }
    }

    private boolean verifyDownloadedFile(File file, long expectedLength) {
        try {
            // 如果文件不存在或为空，验证失败
            if (file == null || !file.exists() || file.length() == 0) {
                Logger.e("File verification failed: file does not exist or is empty");
                return false;
            }

            // 如果知道预期大小，检查文件大小是否匹配
            if (expectedLength > 0 && file.length() != expectedLength) {
                Logger.e("File size mismatch: expected " + expectedLength + ", actual " + file.length());
                return false;
            }

            // 检查APK文件头 (ZIP文件头)
            if (file.length() < 4) {
                Logger.e("File too small: " + file.length() + " bytes");
                return false;
            }

            try (FileInputStream fis = new FileInputStream(file)) {
                byte[] header = new byte[4];
                int bytesRead = fis.read(header);
                if (bytesRead < 4) {
                    Logger.e("Cannot read file header");
                    return false;
                }

                // ZIP文件头应该是 0x504B0304 (PK..)
                if (header[0] != 0x50 || header[1] != 0x4B || header[2] != 0x03 || header[3] != 0x04) {
                    Logger.e("Invalid APK file header: " + String.format("%02X %02X %02X %02X", header[0], header[1], header[2], header[3]));
                    return false;
                }
            }

            Logger.d("APK file verification passed: " + file.getName() + " (" + file.length() + " bytes)");
            return true;
        } catch (Exception e) {
            Logger.e("File verification failed: " + e.getMessage());
            Logger.e("Error", e);
            return false;
        }
    }

    public interface Callback {

        void progress(int progress);

        /** 一次尝试失败、即将重试：进度条保持原样，只提示状态 */
        default void retry() {
        }

        void error(String msg);

        void success(File file);
    }
}
