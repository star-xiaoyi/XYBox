package com.fongmi.android.tv;
import com.github.catvod.utils.Logger;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.os.HandlerCompat;

import com.fongmi.android.tv.Setting;
// import com.fongmi.android.tv.event.EventIndex; // 暂时注释，如果不存在则删除
import com.fongmi.android.tv.ui.activity.CrashActivity;
import com.fongmi.android.tv.utils.CacheCleaner;
import com.fongmi.android.tv.utils.UpdateInstaller;
import com.fongmi.android.tv.utils.AutoSyncManager;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.hook.Hook;
import com.github.catvod.Init;
import com.github.catvod.bean.Doh;
import com.github.catvod.net.OkHttp;
import com.google.gson.Gson;


import org.greenrobot.eventbus.EventBus;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import cat.ereza.customactivityoncrash.config.CaocConfig;

public class App extends Application {

    private final ExecutorService executor;
    private final Handler handler;
    private static App instance;
    private Activity activity;
    private final Gson gson;
    private final long time;
    private Hook hook;
    private final Runnable cleanTask;
    private final Runnable syncTask;
    private boolean appJustLaunched;

    public App() {
        instance = this;
        // 根据CPU核数动态配置线程池，提升性能
        int cpuCores = Runtime.getRuntime().availableProcessors();
        int corePoolSize = Math.max(2, cpuCores);
        int maxPoolSize = corePoolSize * 2;
        executor = new ThreadPoolExecutor(
            corePoolSize,
            maxPoolSize,
            60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<Runnable>()
        );
        handler = HandlerCompat.createAsync(Looper.getMainLooper());
        time = System.currentTimeMillis();
        gson = new com.google.gson.GsonBuilder()
                .disableHtmlEscaping()
                .create();
        cleanTask = this::checkCacheClean;
        syncTask = this::doAutoSync;
        appJustLaunched = true;
    }

    public static App get() {
        return instance;
    }

    public static Gson gson() {
        return get().gson;
    }

    public static long time() {
        return get().time;
    }

    public static Activity activity() {
        return get().activity;
    }
    
    public static boolean isAppJustLaunched() {
        return get().appJustLaunched;
    }
    
    public static void setAppLaunched() {
        get().appJustLaunched = false;
    }

    public static void execute(Runnable runnable) {
        get().executor.execute(runnable);
    }

    public static void post(Runnable runnable) {
        get().handler.post(runnable);
    }

    public static void post(Runnable runnable, long delayMillis) {
        get().handler.removeCallbacks(runnable);
        if (delayMillis >= 0) get().handler.postDelayed(runnable, delayMillis);
    }

    public static void removeCallbacks(Runnable runnable) {
        get().handler.removeCallbacks(runnable);
    }

    public static void removeCallbacks(Runnable... runnable) {
        for (Runnable r : runnable) get().handler.removeCallbacks(r);
    }

    public void setHook(Hook hook) {
        this.hook = hook;
    }

