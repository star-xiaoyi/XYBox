package com.fongmi.android.tv.player.exo;

import androidx.media3.database.StandaloneDatabaseProvider;
import androidx.media3.datasource.cache.Cache;
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor;
import androidx.media3.datasource.cache.SimpleCache;

import com.fongmi.android.tv.App;
import com.github.catvod.utils.Path;

public class CacheManager {

    private static final long MAX_CACHE_BYTES = 256L * 1024 * 1024;

    private SimpleCache cache;

    private static class Loader {
        static volatile CacheManager INSTANCE = new CacheManager();
    }

    public static CacheManager get() {
        return Loader.INSTANCE;
    }

    public Cache getCache() {
        if (cache == null) create();
        return cache;
    }

    private void create() {
        cache = new SimpleCache(Path.exo(), new LeastRecentlyUsedCacheEvictor(MAX_CACHE_BYTES), new StandaloneDatabaseProvider(App.get()));
    }
}

