package com.fongmi.android.tv.utils;

import com.fongmi.android.tv.BuildConfig;
import com.github.catvod.utils.Logger;
import com.github.catvod.utils.Path;

import java.io.File;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Removes only updater-owned APKs whose version is already installed or older. */
public final class UpdatePackages {

    private static final Pattern NAME = Pattern.compile(
            "^XYBox-update-([0-9]+)\\.([0-9]+)\\.([0-9]+)(?:-beta([1-9]|[1-8][0-9]|9[0-8]))?\\.apk$");

    private UpdatePackages() {
    }

    public static void cleanInstalled() {
        CleanupResult result = clean(Path.files("updates"), BuildConfig.VERSION_CODE);
        Logger.i("UpdatePackages: removed=" + result.removed + " bytes=" + result.bytes
                + " retained=" + result.retained + " failed=" + result.failed);
    }

    static CleanupResult clean(File directory, int installedCode) {
        CleanupResult result = new CleanupResult();
        if (installedCode <= 0) return result;
        try {
            if (!directory.exists()) return result;
            // Do not follow a replacement directory or file symlink outside files/updates.
            File root = new File(directory.getParentFile().getCanonicalFile(), directory.getName());
            if (!directory.getCanonicalFile().equals(root)) {
                result.failed++;
                return result;
            }
            File[] files = root.listFiles();
            if (files == null) {
                result.failed++;
                return result;
            }
            for (File file : files) {
                long code = versionCode(file.getName());
                if (code < 0 || code > installedCode || !file.isFile()) {
                    result.retained++;
                    continue;
                }
                try {
                    if (!file.getCanonicalFile().equals(file)) {
                        result.retained++;
                        continue;
                    }
                    long bytes = file.length();
                    if (file.delete()) {
                        result.removed++;
                        result.bytes += bytes;
                    } else {
                        result.failed++;
                    }
                } catch (IOException | SecurityException error) {
                    result.failed++;
                }
            }
        } catch (IOException | SecurityException error) {
            result.failed++;
        }
        return result;
    }

    private static long versionCode(String name) {
        Matcher matcher = NAME.matcher(name);
        if (!matcher.matches()) return -1;
        try {
            long major = Long.parseLong(matcher.group(1));
            long minor = Long.parseLong(matcher.group(2));
            long patch = Long.parseLong(matcher.group(3));
            long tail = matcher.group(4) == null ? 99 : Long.parseLong(matcher.group(4));
            // This is the build.sh versionCode rule. Unknown/malformed names are preserved.
            if (major > 99 || minor > 99 || patch > 99 || tail < 1
                    || matcher.group(4) != null && tail > 98) return -1;
            return major * 1_000_000L + minor * 10_000L + patch * 100L + tail;
        } catch (NumberFormatException error) {
            return -1;
        }
    }

    static final class CleanupResult {
        int removed;
        long bytes;
        int retained;
        int failed;
    }
}
