package com.fongmi.android.tv.utils;

import android.content.Context;
import android.os.Build;
import android.os.Process;
import android.util.Log;

import com.fongmi.android.tv.BuildConfig;
import com.github.catvod.utils.Logger;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Persistent, application-owned diagnostic log.
 *
 * The Android log buffer is transient and cannot be reliably collected after a crash or restart.
 * This sink mirrors XYBox's existing Logger calls to private files, rotates them to a fixed bound,
 * and exposes safe snapshots to the in-app log viewer.
 */
public final class AppLog {

    private static final String ACTIVE_NAME = "xybox.log";
    private static final int BACKUP_COUNT = 3;
    private static final long FILE_LIMIT_BYTES = 512L * 1024L;
    private static final int DISPLAY_LIMIT_BYTES = 320 * 1024;
    private static final int DEBUG_MESSAGE_LIMIT_CHARS = 640;
    private static final int CRAWLER_PAYLOAD_THRESHOLD_CHARS = 320;
    private static final int MESSAGE_LIMIT_CHARS = 2048;
    private static final int CRASH_LIMIT_CHARS = 32 * 1024;
    private static final int STACK_FRAME_LIMIT = 24;
    private static final long WAIT_SECONDS = 5L;
    private static final Pattern SECRET_FIELD = Pattern.compile(
            "(?i)(authorization|cookie|set-cookie|password|passwd|pwd|token|secret|api[-_]?key)(\\s*[:=]\\s*)([^\\s&,;]+)");
    private static final Pattern SECRET_QUERY = Pattern.compile(
            "(?i)([?&](?:password|passwd|pwd|token|secret|api[-_]?key)=)[^&#\\s]+" );
    private static final Pattern URL_PASSWORD = Pattern.compile("(://[^/@:\\s]+:)[^/@\\s]+(@)");

