package com.fongmi.android.tv.utils;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.text.TextUtils;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.Setting;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Backup;
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.bean.History;
import com.fongmi.android.tv.bean.Keep;
import com.fongmi.android.tv.db.AppDatabase;
import com.fongmi.android.tv.event.RefreshEvent;
import com.fongmi.android.tv.impl.Callback;
import com.github.catvod.utils.Logger;
import com.github.catvod.utils.Prefers;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.ToNumberPolicy;
import com.google.gson.reflect.TypeToken;
import com.thegrizzlylabs.sardineandroid.DavResource;
import com.thegrizzlylabs.sardineandroid.Sardine;
import com.thegrizzlylabs.sardineandroid.impl.OkHttpSardine;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Persistent WebDAV synchronization.
 *
 * The remote file is a versioned snapshot. Every sync downloads first, merges
 * records by update time, applies the merged snapshot locally, and only then
 * replaces the remote file through a temporary WebDAV resource.
 */
public final class WebDAVSyncManager {

    private static final int SCHEMA_VERSION = 3;
    private static final int MAX_ATTEMPTS = 3;
    private static final long ACTIVE_SYNC_DELAY = 5L * 60 * 1000;
    private static final long TOMBSTONE_RETENTION = 120L * 24 * 60 * 60 * 1000;
    private static final String SYNC_FILE = "xybox_sync_v2.json";
    private static final String BACKUP_FILE = "xybox_sync_v2.backup.json";
    private static final String LEGACY_HISTORY_FILE = "xmbox_history.json";
    private static final String LEGACY_SETTINGS_FILE = "xmbox_settings.json";
    private static final String LEGACY_BACKUP_FILE = "xmbox_backup.json";
    private static final String PREF_DEVICE_ID = "webdav_device_id_v2";
    private static final String PREF_TOMBSTONES = "webdav_tombstones_v2";
    private static final String PREF_SETTINGS_HASH = "webdav_settings_hash_v2";
    private static final String PREF_SETTINGS_TIME = "webdav_settings_time_v2";
    private static final String PREF_LAST_SUCCESS = "webdav_last_success_v2";
    private static final String PREF_LAST_ATTEMPT = "webdav_last_attempt_v2";
    private static final String PREF_LAST_STATUS = "webdav_last_status_v2";
    private static final String HISTORY_PREFIX = "history:";
    private static final String KEEP_PREFIX = "keep:";
    private static final String CONFIG_PREFIX = "config:";

    private static final Gson SYNC_GSON = new GsonBuilder()
            .disableHtmlEscaping()
            .setObjectToNumberStrategy(ToNumberPolicy.LAZILY_PARSED_NUMBER)
            .create();

    private static final Set<String> SETTINGS_WHITELIST = new HashSet<>(Arrays.asList(
            "wall", "decode", "player_engine", "render", "quality", "size", "viewType",
            "scale", "scale_live", "buffer", "background", "site_mode", "boot_live",
            "invert", "across", "change", "caption", "tunnel", "audio_prefer", "prefer_aac",
            "danmaku_load", "danmaku_size", "danmaku_show", "zhuyin", "speed",
            "subtitle_text_size", "subtitle_position", "gesture_double_tap_play",
            "gesture_double_tap_seek", "gesture_seek_seconds", "gesture_brightness",
            "gesture_volume", "gesture_progress", "live_tab_visible", "history_visible",
            "ai_ad_block", "config_0", "config_1"
    ));

    private static final Set<String> FLOAT_SETTINGS = new HashSet<>(Arrays.asList(
            "danmaku_size", "speed", "subtitle_text_size", "subtitle_position"
    ));

    private static volatile WebDAVSyncManager instance;
    private volatile boolean syncing;
    private Sardine sardine;
    private String baseUrl;
    private String username;
    private String password;
    private long dirtyGeneration;
    private boolean dirtySyncScheduled;
    private boolean flushAfterSync;
    private final Runnable dirtySyncTask = this::dispatchDirtySync;

    public static WebDAVSyncManager get() {
        if (instance == null) {
            synchronized (WebDAVSyncManager.class) {
                if (instance == null) instance = new WebDAVSyncManager();
            }
        }
        return instance;
    }

    private WebDAVSyncManager() {
        loadConfig();
    }

    private synchronized void loadConfig() {
        baseUrl = normalizeBaseUrl(Setting.getWebDAVUrl());
        username = Setting.getWebDAVUsername();
        password = Setting.getWebDAVPassword();
        sardine = null;
        if (!TextUtils.isEmpty(baseUrl) && !TextUtils.isEmpty(username) && !TextUtils.isEmpty(password)) {
            Sardine client = new OkHttpSardine();
            client.setCredentials(username, password);
            sardine = client;
        }
    }

    private String normalizeBaseUrl(String value) {
        String url = value == null ? "" : value.trim();
        return TextUtils.isEmpty(url) || url.endsWith("/") ? url : url + "/";
    }

    public synchronized void reloadConfig() {
        loadConfig();
    }

    public boolean isConfigured() {
        return sardine != null && !TextUtils.isEmpty(baseUrl)
                && !TextUtils.isEmpty(username) && !TextUtils.isEmpty(password);
    }

