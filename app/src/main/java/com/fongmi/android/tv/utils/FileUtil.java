package com.fongmi.android.tv.utils;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.StatFs;
import android.text.TextUtils;

import androidx.core.content.FileProvider;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.impl.Callback;
import com.github.catvod.utils.Logger;
import com.github.catvod.utils.Path;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URLConnection;
import java.text.DecimalFormat;
import java.util.Enumeration;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class FileUtil {

    public static File getWall(int index) {
        return Path.files("wallpaper_" + index);
    }

    public static void openFile(File file) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            
            // 对于APK文件，使用特定的MIME类型
            String mimeType = file.getName().toLowerCase().endsWith(".apk") ? 
                "application/vnd.android.package-archive" : getMimeType(file.getName());
            
            intent.setDataAndType(getShareUri(file), mimeType);
            
            // 添加额外的安装权限检查
            if (file.getName().toLowerCase().endsWith(".apk")) {
                intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            }
            
            App.get().startActivity(intent);
        } catch (Exception e) {
            Logger.e("Failed to open file: " + e.getMessage());
            // 如果失败，尝试使用通用方式
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                intent.setDataAndType(getShareUri(file), "*/*");
                App.get().startActivity(intent);
            } catch (Exception ex) {
                Logger.e("Fallback open file also failed: " + ex.getMessage());
            }
        }
    }

    public static void gzipCompress(File target) {
        byte[] buffer = new byte[1024];
        try (FileInputStream is = new FileInputStream(target); GZIPOutputStream os = new GZIPOutputStream(new FileOutputStream(target.getAbsolutePath() + ".gz"))) {
            int read;
            while ((read = is.read(buffer)) > 0) os.write(buffer, 0, read);
        } catch (IOException e) {
            Logger.e("Error", e);
        } finally {
            Path.clear(target);
        }
    }

    public static void gzipDecompress(File target, File path) {
        byte[] buffer = new byte[1024];
        try (GZIPInputStream is = new GZIPInputStream(new BufferedInputStream(new FileInputStream(target))); BufferedOutputStream os = new BufferedOutputStream(new FileOutputStream(path))) {
            int read;
            while ((read = is.read(buffer)) != -1) os.write(buffer, 0, read);
        } catch (Exception e) {
            Logger.e("Error", e);
        }
    }

    public static void zipDecompress(File target, File path) {
        try (ZipFile zip = new ZipFile(target)) {
            Enumeration<?> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = (ZipEntry) entries.nextElement();
                File out = new File(path, entry.getName());
                if (entry.isDirectory()) out.mkdirs();
                else Path.copy(zip.getInputStream(entry), out);
            }
        } catch (Exception e) {
            Logger.e("Error", e);
        }
    }

    public static void clearCache(Callback callback) {
        App.execute(() -> {
            Path.clear(Path.cache());
            App.post(callback::success);
        });
    }

    public static void getCacheSize(Callback callback) {
        App.execute(() -> {
            String usage = byteCountToDisplaySize(getDirectorySize(Path.cache()));
            App.post(() -> callback.success(usage));
        });
    }

    public static long getDirectorySize(File dir) {
        long size = 0;
        if (dir == null) return 0;
        if (dir.isDirectory()) for (File file: Path.list(dir)) size += getDirectorySize(file);
        else size = dir.length();
        return size;
    }

    public static long getAvailableStorageSpace(File file) {
        try {
            StatFs stat = new StatFs(file.getAbsolutePath());
            return stat.getAvailableBlocksLong() * stat.getBlockSizeLong();
        } catch (Exception e) {
            return 0;
        }
    }

    public static Uri getShareUri(String path) {
        return getShareUri(new File(path.replace("file://", "")));
    }

    public static Uri getShareUri(File file) {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.N ? Uri.fromFile(file) : FileProvider.getUriForFile(App.get(), App.get().getPackageName() + ".provider", file);
    }

    private static String getMimeType(String fileName) {
        String mimeType = URLConnection.guessContentTypeFromName(fileName);
        return TextUtils.isEmpty(mimeType) ? "*/*" : mimeType;
    }

    public static String byteCountToDisplaySize(long size) {
        if (size <= 0) return ResUtil.getString(R.string.none);
        String[] units = new String[]{"bytes", "KB", "MB", "GB", "TB"};
        int digitGroups = (int) (Math.log10(size) / Math.log10(1024));
        return new DecimalFormat("#,##0.#").format(size / Math.pow(1024, digitGroups)) + " " + units[digitGroups];
    }
}
