package com.fongmi.android.tv.utils;

import android.content.Context;

import com.fongmi.android.tv.BuildConfig;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.PriorityQueue;

/** A bounded, read-only inventory for exported logs; never opens file contents or clears data. */
public final class StorageDiagnostics {

    private static final int FILE_LIMIT = 20;
    private static final int DIRECTORY_LIMIT = 60;
    private static final int APK_LIMIT = 100;
    private static final int ENTRY_LIMIT = 100_000;
    private static final int DEPTH_LIMIT = 64;
    private static final long TIME_LIMIT_NANOS = 10_000_000_000L;
    private static final Comparator<Entry> BY_SIZE = Comparator.comparingLong((Entry entry) -> entry.bytes)
            .thenComparing(entry -> entry.path);

    private StorageDiagnostics() {
    }

    public static String collect(Context context) {
        try {
            Inventory inventory = new Inventory();
            inventory.addRoot("internal", context.getDataDir());
            inventory.addRoot("device-protected", context.createDeviceProtectedStorageContext().getDataDir());
            inventory.addRoots("external-files", context.getExternalFilesDirs(null));
            inventory.addRoots("external-cache", context.getExternalCacheDirs());
            inventory.addRoots("external-media", context.getExternalMediaDirs());
            return inventory.report();
        } catch (Exception error) {
            return "===== 存储诊断 =====\n扫描失败：" + error.getClass().getSimpleName() + "\n\n";
        }
    }

    private static final class Inventory {
        private final long started = System.nanoTime();
        private final List<File> roots = new ArrayList<>();
        private final List<Entry> rootSizes = new ArrayList<>();
        private final List<String> warnings = new ArrayList<>();
        private final PriorityQueue<Entry> largestFiles = new PriorityQueue<>(BY_SIZE);
        private final PriorityQueue<Entry> largestDirectories = new PriorityQueue<>(BY_SIZE);
        private final PriorityQueue<Entry> apks = new PriorityQueue<>(BY_SIZE);
        private int visited;
        private int unreadable;
        private int links;
        private int apkCount;
        private int otherVersionApkCount;
        private long apkBytes;
        private long otherVersionApkBytes;
        private boolean truncated;

        private void addRoots(String label, File[] files) {
            if (files == null) return;
            for (int index = 0; index < files.length; index++) addRoot(label + "[" + index + "]", files[index]);
        }

        private void addRoot(String label, File file) {
            if (file == null) {
                warn(label + "：存储不可用");
                return;
            }
            try {
                File canonical = file.getCanonicalFile();
                for (File root : roots) {
                    if (canonical.equals(root) || canonical.getPath().startsWith(root.getPath() + File.separator)) return;
                }
                roots.add(canonical);
                if (!canonical.exists()) {
                    warn(label + "：目录不存在");
                    return;
                }
                rootSizes.add(new Entry(label, scan(canonical, label, "", 0)));
            } catch (IOException | SecurityException error) {
                unreadable++;
                warn(label + "：" + error.getClass().getSimpleName());
            }
        }

        private long scan(File file, String label, String relative, int depth) {
            if (++visited > ENTRY_LIMIT || System.nanoTime() - started >= TIME_LIMIT_NANOS) {
                truncated = true;
                return 0;
            }
            String path = label + (relative.isEmpty() ? "" : "/" + relative);
            try {
                // Resolve the root once, then skip symbolic links, including links within the
                // same root. This prevents cycles, double counting and scanning other apps.
                if (!file.getCanonicalPath().equals(file.getAbsolutePath())) {
                    links++;
                    return 0;
                }
                if (file.isDirectory()) {
                    if (depth >= DEPTH_LIMIT) {
                        truncated = true;
                        warn(path + "：目录层级超过扫描限制");
                        return 0;
                    }
                    File[] children = file.listFiles();
                    if (children == null) {
                        unreadable++;
                        warn(path + "：无法列出目录");
                        return 0;
                    }
                    // Scan the update packages first so even a partial inventory can identify
                    // the suspected accumulation before visiting thousands of browser files.
                    Arrays.sort(children, Comparator.comparingInt((File child) ->
                            child.getName().equals("files") || child.getName().equals("updates") ? 0 : 1)
                            .thenComparing(File::getName));
                    long bytes = 0;
                    for (File child : children) {
                        String childPath = relative.isEmpty() ? child.getName() : relative + "/" + child.getName();
                        bytes += scan(child, label, childPath, depth + 1);
                        if (truncated && (visited > ENTRY_LIMIT || System.nanoTime() - started >= TIME_LIMIT_NANOS)) break;
                    }
                    if (depth > 0 && depth <= 2) keep(largestDirectories, new Entry(path, bytes), DIRECTORY_LIMIT);
                    return bytes;
                }
                if (!file.isFile()) {
                    unreadable++;
                    warn(path + "：文件已消失或不是普通文件");
                    return 0;
                }
                long bytes = file.length();
                keep(largestFiles, new Entry(path, bytes), FILE_LIMIT);
                if ((label.equals("internal") || label.equals("device-protected"))
                        && relative.startsWith("files/updates/")
                        && file.getName().toLowerCase(Locale.ROOT).endsWith(".apk")) {
                    apkCount++;
                    apkBytes += bytes;
                    if (!file.getName().equals("XYBox-update-" + BuildConfig.VERSION_NAME + ".apk")) {
                        otherVersionApkCount++;
                        otherVersionApkBytes += bytes;
                    }
                    keep(apks, new Entry(path, bytes), APK_LIMIT);
                }
                return bytes;
            } catch (IOException | SecurityException error) {
                unreadable++;
                warn(path + "：" + error.getClass().getSimpleName());
                return 0;
            }
        }