    /** Kept for compatibility with older screens; v2 uses authenticated WebDAV only. */
    public static String generateSyncCode() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
    }

    public TestResult testConnectionWithMessage() {
        reloadConfig();
        if (!isConfigured()) return new TestResult(false, "WebDAV未配置，请检查地址、用户名和应用密码");
        if (!isNetworkAvailable()) return new TestResult(false, "当前网络不可用");
        try {
            ensureDirectory();
            String probeName = ".xybox-write-test-" + getDeviceId();
            String probeUrl = fileUrl(probeName);
            sardine.put(probeUrl, "{}".getBytes(StandardCharsets.UTF_8), "application/json; charset=utf-8");
            sardine.delete(probeUrl);
            return new TestResult(true, "连接成功，可以读写同步目录");
        } catch (Exception e) {
            return new TestResult(false, friendlyError(e));
        }
    }

    public boolean testConnection() {
        return testConnectionWithMessage().success;
    }

    public SyncResult syncNow() {
        final long generationAtStart;
        synchronized (this) {
            if (syncing) return new SyncResult(false, "同步正在进行中，请稍候", 0, 0);
            syncing = true;
            flushAfterSync = false;
            generationAtStart = dirtyGeneration;
        }
        putLong(PREF_LAST_ATTEMPT, System.currentTimeMillis());
        try {
            reloadConfig();
            if (!isConfigured()) return finish(false, "WebDAV尚未配置", 0, 0);
            if (!isNetworkAvailable()) return finish(false, "网络不可用，联网后会自动重试", 0, 0);
            ensureDirectory();

            Exception lastError = null;
            for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
                try {
                    RemoteSnapshot remote = downloadSnapshot();
                    SyncEnvelope local = captureLocal(remote);
                    SyncEnvelope merged = merge(remote.envelope, local);
                    SyncDelta downloaded = diff(local, merged);
                    SyncDelta uploaded = diff(remote.envelope, merged);
                    boolean needsUpload = !remote.exists || remote.legacyImported || uploaded.hasChanges();
                    if (needsUpload) {
                        uploadSafely(remote, merged);
                    } else {
                        merged.revision = remote.envelope.revision;
                        merged.deviceId = remote.envelope.deviceId;
                        merged.updatedAt = remote.envelope.updatedAt;
                    }
                    applyLocally(merged);
                    rememberSuccessfulState(merged);
                    markGenerationSynced(generationAtStart);
                    String legacy = remote.legacyImported ? "，并已导入旧版数据" : "";
                    return finish(true, buildSyncMessage(downloaded, needsUpload ? uploaded : new SyncDelta()) + legacy,
                            merged.histories.size(), merged.keeps.size());
                } catch (SyncConflictException e) {
                    lastError = e;
                    Logger.w("WebDAV: 云端数据已变化，第 " + (attempt + 1) + " 次重新合并");
                } catch (Exception e) {
                    lastError = e;
                    Logger.e("WebDAV: 第 " + (attempt + 1) + " 次同步失败: " + e.getMessage());
                }
                if (attempt + 1 < MAX_ATTEMPTS) {
                    try {
                        Thread.sleep(250L * (attempt + 1) * (attempt + 1));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
            return finish(false, friendlyError(lastError), 0, 0);
        } catch (Exception e) {
            return finish(false, friendlyError(e), 0, 0);
        } finally {
            boolean flush;
            synchronized (this) {
                syncing = false;
                flush = flushAfterSync && dirtyGeneration != 0;
                flushAfterSync = false;
            }
            if (flush) App.execute(this::syncNow);
        }
    }

    public void performAutoSync() {
        if (!Setting.isWebDAVAutoSync() || !isConfigured()) return;
        if (System.currentTimeMillis() - getLong(PREF_LAST_SUCCESS) < ACTIVE_SYNC_DELAY) return;
        syncNow();
    }

    private void markGenerationSynced(long generation) {
        synchronized (this) {
            if (generation != dirtyGeneration) return;
            dirtyGeneration = 0;
            dirtySyncScheduled = false;
        }
        App.removeCallbacks(dirtySyncTask);
        WebDAVSyncJobService.cancel();
    }

    public void requestSync() {
        if (!Setting.isWebDAVAutoSync() || !isConfigured()) return;
        synchronized (this) {
            dirtyGeneration++;
            WebDAVSyncJobService.schedule();
            if (dirtySyncScheduled) return;
            dirtySyncScheduled = true;
        }
        App.post(dirtySyncTask, ACTIVE_SYNC_DELAY);
    }

    public void flushPendingSync() {
        synchronized (this) {
            if (dirtyGeneration == 0) return;
            dirtySyncScheduled = false;
            if (syncing) flushAfterSync = true;
        }
        App.removeCallbacks(dirtySyncTask);
        WebDAVSyncJobService.scheduleImmediate();
    }

    private void dispatchDirtySync() {
        synchronized (this) {
            dirtySyncScheduled = false;
            if (dirtyGeneration == 0) return;
        }
        App.execute(this::syncNow);
    }

    public long getAutoSyncIntervalMillis() {
        return ACTIVE_SYNC_DELAY;
    }

    public String getLastStatus() {
        return Prefers.getString(PREF_LAST_STATUS, "尚未同步");
    }

    public void markHistoryDeleted(History history) {
        if (history == null || TextUtils.isEmpty(history.getKey())) return;
        markDeleted(HISTORY_PREFIX + history.getKey());
    }

    public void markKeepDeleted(Keep keep) {
        if (keep == null || TextUtils.isEmpty(keep.getKey())) return;
        markDeleted(KEEP_PREFIX + keep.getKey());
    }

    public void markConfigDeleted(Config config) {
        if (config == null || TextUtils.isEmpty(config.getUrl()) || config.getType() > 1) return;
        markDeleted(CONFIG_PREFIX + configKey(config));
    }

    public void markHistoriesDeleted(List<History> histories) {
        if (histories == null || histories.isEmpty()) return;
        Map<String, Long> tombstones = loadLocalTombstones();
        long now = System.currentTimeMillis();
        for (History item : histories) if (item != null && !TextUtils.isEmpty(item.getKey())) {
            tombstones.put(HISTORY_PREFIX + item.getKey(), now);
        }
        saveLocalTombstones(tombstones);
        requestSync();
    }

    public void markKeepsDeleted(List<Keep> keeps) {
        if (keeps == null || keeps.isEmpty()) return;
        Map<String, Long> tombstones = loadLocalTombstones();
        long now = System.currentTimeMillis();
        for (Keep item : keeps) if (item != null && !TextUtils.isEmpty(item.getKey())) {
            tombstones.put(KEEP_PREFIX + item.getKey(), now);
        }
        saveLocalTombstones(tombstones);
        requestSync();
    }

    private synchronized void markDeleted(String key) {
        Map<String, Long> tombstones = loadLocalTombstones();
        tombstones.put(key, System.currentTimeMillis());
        saveLocalTombstones(tombstones);
        requestSync();
    }

    private RemoteSnapshot downloadSnapshot() throws Exception {
        String url = fileUrl(SYNC_FILE);
        if (remoteExists(SYNC_FILE)) {
            SyncEnvelope envelope = SYNC_GSON.fromJson(readText(url), SyncEnvelope.class);
            if (envelope == null || envelope.schemaVersion > SCHEMA_VERSION) {
                throw new IllegalStateException("云端同步文件版本不受支持");
            }
            normalize(envelope);
            return new RemoteSnapshot(true, false, getEtag(SYNC_FILE), envelope);
        }

        SyncEnvelope legacy = new SyncEnvelope();
        boolean imported = importLegacy(legacy);
        normalize(legacy);
        return new RemoteSnapshot(false, imported, null, legacy);
    }

    private boolean importLegacy(SyncEnvelope target) {
        boolean imported = importLegacyFrom(baseUrl, target);
        String oldBrandUrl = legacyBrandBaseUrl();
        if (!imported && oldBrandUrl != null) imported = importLegacyFrom(oldBrandUrl, target);
        return imported;
    }

    private boolean importLegacyFrom(String folderUrl, SyncEnvelope target) {
        boolean imported = false;
        try {
            String historyUrl = folderUrl + LEGACY_HISTORY_FILE;
            if (remoteExists(folderUrl, LEGACY_HISTORY_FILE)) {
                Type type = new TypeToken<List<History>>() {}.getType();
                List<History> histories = SYNC_GSON.fromJson(readText(historyUrl), type);
                if (histories != null) target.histories.addAll(histories);
                imported = true;
            }
        } catch (Exception e) {
            Logger.w("WebDAV: 旧历史文件导入失败: " + e.getMessage());
        }
        try {
            String settingsUrl = folderUrl + LEGACY_SETTINGS_FILE;
            if (remoteExists(folderUrl, LEGACY_SETTINGS_FILE)) {
                Type type = new TypeToken<Map<String, Object>>() {}.getType();
                Map<String, Object> settings = SYNC_GSON.fromJson(readText(settingsUrl), type);
                target.settings.putAll(filterSettings(settings));
                target.settingsUpdatedAt = System.currentTimeMillis();
                imported = true;
            }
        } catch (Exception e) {
            Logger.w("WebDAV: 旧设置文件导入失败: " + e.getMessage());
        }
        try {
            String backupUrl = folderUrl + LEGACY_BACKUP_FILE;
            if (remoteExists(folderUrl, LEGACY_BACKUP_FILE)) {
                Backup backup = Backup.objectFrom(readText(backupUrl));
                target.histories.addAll(backup.getHistory());
                target.keeps.addAll(backup.getKeep());
                if (target.settings.isEmpty()) target.settings.putAll(filterSettings(backup.getPrefers()));
                imported = true;
            }
        } catch (Exception e) {
            Logger.w("WebDAV: 旧备份文件导入失败: " + e.getMessage());
        }
        return imported;
    }

    private String legacyBrandBaseUrl() {
        String lower = baseUrl.toLowerCase();
        String suffix = "/xybox/";
        int index = lower.lastIndexOf(suffix);
        if (index < 0 || index + suffix.length() != lower.length()) return null;
        return baseUrl.substring(0, index) + "/XMBOX/";
    }

    private SyncEnvelope captureLocal(RemoteSnapshot remote) {
        SyncEnvelope local = new SyncEnvelope();
        local.deviceId = getDeviceId();
        local.updatedAt = System.currentTimeMillis();
        local.histories.addAll(AppDatabase.get().getHistoryDao().findAll());
        local.keeps.addAll(AppDatabase.get().getKeepDao().findAll());
        for (Config config : AppDatabase.get().getConfigDao().findAll()) {
            if (config.getType() <= 1 && !TextUtils.isEmpty(config.getUrl())) {
                local.configs.add(copyConfigForSync(config));
            }
        }
        local.tombstones.putAll(loadLocalTombstones());
        local.settings.putAll(collectSettings());
        local.settingsUpdatedAt = resolveLocalSettingsTime(local.settings, !remote.envelope.settings.isEmpty());
        return local;
    }

    private SyncEnvelope merge(SyncEnvelope remote, SyncEnvelope local) {
        SyncEnvelope merged = new SyncEnvelope();
        merged.revision = Math.max(remote.revision, local.revision) + 1;
        merged.deviceId = local.deviceId;
        merged.updatedAt = System.currentTimeMillis();
        mergeTombstones(merged.tombstones, remote.tombstones);
        mergeTombstones(merged.tombstones, local.tombstones);

        Map<String, Config> configs = new LinkedHashMap<>();
        mergeConfigs(configs, remote.configs);
        mergeConfigs(configs, local.configs);
        for (Map.Entry<String, Config> entry : configs.entrySet()) {
            long deletedAt = merged.tombstones.getOrDefault(CONFIG_PREFIX + entry.getKey(), 0L);
            if (deletedAt == 0 || deletedAt < entry.getValue().getTime()) merged.configs.add(entry.getValue());
        }
        normalizeConfigIds(merged.configs);
        remapEnvelopeConfigIds(remote, merged.configs);
        remapEnvelopeConfigIds(local, merged.configs);

        Map<String, History> histories = new LinkedHashMap<>();
        mergeHistories(histories, remote.histories);
        mergeHistories(histories, local.histories);
        for (Map.Entry<String, History> entry : histories.entrySet()) {
            long deletedAt = merged.tombstones.getOrDefault(HISTORY_PREFIX + entry.getKey(), 0L);
            if (deletedAt < entry.getValue().getCreateTime()) merged.histories.add(entry.getValue());
        }

        Map<String, Keep> keeps = new LinkedHashMap<>();
        mergeKeeps(keeps, remote.keeps);
        mergeKeeps(keeps, local.keeps);
        for (Map.Entry<String, Keep> entry : keeps.entrySet()) {
            long deletedAt = merged.tombstones.getOrDefault(KEEP_PREFIX + entry.getKey(), 0L);
            if (deletedAt < entry.getValue().getCreateTime()) merged.keeps.add(entry.getValue());
        }

        if (remote.settingsUpdatedAt > local.settingsUpdatedAt) {
            merged.settings.putAll(remote.settings);
            merged.settingsUpdatedAt = remote.settingsUpdatedAt;
        } else {
            merged.settings.putAll(local.settings);
            merged.settingsUpdatedAt = local.settingsUpdatedAt;
        }
        pruneTombstones(merged.tombstones);
        return merged;
    }

    private void mergeHistories(Map<String, History> output, List<History> input) {
        if (input == null) return;
        for (History candidate : input) {
            if (candidate == null || TextUtils.isEmpty(candidate.getKey())) continue;
            History current = output.get(candidate.getKey());
            if (current == null || candidate.getCreateTime() > current.getCreateTime()
                    || candidate.getCreateTime() == current.getCreateTime()
                    && candidate.getPosition() > current.getPosition()) {
                output.put(candidate.getKey(), candidate);
            }
        }
    }

    private void mergeKeeps(Map<String, Keep> output, List<Keep> input) {
        if (input == null) return;
        for (Keep candidate : input) {
            if (candidate == null || TextUtils.isEmpty(candidate.getKey())) continue;
            Keep current = output.get(candidate.getKey());
            if (current == null || candidate.getCreateTime() > current.getCreateTime()) {
                output.put(candidate.getKey(), candidate);
            }
        }
    }

    private void mergeConfigs(Map<String, Config> output, List<Config> input) {
        if (input == null) return;
        for (Config candidate : input) {
            if (candidate == null || TextUtils.isEmpty(candidate.getUrl()) || candidate.getType() > 1) continue;
            String key = configKey(candidate);
            Config current = output.get(key);
            if (current == null) {
                output.put(key, candidate);
            } else if (candidate.getTime() > current.getTime()) {
                Config winner = copyConfigForSync(candidate);
                winner.setId(current.getId());
                output.put(key, winner);
            }
        }
    }

    private Config copyConfigForSync(Config source) {
        Config copy = new Config();
        copy.setId(source.getId());
        copy.setType(source.getType());
        // Time also determines the selected source. Diffs intentionally ignore time-only changes.
        copy.setTime(source.getTime());
        copy.setUrl(source.getUrl());
        copy.setName(source.getName());
        return copy;
    }

    private String configKey(Config config) {
        return config.getType() + ":" + config.getUrl();
    }

    private void normalizeConfigIds(List<Config> configs) {
        Set<Integer> used = new HashSet<>();
        int nextId = 1;
        for (Config config : configs) nextId = Math.max(nextId, config.getId() + 1);
        for (Config config : configs) {
            if (config.getId() > 0 && used.add(config.getId())) continue;
            while (used.contains(nextId)) nextId++;
            config.setId(nextId);
            used.add(nextId++);
        }
    }

    private void remapEnvelopeConfigIds(SyncEnvelope envelope, List<Config> canonicalConfigs) {
        Map<String, Integer> canonicalIds = new HashMap<>();
        for (Config config : canonicalConfigs) canonicalIds.put(configKey(config), config.getId());
        Map<Integer, Integer> idMap = new HashMap<>();
        for (Config config : envelope.configs) {
            Integer canonicalId = canonicalIds.get(configKey(config));
            if (canonicalId != null && config.getId() > 0) idMap.put(config.getId(), canonicalId);
        }
        remapRecordConfigIds(envelope.histories, envelope.keeps, idMap);
    }

    private void remapRecordConfigIds(List<History> histories, List<Keep> keeps, Map<Integer, Integer> idMap) {
        for (History history : histories) {
            Integer id = idMap.get(history.getCid());
            if (id != null) history.setCid(id);
        }
        for (Keep keep : keeps) {
            Integer id = idMap.get(keep.getCid());
            if (id != null) keep.setCid(id);
        }
    }

    private void mergeTombstones(Map<String, Long> output, Map<String, Long> input) {
        if (input == null) return;
        for (Map.Entry<String, Long> entry : input.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) continue;
            output.merge(entry.getKey(), entry.getValue(), Math::max);
        }
    }

    private SyncDelta diff(SyncEnvelope before, SyncEnvelope after) {
        SyncDelta delta = new SyncDelta();
        Map<String, History> oldHistories = new HashMap<>();
        Map<String, History> newHistories = new HashMap<>();
        for (History item : before.histories) oldHistories.put(item.getKey(), item);
        for (History item : after.histories) newHistories.put(item.getKey(), item);
        for (Map.Entry<String, History> entry : newHistories.entrySet()) {
            History old = oldHistories.get(entry.getKey());
            if (old == null) delta.historyAdded++;
            else if (!sameJson(old, entry.getValue())) delta.historyUpdated++;
        }
        for (String key : oldHistories.keySet()) if (!newHistories.containsKey(key)) delta.historyDeleted++;

        Map<String, Keep> oldKeeps = new HashMap<>();
        Map<String, Keep> newKeeps = new HashMap<>();
        for (Keep item : before.keeps) oldKeeps.put(item.getKey(), item);
        for (Keep item : after.keeps) newKeeps.put(item.getKey(), item);
        for (Map.Entry<String, Keep> entry : newKeeps.entrySet()) {
            Keep old = oldKeeps.get(entry.getKey());
            if (old == null) delta.keepAdded++;
            else if (!sameJson(old, entry.getValue())) delta.keepUpdated++;
        }
        for (String key : oldKeeps.keySet()) if (!newKeeps.containsKey(key)) delta.keepDeleted++;

        Map<String, Config> oldConfigs = new HashMap<>();
        Map<String, Config> newConfigs = new HashMap<>();
        for (Config item : before.configs) oldConfigs.put(configKey(item), item);
        for (Config item : after.configs) newConfigs.put(configKey(item), item);
        for (Map.Entry<String, Config> entry : newConfigs.entrySet()) {
            Config old = oldConfigs.get(entry.getKey());
            if (old == null) delta.configAdded++;
            else if (!sameConfig(old, entry.getValue())) delta.configUpdated++;
        }
        for (String key : oldConfigs.keySet()) if (!newConfigs.containsKey(key)) delta.configDeleted++;

        delta.settingsChanged = !Objects.equals(before.settings, after.settings);
        delta.metadataChanged = !Objects.equals(before.tombstones, after.tombstones);
        return delta;
    }

    private boolean sameJson(Object first, Object second) {
        return Objects.equals(SYNC_GSON.toJson(first), SYNC_GSON.toJson(second));
    }

    private boolean sameConfig(Config first, Config second) {
        return first.getId() == second.getId()
                && first.getType() == second.getType()
                && TextUtils.equals(first.getUrl(), second.getUrl())
                && TextUtils.equals(first.getName(), second.getName());
    }

    private String buildSyncMessage(SyncDelta downloaded, SyncDelta uploaded) {
        List<String> changes = new ArrayList<>();
        if (downloaded.hasChanges()) changes.add("下载" + downloaded.describe());
        if (uploaded.hasChanges()) changes.add("上传" + uploaded.describe());
        if (changes.isEmpty()) return "同步完成：没有新变化";
        return "同步完成：" + String.join("；", changes);
    }

    private void pruneTombstones(Map<String, Long> tombstones) {
        long cutoff = System.currentTimeMillis() - TOMBSTONE_RETENTION;
        tombstones.entrySet().removeIf(entry -> entry.getValue() == null || entry.getValue() < cutoff);
    }

    private void applyLocally(SyncEnvelope merged) {
        String runtimeVodUrl = VodConfig.getUrl();
        Map<Integer, Integer> localConfigIds = applyConfigs(merged.configs);
        remapRecordConfigIds(merged.histories, merged.keeps, localConfigIds);
        Set<String> historyKeys = new HashSet<>();
        Set<String> keepKeys = new HashSet<>();
        for (History history : merged.histories) historyKeys.add(history.getKey());
        for (Keep keep : merged.keeps) keepKeys.add(keep.getKey());
        AppDatabase.get().runInTransaction(() -> {
            AppDatabase.get().getHistoryDao().insertOrUpdate(merged.histories);
            AppDatabase.get().getKeepDao().insertOrUpdate(merged.keeps);
            for (Map.Entry<String, Long> entry : merged.tombstones.entrySet()) {
                if (entry.getKey().startsWith(HISTORY_PREFIX)) {
                    String key = entry.getKey().substring(HISTORY_PREFIX.length());
                    if (!historyKeys.contains(key)) AppDatabase.get().getHistoryDao().deleteByKey(key);
                } else if (entry.getKey().startsWith(KEEP_PREFIX)) {
                    String key = entry.getKey().substring(KEEP_PREFIX.length());
                    if (!keepKeys.contains(key)) AppDatabase.get().getKeepDao().deleteByKey(key);
                } else if (entry.getKey().startsWith(CONFIG_PREFIX)) {
                    deleteConfigTombstone(entry.getKey());
                }
            }
        });
        applySettings(merged.settings);
        applySelectedConfigs(merged.settings);
        saveLocalTombstones(merged.tombstones);
        Config syncedVod = Config.vod();
        boolean reloadVod = !syncedVod.isEmpty() && !TextUtils.equals(runtimeVodUrl, syncedVod.getUrl());
        App.post(() -> {
            RefreshEvent.history();
            RefreshEvent.keep();
            RefreshEvent.config();
            if (reloadVod) {
                VodConfig.load(Config.vod(), new Callback() {
                    @Override
                    public void success() {
                        RefreshEvent.config();
                        RefreshEvent.video();
                    }
                });
            }
        });
    }

    private Map<Integer, Integer> applyConfigs(List<Config> configs) {
        Map<Integer, Integer> idMap = new HashMap<>();
        if (configs == null) return idMap;
        for (Config cloud : configs) {
            if (cloud == null || TextUtils.isEmpty(cloud.getUrl()) || cloud.getType() > 1) continue;
            Config stored = AppDatabase.get().getConfigDao().find(cloud.getUrl(), cloud.getType());
            Config target = copyConfigForSync(cloud);
            if (stored != null) target.setId(stored.getId());
            if (stored == null && AppDatabase.get().getConfigDao().findById(target.getId()) != null) target.setId(0);
            AppDatabase.get().getConfigDao().insertOrUpdate(target);
            Config applied = AppDatabase.get().getConfigDao().find(cloud.getUrl(), cloud.getType());
            if (applied != null && cloud.getId() > 0) idMap.put(cloud.getId(), applied.getId());
        }
        return idMap;
    }

    private void deleteConfigTombstone(String tombstone) {
        String value = tombstone.substring(CONFIG_PREFIX.length());
        int separator = value.indexOf(':');
        if (separator <= 0) return;
        try {
            int type = Integer.parseInt(value.substring(0, separator));
            String url = value.substring(separator + 1);
            AppDatabase.get().getConfigDao().delete(url, type);
        } catch (NumberFormatException ignored) {
        }
    }

    private void applySelectedConfigs(Map<String, Object> settings) {
        if (settings == null) return;
        applySelectedConfig(settings, 0);
        applySelectedConfig(settings, 1);
    }

    private void applySelectedConfig(Map<String, Object> settings, int type) {
        Object value = settings.get("config_" + type);
        if (!(value instanceof String) || TextUtils.isEmpty((String) value)) return;
        Config selected = AppDatabase.get().getConfigDao().find((String) value, type);
        if (selected != null && Config.getAll(type).size() > 1) {
            selected.setTime(System.currentTimeMillis());
            AppDatabase.get().getConfigDao().insertOrUpdate(selected);
        }
    }

    private void uploadSafely(RemoteSnapshot expected, SyncEnvelope merged) throws Exception {
        String finalUrl = fileUrl(SYNC_FILE);
        String tempName = ".xybox-sync-temp-" + getDeviceId().substring(0, 8);
        String tempUrl = fileUrl(tempName);
        byte[] bytes = SYNC_GSON.toJson(merged).getBytes(StandardCharsets.UTF_8);
        try {
            verifyRemoteUnchanged(expected);
            createRemoteBackup(expected, finalUrl);
            if (remoteExists(tempName)) sardine.delete(tempUrl);
            sardine.put(tempUrl, bytes, "application/json; charset=utf-8");
            sardine.move(tempUrl, finalUrl, true);
        } catch (Exception e) {
            try {
                if (remoteExists(tempName)) sardine.delete(tempUrl);
            } catch (Exception ignored) {
            }
            if (e instanceof SyncConflictException || !isAtomicReplaceUnsupported(e)) throw e;
            Logger.w("WebDAV: 服务器不支持临时文件替换，改用兼容写入");
            verifyRemoteUnchanged(expected);
            createRemoteBackup(expected, finalUrl);
            sardine.put(finalUrl, bytes, "application/json; charset=utf-8");
        }
        verifyUploadedSnapshot(merged);
    }

    private void verifyRemoteUnchanged(RemoteSnapshot expected) throws Exception {
        boolean existsNow = remoteExists(SYNC_FILE);
        if (expected.exists != existsNow) throw new SyncConflictException();
        if (!expected.exists) return;
        String currentEtag = getEtag(SYNC_FILE);
        if (!TextUtils.isEmpty(expected.etag) && !Objects.equals(expected.etag, currentEtag)) {
            throw new SyncConflictException();
        }
        if (TextUtils.isEmpty(expected.etag)) {
            SyncEnvelope current = SYNC_GSON.fromJson(readText(fileUrl(SYNC_FILE)), SyncEnvelope.class);
            if (current == null || current.revision != expected.envelope.revision) {
                throw new SyncConflictException();
            }
        }
    }

    private void createRemoteBackup(RemoteSnapshot expected, String finalUrl) {
        if (!expected.exists) return;
        try {
            sardine.copy(finalUrl, fileUrl(BACKUP_FILE), true);
        } catch (Exception e) {
            Logger.w("WebDAV: 创建云端回滚副本失败，继续写入: " + e.getMessage());
        }
    }

    private boolean isAtomicReplaceUnsupported(Exception error) {
        String message = String.valueOf(error.getMessage()).toLowerCase();
        return message.contains("405") || message.contains("409") || message.contains("501")
                || message.contains("method not allowed") || message.contains("not implemented");
    }

    private void verifyUploadedSnapshot(SyncEnvelope expected) throws Exception {
        if (!remoteExists(SYNC_FILE)) throw new IllegalStateException("云端文件写入后未找到");
        SyncEnvelope uploaded = SYNC_GSON.fromJson(readText(fileUrl(SYNC_FILE)), SyncEnvelope.class);
        if (uploaded == null || uploaded.revision != expected.revision) {
            throw new IllegalStateException("云端文件写入后校验失败");
        }
    }

    private void rememberSuccessfulState(SyncEnvelope merged) {
        long now = System.currentTimeMillis();
        putLong(PREF_LAST_SUCCESS, now);
        putLong(PREF_SETTINGS_TIME, merged.settingsUpdatedAt);
        Prefers.put(PREF_SETTINGS_HASH, settingsHash(merged.settings));
        saveLocalTombstones(merged.tombstones);
    }

    private Map<String, Object> collectSettings() {
        return filterSettings(Prefers.getPrefers().getAll());
    }

    private Map<String, Object> filterSettings(Map<String, ?> source) {
        Map<String, Object> output = new TreeMap<>();
        if (source == null) return output;
        for (String key : SETTINGS_WHITELIST) {
            if (source.containsKey(key)) output.put(key, normalizeSettingValue(key, source.get(key)));
        }
        return output;
    }

    private Object normalizeSettingValue(String key, Object value) {
        if (!(value instanceof Number)) return value;
        Number number = (Number) value;
        return FLOAT_SETTINGS.contains(key) ? number.floatValue() : number.intValue();
    }

    private void applySettings(Map<String, Object> settings) {
        if (settings == null) return;
        for (Map.Entry<String, Object> entry : settings.entrySet()) {
            if (!SETTINGS_WHITELIST.contains(entry.getKey())) continue;
            Prefers.put(entry.getKey(), normalizeSettingValue(entry.getKey(), entry.getValue()));
        }
    }

    private long resolveLocalSettingsTime(Map<String, Object> settings, boolean remoteHasSettings) {
        String currentHash = settingsHash(settings);
        String previousHash = Prefers.getString(PREF_SETTINGS_HASH, "");
        long previousTime = getLong(PREF_SETTINGS_TIME);
        if (TextUtils.isEmpty(previousHash)) return remoteHasSettings ? 0 : System.currentTimeMillis();
        return previousHash.equals(currentHash) ? previousTime : System.currentTimeMillis();
    }

    private String settingsHash(Map<String, Object> settings) {
        return Integer.toHexString(SYNC_GSON.toJson(new TreeMap<>(settings)).hashCode());
    }

    private Map<String, Long> loadLocalTombstones() {
        try {
            Type type = new TypeToken<Map<String, Long>>() {}.getType();
            Map<String, Long> map = SYNC_GSON.fromJson(Prefers.getString(PREF_TOMBSTONES, "{}"), type);
            return map == null ? new HashMap<>() : new HashMap<>(map);
        } catch (Exception e) {
            return new HashMap<>();
        }
    }

    private void saveLocalTombstones(Map<String, Long> tombstones) {
        Prefers.put(PREF_TOMBSTONES, SYNC_GSON.toJson(tombstones));
    }

    private void normalize(SyncEnvelope envelope) {
        envelope.schemaVersion = SCHEMA_VERSION;
        if (envelope.histories == null) envelope.histories = new ArrayList<>();
        if (envelope.keeps == null) envelope.keeps = new ArrayList<>();
        if (envelope.configs == null) envelope.configs = new ArrayList<>();
        if (envelope.settings == null) envelope.settings = new TreeMap<>();
        else envelope.settings = filterSettings(envelope.settings);
        if (envelope.tombstones == null) envelope.tombstones = new HashMap<>();
    }

    private void ensureDirectory() throws Exception {
        try {
            List<DavResource> resources = sardine.list(baseUrl, 0);
            String requested = trimPath(URI.create(baseUrl).getPath());
            boolean matched = false;
            for (DavResource resource : resources) {
                if (requested.equalsIgnoreCase(trimPath(resource.getPath()))) {
                    matched = true;
                    break;
                }
            }
            if (!matched) sardine.createDirectory(baseUrl);
        } catch (Exception e) {
            String message = String.valueOf(e.getMessage()).toLowerCase();
            if (message.contains("404") || message.contains("not found")) sardine.createDirectory(baseUrl);
            else throw e;
        }
    }

    private String trimPath(String path) {
        if (path == null || path.isEmpty()) return "";
        String value = path;
        while (value.length() > 1 && value.endsWith("/")) value = value.substring(0, value.length() - 1);
        return value;
    }

    private String fileUrl(String name) {
        return baseUrl + name;
    }

    private String readText(String url) throws Exception {
        try (InputStream input = sardine.get(url); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            return output.toString(StandardCharsets.UTF_8.name());
        }
    }

    private boolean remoteExists(String name) throws Exception {
        return remoteExists(baseUrl, name);
    }

    private boolean remoteExists(String folderUrl, String name) throws Exception {
        return findResource(folderUrl, name) != null;
    }

    private DavResource findResource(String name) throws Exception {
        return findResource(baseUrl, name);
    }

    private DavResource findResource(String folderUrl, String name) throws Exception {
        for (DavResource resource : sardine.list(folderUrl, 1)) {
            if (name.equals(resource.getName())) return resource;
            String path = resource.getPath();
            if (path != null && path.endsWith("/" + name)) return resource;
        }
        return null;
    }

    private String getEtag(String name) {
        try {
            DavResource resource = findResource(name);
            return resource == null ? null : resource.getEtag();
        } catch (Exception e) {
            return null;
        }
    }

    private String getDeviceId() {
        String id = Prefers.getString(PREF_DEVICE_ID, "");
        if (!TextUtils.isEmpty(id)) return id;
        id = UUID.randomUUID().toString();
        Prefers.put(PREF_DEVICE_ID, id);
        return id;
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager manager = (ConnectivityManager) App.get().getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo info = manager == null ? null : manager.getActiveNetworkInfo();
        return info != null && info.isConnected();
    }

    private SyncResult finish(boolean success, String message, int histories, int keeps) {
        Prefers.put(PREF_LAST_STATUS, message);
        if (success) Logger.d("WebDAV: " + message); else Logger.e("WebDAV: " + message);
        return new SyncResult(success, message, histories, keeps);
    }

    private String friendlyError(Exception error) {
        String message = error == null ? "未知错误" : error.getMessage();
        if (message == null) message = error.getClass().getSimpleName();
        String lower = message.toLowerCase();
        if (lower.contains("401") || lower.contains("unauthorized")) {
            return "认证失败，请确认用户名和应用密码；坚果云不能使用登录密码";
        }
        if (lower.contains("403") || lower.contains("forbidden")) return "服务器拒绝写入，请检查WebDAV权限";
        if (lower.contains("409") || lower.contains("conflict")) return "服务器拒绝创建同步文件，请检查同步目录是否可写";
        if (lower.contains("404") || lower.contains("not found")) return "同步目录不存在或地址填写错误";
        if (lower.contains("timeout")) return "连接超时，联网后会自动重试";
        if (lower.contains("unknownhost") || lower.contains("unreachable")) return "无法连接服务器，请检查网络和地址";
        if (lower.contains("ssl") || lower.contains("certificate")) return "服务器证书校验失败";
        return "同步失败：" + message;
    }

    private long getLong(String key) {
        try {
            return Long.parseLong(Prefers.getString(key, "0"));
        } catch (Exception e) {
            return 0;
        }
    }

    private void putLong(String key, long value) {
        Prefers.put(key, String.valueOf(value));
    }

    // Compatibility API used by older UI variants.
    public boolean uploadHistory() { return syncNow().success; }
    public boolean downloadHistory() { return syncNow().success; }
    public boolean uploadSettings() { return syncNow().success; }
    public boolean downloadSettings() { return syncNow().success; }
    public boolean uploadBackup() { return syncNow().success; }
    public boolean downloadBackup() { return syncNow().success; }
    public boolean syncHistory() { return syncHistory(true); }
    public boolean syncSettings() { return syncSettings(true); }
    public boolean syncAll() { return syncAll(true); }

    public boolean syncHistory(boolean async) { return runCompatible(async); }
    public boolean syncSettings(boolean async) { return runCompatible(async); }
    public boolean syncAll(boolean async) { return runCompatible(async); }

    private boolean runCompatible(boolean async) {
        if (!isConfigured()) return false;
        if (async) App.execute(this::syncNow);
        else return syncNow().success;
        return true;
    }

    public static final class TestResult {
        public final boolean success;
        public final String message;

        public TestResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }
    }

    public static final class SyncResult {
        public final boolean success;
        public final String message;
        public final int historyCount;
        public final int keepCount;

        public SyncResult(boolean success, String message, int historyCount, int keepCount) {
            this.success = success;
            this.message = message;
            this.historyCount = historyCount;
            this.keepCount = keepCount;
        }
    }

    private static final class SyncEnvelope {
        int schemaVersion = SCHEMA_VERSION;
        long revision;
        String deviceId = "";
        long updatedAt;
        long settingsUpdatedAt;
        List<History> histories = new ArrayList<>();
        List<Keep> keeps = new ArrayList<>();
        List<Config> configs = new ArrayList<>();
        Map<String, Object> settings = new TreeMap<>();
        Map<String, Long> tombstones = new HashMap<>();
    }

    private static final class RemoteSnapshot {
        final boolean exists;
        final boolean legacyImported;
        final String etag;
        final SyncEnvelope envelope;

        RemoteSnapshot(boolean exists, boolean legacyImported, String etag, SyncEnvelope envelope) {
            this.exists = exists;
            this.legacyImported = legacyImported;
            this.etag = etag;
            this.envelope = envelope;
        }
    }

    private static final class SyncConflictException extends Exception {
    }

    private static final class SyncDelta {
        int historyAdded;
        int historyUpdated;
        int historyDeleted;
        int keepAdded;
        int keepUpdated;
        int keepDeleted;
        int configAdded;
        int configUpdated;
        int configDeleted;
        boolean settingsChanged;
        boolean metadataChanged;

        boolean hasChanges() {
            return historyAdded + historyUpdated + historyDeleted
                    + keepAdded + keepUpdated + keepDeleted
                    + configAdded + configUpdated + configDeleted > 0
                    || settingsChanged || metadataChanged;
        }

        String describe() {
            List<String> items = new ArrayList<>();
            add(items, historyAdded, "新增", "条观看记录");
            add(items, historyUpdated, "更新", "条观看记录");
            add(items, historyDeleted, "删除", "条观看记录");
            add(items, keepAdded, "新增", "条收藏");
            add(items, keepUpdated, "更新", "条收藏");
            add(items, keepDeleted, "删除", "条收藏");
            add(items, configAdded, "新增", "个播放源");
            add(items, configUpdated, "更新", "个播放源");
            add(items, configDeleted, "删除", "个播放源");
            if (settingsChanged) items.add("更新设置");
            if (items.isEmpty() && metadataChanged) items.add("更新删除状态");
            return String.join("、", items);
        }

        private void add(List<String> items, int count, String action, String unit) {
            if (count > 0) items.add(action + count + unit);
        }
    }
}
