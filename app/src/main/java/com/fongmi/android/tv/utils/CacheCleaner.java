package com.fongmi.android.tv.utils;
import com.github.catvod.utils.Logger;

import android.os.StatFs;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.impl.Callback;
import com.github.catvod.utils.Path;

/**
 * 缓存自动清理管理器
 */
public class CacheCleaner {

    // 默认缓存清理阈值 200MB
    private static final long DEFAULT_CACHE_THRESHOLD = 200 * 1024 * 1024;
    // 最小保留空间 500MB
    private static final long MIN_FREE_SPACE = 500 * 1024 * 1024;
    // 单例实例
    private static CacheCleaner instance;
    // 缓存清理阈值
    private long cacheThreshold;

    private CacheCleaner() {
        this.cacheThreshold = DEFAULT_CACHE_THRESHOLD;
    }

    public static CacheCleaner get() {
        if (instance == null) {
            synchronized (CacheCleaner.class) {
                if (instance == null) {
                    instance = new CacheCleaner();
                }
            }
        }
        return instance;
    }

    /**
     * 设置缓存阈值
     * @param threshold 阈值大小（字节）
     */
    public void setCacheThreshold(long threshold) {
        this.cacheThreshold = threshold;
    }

    /**
     * 检查缓存，如果超过阈值则清理
     */
    public void checkAndClean() {
        App.execute(() -> {
            try {
                // 获取当前缓存大小
                long cacheSize = FileUtil.getDirectorySize(Path.cache());
                // 获取剩余存储空间
                long freeSpace = getAvailableStorageSpace();
                
                // 如果缓存超过阈值或可用空间低于最小要求，清理缓存
                if (cacheSize > cacheThreshold || freeSpace < MIN_FREE_SPACE) {
                    cleanCache();
                }
            } catch (Exception e) {
                Logger.e("Error", e);
            }
        });
    }

    /**
     * 清理缓存
     */
    private void cleanCache() {
        FileUtil.clearCache(new Callback() {
            @Override
            public void success() {
                // 缓存清理成功
            }
        });
    }

    /**
     * 获取设备可用存储空间
     * @return 可用空间（字节）
     */
    private long getAvailableStorageSpace() {
        try {
            StatFs stat = new StatFs(Path.cache().getPath());
            return stat.getAvailableBlocksLong() * stat.getBlockSizeLong();
        } catch (Exception e) {
            Logger.e("Error", e);
            return 0;
        }
    }
} 