        private void warn(String warning) {
            if (warnings.size() < 10) warnings.add(warning);
        }

        private String report() {
            StringBuilder text = new StringBuilder(8192);
            text.append("===== 存储诊断 =====\n")
                    .append("只读取目录和文件长度，未读取文件内容，未清理任何数据。\n")
                    .append("单位：MiB（1024×1024 字节），同时列出原始字节数；系统分配空间及统计刷新可能产生差异。\n")
                    .append("目录名使用相对路径；internal 包含 files、databases、cache、code_cache、app_webview 等实际存在的目录。\n")
                    .append("扫描状态：").append(truncated || unreadable > 0 ? "不完整，以下大小仅为已扫描部分" : "完成")
                    .append("；条目 ").append(visited).append("；无法读取 ").append(unreadable)
                    .append("；跳过符号链接 ").append(links)
                    .append("；用时 ").append((System.nanoTime() - started) / 1_000_000L).append(" ms\n")
                    .append("限制：10 秒 / 100000 条目 / 64 层目录，扫描期间文件可能变化。\n\n")
                    .append("[各存储根目录]\n");
            long total = 0;
            for (Entry entry : rootSizes) {
                line(text, entry);
                total += entry.bytes;
            }
            text.append("已扫描文件合计：").append(size(total)).append("\n\n")
                    .append("[目录占用，最多 60 项，展示到第二层]\n")
                    .append("父目录已包含子目录，不能将各行重复相加。\n");
            appendEntries(text, largestDirectories);
            text.append("\n[应用内更新安装包 files/updates]\n")
                    .append("APK 数量：").append(apkCount).append("；合计 ").append(size(apkBytes)).append('\n')
                    .append("文件名与当前版本不同的 APK：").append(otherVersionApkCount)
                    .append("；合计 ").append(size(otherVersionApkBytes)).append('\n')
                    .append("该目录可包含完整安装包及下载中断的半包；大小统计不校验 APK 内容。\n");
            appendEntries(text, apks);
            if (apkCount > APK_LIMIT) text.append("仅列出最大的 100 个 APK，数量及合计包含其余已扫描 APK。\n");
            text.append("\n[最大文件，最多 20 项]\n");
            appendEntries(text, largestFiles);
            if (!warnings.isEmpty()) {
                text.append("\n[扫描提示，最多 10 项]\n");
                for (String warning : warnings) text.append(safePath(warning)).append('\n');
            }
            return text.append("\n===== 存储诊断结束 =====\n\n").toString();
        }
    }

    private static void keep(PriorityQueue<Entry> queue, Entry entry, int limit) {
        queue.add(entry);
        if (queue.size() > limit) queue.poll();
    }

    private static void appendEntries(StringBuilder text, PriorityQueue<Entry> queue) {
        if (queue.isEmpty()) {
            text.append("无已扫描条目\n");
            return;
        }
        List<Entry> entries = new ArrayList<>(queue);
        entries.sort(BY_SIZE.reversed());
        for (Entry entry : entries) line(text, entry);
    }

    private static void line(StringBuilder text, Entry entry) {
        text.append(size(entry.bytes)).append("  ").append(safePath(entry.path)).append('\n');
    }

    private static String size(long bytes) {
        return String.format(Locale.ROOT, "%.2f MiB (%d bytes)", bytes / 1048576.0, bytes);
    }

    private static String safePath(String path) {
        return path.replace('\n', '_').replace('\r', '_').replace('\t', '_');
    }

    private static final class Entry {
        private final String path;
        private final long bytes;

        private Entry(String path, long bytes) {
            this.path = path;
            this.bytes = bytes;
        }
    }
}