    private void setActivity(Activity activity) {
        this.activity = activity;
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        Init.set(base);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        OkHttp.get().setProxy(Setting.getProxy());
        OkHttp.get().setDoh(Doh.objectFrom(Setting.getDoh()));
        // EventBus.builder().addIndex(new EventIndex()).installDefaultEventBus(); // 暂时注释，如果EventIndex不存在则删除
        EventBus.getDefault(); // 使用默认EventBus
        CaocConfig.Builder.create().backgroundMode(CaocConfig.BACKGROUND_MODE_SILENT).errorActivity(CrashActivity.class).apply();
        // Ensure default notification channel exists for foreground playback service (TV flavor too)
        Notify.createChannel();
        
        // 初始化自动缓存清理
        initCacheCleaner();
        
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override
            public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {
                if (activity != activity()) setActivity(activity);
            }

            @Override
            public void onActivityStarted(@NonNull Activity activity) {
                if (activity != activity()) setActivity(activity);
            }

            @Override
            public void onActivityResumed(@NonNull Activity activity) {
                if (activity != activity()) setActivity(activity);
                // 应用回到前台时检查缓存
                checkCacheClean();
                // 检查是否有待安装的更新文件（用户从设置页面返回后）
                checkPendingInstall();
                // 检查局域网自动同步
                checkAutoSync();
                // 自动检查更新（如果启用）
                checkAutoUpdate(activity);
            }

            @Override
            public void onActivityPaused(@NonNull Activity activity) {
                if (activity == activity()) setActivity(null);
            }

            @Override
            public void onActivityStopped(@NonNull Activity activity) {
                if (activity == activity()) setActivity(null);
            }

            @Override
            public void onActivityDestroyed(@NonNull Activity activity) {
                if (activity == activity()) setActivity(null);
            }

            @Override
            public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {
            }
        });
    }

    private void initCacheCleaner() {
        CacheCleaner cleaner = CacheCleaner.get();
        cleaner.setCacheThreshold(200 * 1024 * 1024); // 固定使用200MB阈值
        
        // 定期检查缓存 (每30分钟)
        post(cleanTask, 30 * 60 * 1000);
    }
    
    private void checkCacheClean() {
        CacheCleaner.get().checkAndClean();
        // 每30分钟定期检查缓存
        post(cleanTask, 30 * 60 * 1000);
    }
    
    /**
     * 检查是否有待安装的更新文件
     * 当用户从设置页面授予安装权限后返回时，自动安装
     */
    private void checkPendingInstall() {
        UpdateInstaller installer = UpdateInstaller.get();
        if (installer.hasPendingInstall()) {
            Logger.d("App: 检测到待安装文件且权限已授予，自动安装");
            boolean success = installer.autoRetryInstall();
            if (success) {
                Notify.show("正在安装更新...");
            } else {
                Logger.e("App: 自动安装失败");
            }
        }
    }
    
    /**
     * 自动检查更新（如果启用）
     */
    private void checkAutoUpdate(Activity activity) {
        // 检查是否启用自动更新检查
        if (!Setting.getAutoUpdateCheck()) {
            return;
        }
        
        // 检查是否启用更新功能
        if (!Setting.getUpdate()) {
            return;
        }
        
        // 延迟一小段时间，避免影响应用启动速度
        post(() -> {
            if (activity != null && !activity.isFinishing() && !activity.isDestroyed()) {
                Logger.d("App: 开始自动检查更新");
                Updater.create().auto().release().start(activity);
            }
        }, 2000); // 延迟2秒
    }
    
    /**
     * 检查并执行局域网自动同步
     */
    private void checkAutoSync() {
        AutoSyncManager manager = AutoSyncManager.get();
        
        if (!manager.isAutoSyncEnabled()) {
            Logger.d("App: 局域网自动同步未启用");
            return;
        }
        
        // 应用启动时立即执行一次同步
        Logger.d("App: 局域网自动同步已启用，准备执行同步");
        execute(() -> manager.performAutoSync());
        
        // 设置定期同步
        int interval = manager.getSyncInterval();
        Logger.d("App: 设置定期同步，间隔: " + interval + " 分钟");
        post(syncTask, interval * 60 * 1000L);
    }
    
    /**
     * 执行定期自动同步
     */
    private void doAutoSync() {
        execute(() -> {
            AutoSyncManager manager = AutoSyncManager.get();
            if (manager.isAutoSyncEnabled()) {
                Logger.d("App: 开始定期自动同步");
                manager.performAutoSync();
                
                // 设置下次同步
                int interval = manager.getSyncInterval();
                post(syncTask, interval * 60 * 1000L);
            }
        });
    }
    

    @Override
    public PackageManager getPackageManager() {
        return hook != null ? hook : getBaseContext().getPackageManager();
    }

    @Override
    public String getPackageName() {
        return hook != null ? hook.getPackageName() : getBaseContext().getPackageName();
    }
}
