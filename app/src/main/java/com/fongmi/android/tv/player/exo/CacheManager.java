package com.fongmi.android.tv.player.exo;

import androidx.media3.database.StandaloneDatabaseProvider;
import androidx.media3.datasource.cache.Cache;
import androidx.media3.datasource.cache.NoOpCacheEvictor;
import androidx.media3.datasource.cache.SimpleCache;

import com.fongmi.android.tv.App;
import com.github.catvod.utils.Path;

import java.util.HashSet;

public class CacheManager {

    private SimpleCache cache;

    private static class Loader {
        static volatile CacheManager INSTANCE = new CacheManager();
    }

    public static CacheManager get() {
        return Loader.INSTANCE;
    }

    public synchronized Cache getCache() {
        if (cache == null) create();
        return cache;
    }

    private void create() {
        // 播放页现在会把当前一集主动预取到结尾，因此不能再让 LRU 在任务尚未完成时
        // 淘汰前面已经写好的分片。会话切换/退出负责精确 remove；异常退出留下的内容
        // 在下次首次打开缓存时统一清掉。
        cache = new SimpleCache(Path.exo(), new NoOpCacheEvictor(), new StandaloneDatabaseProvider(App.get()));
        clear();
    }

    public synchronized void clear() {
        if (cache == null) {
            create();
            return;
        }
        for (String key : new HashSet<>(cache.getKeys())) cache.removeResource(key);
    }

    public synchronized long getCacheSpace() {
        return getCache().getCacheSpace();
    }
}