    private static final ExecutorService WRITER = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "xybox-log-writer");
        thread.setDaemon(true);
        return thread;
    });

    private static volatile File directory;
    private static volatile boolean installed;

    private AppLog() {
    }

    public static synchronized void install(Context context) {
        if (installed) return;
        directory = new File(context.getFilesDir(), "logs");
        if (!directory.exists() && !directory.mkdirs()) return;
        Logger.setSink(AppLog::enqueue);
        installed = true;
        Logger.i("AppLog: 启动 v" + BuildConfig.VERSION_NAME
                + " pid=" + Process.myPid()
                + " device=" + Build.MANUFACTURER + " " + Build.MODEL
                + " android=" + Build.VERSION.RELEASE + " sdk=" + Build.VERSION.SDK_INT);
    }

    private static void enqueue(int priority, String tag, String message, Throwable throwable) {
        if (!installed || directory == null) return;
        final String entry = format(priority, tag, message, throwable);
        WRITER.execute(() -> append(entry));
    }

    public static void event(String tag, String message) {
        enqueue(Log.INFO, tag, message, null);
        Log.i(tag, message);
    }

    public static void recordCrash(String details) {
        enqueue(Log.ASSERT, "Crash", "未捕获异常\n" + details, null);
        flush();
    }

    public static String readForDisplay() {
        String all = readAll();
        byte[] bytes = all.getBytes(StandardCharsets.UTF_8);
        if (bytes.length <= DISPLAY_LIMIT_BYTES) return all;
        int start = bytes.length - DISPLAY_LIMIT_BYTES;
        while (start < bytes.length && (bytes[start] & 0xC0) == 0x80) start++;
        return "……仅显示最近 320 KB，分享日志可导出全部内容……\n\n"
                + new String(bytes, start, bytes.length - start, StandardCharsets.UTF_8);
    }

    public static String readAll() {
        return callOnWriter(() -> {
            StringBuilder result = new StringBuilder();
            for (File file : orderedLogFiles()) {
                if (!file.isFile() || file.length() == 0) continue;
                if (result.length() > 0) result.append('\n');
                result.append("===== ").append(file.getName()).append(" =====\n");
                result.append(readFile(file));
            }
            return result.toString();
        }, "");
    }

    public static long size() {
        return callOnWriter(() -> {
            long total = 0L;
            for (File file : orderedLogFiles()) if (file.isFile()) total += file.length();
            return total;
        }, 0L);
    }

    public static File createShareFile(Context context) {
        return callOnWriter(() -> {
            File shareDir = new File(context.getCacheDir(), "log-share");
            if (!shareDir.exists() && !shareDir.mkdirs()) return null;
            File target = new File(shareDir, "XYBox-log-"
                    + new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date()) + ".txt");
            String header = "XYBox " + BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ")\n"
                    + Build.MANUFACTURER + " " + Build.MODEL + " · Android " + Build.VERSION.RELEASE
                    + " (SDK " + Build.VERSION.SDK_INT + ")\n"
                    + "导出时间：" + timestamp() + "\n\n";
            try (FileOutputStream output = new FileOutputStream(target)) {
                output.write(header.getBytes(StandardCharsets.UTF_8));
                for (File file : orderedLogFiles()) {
                    if (!file.isFile() || file.length() == 0) continue;
                    output.write(("===== " + file.getName() + " =====\n").getBytes(StandardCharsets.UTF_8));
                    try (FileInputStream input = new FileInputStream(file)) {
                        byte[] buffer = new byte[8192];
                        int count;
                        while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
                    }
                    output.write('\n');
                }
            }
            return target;
        }, null);
    }

    public static boolean clear() {
        return callOnWriter(() -> {
            boolean success = true;
            for (File file : orderedLogFiles()) {
                if (file.exists() && !file.delete()) success = false;
            }
            append(format(Log.INFO, "AppLog", "日志已由用户清空", null));
            return success;
        }, false);
    }

    public static void flush() {
        callOnWriter(() -> true, false);
    }

    private static <T> T callOnWriter(Callable<T> action, T fallback) {
        if (!installed || directory == null) return fallback;
        try {
            Future<T> future = WRITER.submit(action);
            return future.get(WAIT_SECONDS, TimeUnit.SECONDS);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static void append(String entry) {
        File target = activeFile();
        byte[] data = entry.getBytes(StandardCharsets.UTF_8);
        if (target.length() + data.length > FILE_LIMIT_BYTES) {
            rotate();
            target = activeFile();
        }
        try (FileOutputStream output = new FileOutputStream(target, true)) {
            output.write(data);
        } catch (Exception error) {
            Log.e("XYBoxLog", "Unable to persist application log", error);
        }
    }

    private static void rotate() {
        for (int index = BACKUP_COUNT; index >= 1; index--) {
            File source = index == 1 ? activeFile() : backupFile(index - 1);
            File target = backupFile(index);
            if (target.exists() && !target.delete()) continue;
            if (source.exists() && !source.renameTo(target)) {
                Log.w("XYBoxLog", "Unable to rotate " + source.getName());
            }
        }
    }

    private static List<File> orderedLogFiles() {
        if (directory == null) return Collections.emptyList();
        List<File> files = new ArrayList<>();
        for (int index = BACKUP_COUNT; index >= 1; index--) files.add(backupFile(index));
        files.add(activeFile());
        return files;
    }

    private static File activeFile() {
        return new File(directory, ACTIVE_NAME);
    }

    private static File backupFile(int index) {
        return new File(directory, "xybox." + index + ".log");
    }

    private static String readFile(File file) throws Exception {
        try (FileInputStream input = new FileInputStream(file);
             ByteArrayOutputStream output = new ByteArrayOutputStream((int) Math.min(file.length(), Integer.MAX_VALUE))) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            return output.toString(StandardCharsets.UTF_8.name());
        }
    }

    private static String format(int priority, String tag, String message, Throwable throwable) {
        StringBuilder result = new StringBuilder(256);
        String safeMessage = redact(message == null ? "null" : message);
        safeMessage = compactMessage(priority, tag, safeMessage);
        int messageLimit = priority == Log.ASSERT
                ? CRASH_LIMIT_CHARS
                : priority <= Log.DEBUG ? DEBUG_MESSAGE_LIMIT_CHARS : MESSAGE_LIMIT_CHARS;
        safeMessage = abbreviate(safeMessage, messageLimit);
        result.append(timestamp())
                .append(' ').append(level(priority))
                .append('/').append(tag == null ? "XYBox" : tag)
                .append(" [").append(Thread.currentThread().getName()).append("] ")
                .append(safeMessage);
        if (throwable != null) result.append('\n').append(compactThrowable(throwable));
        if (result.length() == 0 || result.charAt(result.length() - 1) != '\n') result.append('\n');
        return result.toString();
    }

    /**
     * Network libraries often contribute dozens of framework frames. Keep the exception root,
     * the first few frames and every XYBox/catvod frame; that is enough to identify both the
     * failure and our call site without burying one playback action under hundreds of lines.
     */
    private static String compactThrowable(Throwable throwable) {
        StringBuilder result = new StringBuilder(2048);
        Throwable current = throwable;
        int causeDepth = 0;
        while (current != null && causeDepth < 6) {
            if (causeDepth > 0) result.append("Caused by: ");
            result.append(redact(current.toString())).append('\n');
            StackTraceElement[] frames = current.getStackTrace();
            int included = 0;
            for (int index = 0; index < frames.length && included < STACK_FRAME_LIMIT; index++) {
                String className = frames[index].getClassName();
                boolean applicationFrame = className.startsWith("com.fongmi.")
                        || className.startsWith("com.github.catvod.");
                if (index < 6 || applicationFrame) {
                    result.append("    at ").append(frames[index]).append('\n');
                    included++;
                }
            }
            if (frames.length > included) {
                result.append("    …省略 ").append(frames.length - included).append(" 个框架调用…\n");
            }
            current = current.getCause();
            causeDepth++;
        }
        return result.toString();
    }

    /**
     * A successful crawler response can contain tens of thousands of characters of poster URLs,
     * filters and episode data. Its full body is noisy, sensitive and already too large to be
     * useful once truncated. Keep a compact response marker; parse failures still reach the log as
     * exceptions with the application call site.
     */
    private static String compactMessage(int priority, String tag, String message) {
        if (priority > Log.DEBUG || !"SpiderDebug".equals(tag)
                || message.length() <= CRAWLER_PAYLOAD_THRESHOLD_CHARS) return message;
        int objectStart = message.indexOf('{');
        int arrayStart = message.indexOf('[');
        int payloadStart;
        if (objectStart < 0) payloadStart = arrayStart;
        else if (arrayStart < 0) payloadStart = objectStart;
        else payloadStart = Math.min(objectStart, arrayStart);
        if (payloadStart < 0 || payloadStart > 80) return message;
        String source = message.substring(0, payloadStart).trim();
        while (source.endsWith(",")) source = source.substring(0, source.length() - 1).trim();
        if (source.length() > 40) source = source.substring(0, 40) + "…";
        return "爬虫响应" + (source.isEmpty() ? "" : "（" + source + "）")
                + "：已返回，正文 " + message.length() + " 字符未写入";
    }

    private static String abbreviate(String value, int limit) {
        if (value.length() <= limit) return value;
        return value.substring(0, limit)
                + "\n……已省略 " + (value.length() - limit) + " 个字符……";
    }

    private static String redact(String raw) {
        String value = SECRET_FIELD.matcher(raw).replaceAll("$1$2***");
        value = SECRET_QUERY.matcher(value).replaceAll("$1***");
        return URL_PASSWORD.matcher(value).replaceAll("$1***$2");
    }

    private static String timestamp() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(new Date());
    }

    private static char level(int priority) {
        switch (priority) {
            case Log.VERBOSE: return 'V';
            case Log.DEBUG: return 'D';
            case Log.INFO: return 'I';
            case Log.WARN: return 'W';
            case Log.ERROR: return 'E';
            case Log.ASSERT: return 'A';
            default: return '?';
        }
    }
}
