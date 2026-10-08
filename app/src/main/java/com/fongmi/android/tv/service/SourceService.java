package com.fongmi.android.tv.service;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;
import android.os.ParcelFileDescriptor;

import com.fongmi.android.tv.api.loader.BaseLoader;
import com.fongmi.android.tv.api.loader.SourceBridge;
import com.fongmi.android.tv.api.loader.SourceWire;
import com.fongmi.android.tv.utils.AppLog;
import com.github.catvod.crawler.Spider;
import com.github.catvod.crawler.SpiderNull;
import com.github.catvod.bean.Doh;
import com.github.catvod.net.OkHttp;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ScheduledExecutorService;

import fi.iki.elonen.NanoHTTPD;

/** Private bound service: a crashing third-party native plugin terminates only :sources. */
public final class SourceService extends Service {
    /** A cancelled call that a plugin has not released after this long counts as stuck. */
    private static final long STUCK_AFTER_MS = 20_000;
    private static final int STUCK_LIMIT = 4;
    private final ThreadPoolExecutor workers = pool();
    private final ThreadPoolExecutor searchWorkers = pool();
    private final ThreadPoolExecutor proxyWorkers = pool();
    private static ThreadPoolExecutor pool() {
        return new ThreadPoolExecutor(0, 8, 30, TimeUnit.SECONDS,
                new SynchronousQueue<>(), runnable -> new Thread(runnable, "source-rpc"));
    }
    private int generation = -1;
    private String proxy, doh, network;
    private static final class Job {
        final ParcelFileDescriptor input, output;
        FutureTask<Void> task;
        boolean done;
        long cancelledAt;
        Job(ParcelFileDescriptor input, ParcelFileDescriptor output) { this.input = input; this.output = output; }
        void cancel() {
            task.cancel(true);
            try { input.close(); } catch (Exception ignored) { }
            try { output.closeWithError("Source request cancelled"); } catch (Exception ignored) { }
        }
    }
    private final Map<Integer, Job> jobs = new ConcurrentHashMap<>();
    /** Cancelled jobs that still occupy a worker because the plugin ignores interruption. */
    private final Map<Integer, Job> cancelled = new ConcurrentHashMap<>();
    private final ScheduledExecutorService watchdog = Executors.newSingleThreadScheduledExecutor(runnable -> new Thread(runnable, "source-watchdog"));
    private final Binder endpoint = new Binder() {
        @Override protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) {
            if (code == INTERFACE_TRANSACTION) { reply.writeString(SourceBridge.TOKEN); return true; }
            if (code != SourceBridge.CALL && code != SourceBridge.CANCEL) return false;
            data.enforceInterface(SourceBridge.TOKEN);
            if (code == SourceBridge.CANCEL) {
                int cancelledId = data.readInt();
                Job job = jobs.remove(cancelledId);
                if (job != null) trackCancelled(cancelledId, job);
                return true;
            }
            ParcelFileDescriptor[] pipe = null;
            final int id = data.readInt();
            final String operation = data.readString();
            ParcelFileDescriptor requestPipe = data.readParcelable(ParcelFileDescriptor.class.getClassLoader());
            try {
                if (requestPipe == null) throw new IllegalArgumentException("Missing source request pipe");
                pipe = ParcelFileDescriptor.createReliablePipe();
                final ParcelFileDescriptor writer = pipe[1];
                Job job = new Job(requestPipe, writer);
                FutureTask<Void> task = new FutureTask<>(() -> {
                    try (InputStream input = new ParcelFileDescriptor.AutoCloseInputStream(requestPipe)) {
                        JSONObject request = new JSONObject(SourceWire.read(input));
                        if (!operation.equals(request.optString("op"))) throw new IllegalArgumentException("Source operation mismatch");
                        execute(request, writer);
                    } catch (Exception error) { try { writer.closeWithError("Invalid source request: " + error); } catch (Exception ignored) { } }
                    finally {
                        synchronized (job) { job.done = true; }
                        jobs.remove(id); cancelled.remove(id);
                    }
                    return null;
                });
                job.task = task;
                jobs.put(id, job);
                // Uninterruptible searches must not consume all playback/proxy execution slots.
                ThreadPoolExecutor pool = "proxy".equals(operation) ? proxyWorkers
                        : "search".equals(operation) || "home".equals(operation) || "category".equals(operation) ? searchWorkers : workers;
                try { pool.execute(task); }
                catch (Exception error) { jobs.remove(id); throw error; }
                reply.writeNoException();
                reply.writeParcelable(pipe[0], android.os.Parcelable.PARCELABLE_WRITE_RETURN_VALUE);
                pipe[0].close();
            } catch (Exception error) {
                if (pipe != null) for (ParcelFileDescriptor fd : pipe) try { fd.close(); } catch (Exception ignored) { }
                if (requestPipe != null) try { requestPipe.close(); } catch (Exception ignored) { }
                reply.writeException(new IllegalStateException("Isolated source unavailable", error));
            }
            return true;
        }
    };

    @Override public IBinder onBind(Intent intent) { return endpoint; }

    @Override public void onCreate() {
        super.onCreate();
        watchdog.scheduleWithFixedDelay(this::checkStuckJobs, 5, 5, TimeUnit.SECONDS);
    }

    private void trackCancelled(int id, Job job) {
        synchronized (job) {
            if (job.done) return;
            job.cancelledAt = android.os.SystemClock.elapsedRealtime();
            cancelled.put(id, job);
        }
        job.cancel();
    }

    /**
     * A plugin that ignores interruption would otherwise consume a worker slot forever. Once
     * enough cancelled calls are stuck, exiting lets the system respawn a clean worker process
     * for the still-active binding; the main process reconnects through its death recipient.
     */
    private void checkStuckJobs() {
        long now = android.os.SystemClock.elapsedRealtime();
        int stuck = 0;
        for (Job job : cancelled.values()) if (now - job.cancelledAt >= STUCK_AFTER_MS) stuck++;
        if (stuck < STUCK_LIMIT) return;
        AppLog.recordSourceFailure("watchdog: restarting source process stuck=" + stuck + " cancelled=" + cancelled.size());
        android.os.Process.killProcess(android.os.Process.myPid());
    }

    @Override public void onDestroy() {
        watchdog.shutdownNow();
        for (Job job : jobs.values()) job.cancel();
        workers.shutdownNow(); searchWorkers.shutdownNow(); proxyWorkers.shutdownNow(); super.onDestroy();
    }

    // Mirror of the network side effects in VodConfig.install(): any new global OkHttp setting
    // added there must also be synced here, or isolated plugins silently diverge from the app.
    private synchronized void configure(JSONObject request) throws Exception {
        int next = request.getInt("generation");
        if (next < generation) throw new IllegalStateException("Obsolete source request");
        if (next > generation) { BaseLoader.get().clear(); generation = next; }
        com.github.catvod.Proxy.set(request.getInt("port"));
        String nextProxy = request.optString("proxy"), nextDoh = request.optString("doh");
        if (!nextProxy.equals(proxy)) { OkHttp.get().setProxy(nextProxy); proxy = nextProxy; }
        if (!nextDoh.equals(doh)) { OkHttp.get().setDoh(Doh.objectFrom(nextDoh)); doh = nextDoh; }
        String nextNetwork = request.optString("network", "{}");
        if (!nextNetwork.equals(network)) {
            JSONObject config = new JSONObject(nextNetwork);
            OkHttp.dns().clear(); OkHttp.selector().clear(); OkHttp.responseInterceptor().clear();
            OkHttp.dns().addAll(strings(config.optJSONArray("hosts")));
            OkHttp.selector().addAll(strings(config.optJSONArray("proxyHosts")));
            JSONArray headers = config.optJSONArray("headers");
            List<com.google.gson.JsonElement> items = new ArrayList<>();
            if (headers != null) for (int i = 0; i < headers.length(); i++) items.add(com.github.catvod.utils.Json.parse(headers.get(i).toString()));
            OkHttp.responseInterceptor().setHeaders(items);
            network = nextNetwork;
        }
    }

    private void execute(JSONObject request, ParcelFileDescriptor descriptor) {
        String operation = request.optString("op");
        JSONObject metadata = request.optJSONObject("site");
        String siteKey = metadata == null ? "" : metadata.optString("key");
        boolean started = false;
        try (OutputStream output = new ParcelFileDescriptor.AutoCloseOutputStream(descriptor)) {
            try {
                configure(request);
                JSONObject args = request.getJSONObject("args");
                AppLog.event("SourceRpc", "start pid=" + android.os.Process.myPid() + " op=" + operation + " site=" + siteKey);
                if (operation.equals("proxy")) {
                    if (metadata != null) spider(metadata);
                    BaseLoader.get().parseJar(args.optString("jar"), true);
                    Object[] value = BaseLoader.get().proxyLocal(stringMap(args.optJSONObject("params")));
                    if (value == null || value.length == 0) throw new IllegalStateException("Source proxy returned no response");
                    JSONObject header = new JSONObject().put("ok", true);
                    InputStream input;
                    if (value[0] instanceof NanoHTTPD.Response) {
                        NanoHTTPD.Response response = (NanoHTTPD.Response) value[0];
                        Field headers = NanoHTTPD.Response.class.getDeclaredField("header"); headers.setAccessible(true);
                        Field length = NanoHTTPD.Response.class.getDeclaredField("contentLength"); length.setAccessible(true);
                        header.put("status", response.getStatus().getRequestStatus()).put("mime", response.getMimeType())
                                .put("headers", new JSONObject((Map<?, ?>) headers.get(response))).put("length", length.getLong(response));
                        input = response.getData();
                    } else {
                        header.put("status", value[0]).put("mime", value[1]);
                        if (value.length > 3 && value[3] instanceof Map) header.put("headers", new JSONObject((Map<?, ?>) value[3]));
                        input = (InputStream) value[2];
                    }
                    try (InputStream body = input) {
                        SourceWire.write(output, header.toString()); started = true;
                        if (body != null) {
                            byte[] bytes = new byte[32 * 1024]; int count;
                            while ((count = body.read(bytes)) != -1) output.write(bytes, 0, count);
                        }
                    }
                } else {
                    Object value;
                    if (operation.equals("jsonExt") || operation.equals("jsonExtMix")) {
                        String jar = args.optString("jar"); BaseLoader.get().parseJar(jar, true);
                        JSONArray parsers = args.getJSONArray("parsers");
                        if (operation.equals("jsonExt")) {
                            LinkedHashMap<String, String> map = new LinkedHashMap<>();
                            for (int i = 0; i < parsers.length(); i++) { JSONObject entry = parsers.getJSONObject(i); map.put(entry.getString("key"), entry.getString("value")); }
                            value = BaseLoader.get().jsonExt(jar, args.getString("key"), map, args.getString("url"));
                        }
                        else {
                            LinkedHashMap<String, HashMap<String, String>> map = new LinkedHashMap<>();
                            for (int i = 0; i < parsers.length(); i++) { JSONObject entry = parsers.getJSONObject(i); map.put(entry.getString("key"), stringMap(entry.getJSONObject("value"))); }
                            value = BaseLoader.get().jsonExtMix(jar, args.getString("flag"), args.getString("key"), args.getString("name"), map, args.getString("url"));
                        }
                    } else value = invoke(spider(metadata), operation, args);
                    SourceWire.write(output, new JSONObject().put("ok", true).put("value", value == null ? "" : value.toString()).toString());
                    started = true;
                }
                AppLog.event("SourceRpc", "finish pid=" + android.os.Process.myPid() + " op=" + operation + " site=" + siteKey);
            } catch (Throwable error) {
                StringWriter trace = new StringWriter(); error.printStackTrace(new PrintWriter(trace));
                // 主进程取消请求后插件初始化被打断（FutureTask.get 会清掉中断标志）：这是取消，不是站点失败。
                if (!Thread.currentThread().isInterrupted() && !cancelled(error)) AppLog.recordSourceFailure("op=" + operation + " site=" + siteKey + "\n" + trace);
                if (!started) SourceWire.write(output, new JSONObject().put("ok", false).put("error", trace.toString()).toString());
                else descriptor.closeWithError("Source stream failed: " + error);
            }
        } catch (Exception ignored) { }
    }

    private static boolean cancelled(Throwable error) {
        for (int depth = 0; error != null && depth < 12; depth++, error = error.getCause())
            if (error instanceof InterruptedException || error instanceof java.io.InterruptedIOException) return true;
        return false;
    }

    private Spider spider(JSONObject site) {
        if (site == null) throw new IllegalArgumentException("Missing source descriptor");
        Spider spider = BaseLoader.get().getSpider(site.optString("key"), site.optString("api"), site.optString("ext"), site.optString("jar"));
        if (spider instanceof SpiderNull) throw new IllegalStateException("Source plugin failed to initialize: " + site.optString("api"));
        BaseLoader.get().setRecent(site.optString("key"), site.optString("api"), site.optString("jar"));
        return spider;
    }

    private Object invoke(Spider spider, String op, JSONObject args) throws Exception {
        switch (op) {
            case "home": return spider.homeContent(args.optBoolean("filter"));
            case "homeVideo": return spider.homeVideoContent();
            case "category": return spider.categoryContent(args.getString("tid"), args.getString("pg"), args.optBoolean("filter"), stringMap(args.optJSONObject("extend")));
            case "detail": return spider.detailContent(strings(args.getJSONArray("ids")));
            case "search": return args.has("pg") ? spider.searchContent(args.getString("query"), args.optBoolean("quick"), args.getString("pg")) : spider.searchContent(args.getString("query"), args.optBoolean("quick"));
            case "player": return spider.playerContent(args.getString("flag"), args.getString("id"), strings(args.getJSONArray("flags")));
            case "live": return spider.liveContent(args.getString("url"));
            case "action": return spider.action(args.getString("action"));
            case "manualVideoCheck": return spider.manualVideoCheck();
            case "isVideoFormat": return spider.isVideoFormat(args.getString("url"));
            default: throw new IllegalArgumentException("Unknown source operation: " + op);
        }
    }

    public static HashMap<String, String> stringMap(JSONObject json) {
        HashMap<String, String> map = new HashMap<>();
        if (json != null) { java.util.Iterator<String> keys = json.keys(); while (keys.hasNext()) { String key = keys.next(); map.put(key, json.optString(key)); } }
        return map;
    }
    private List<String> strings(JSONArray array) throws Exception {
        List<String> list = new ArrayList<>(); if (array != null) for (int i = 0; i < array.length(); i++) list.add(array.getString(i)); return list;
    }
}
