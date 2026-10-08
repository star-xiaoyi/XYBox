package com.fongmi.android.tv.api.loader;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.Looper;
import android.os.Parcel;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.system.Os;
import android.system.OsConstants;
import android.system.StructPollfd;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.service.SourceService;
import com.fongmi.android.tv.utils.AppLog;
import com.github.catvod.utils.Util;

import org.json.JSONObject;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** The main process owns only descriptors. All third-party code executes in :sources. */
public final class SourceBridge {
    public static final String TOKEN = "com.xybox.source.rpc.v1";
    public static final int CALL = IBinder.FIRST_CALL_TRANSACTION;
    public static final int CANCEL = CALL + 1;
    private static final SourceBridge INSTANCE = new SourceBridge();
    private final Map<String, String> jars = new ConcurrentHashMap<>();
    private final Map<String, JSONObject> sites = new ConcurrentHashMap<>();
    private final AtomicInteger generation = new AtomicInteger();
    private final AtomicInteger requests = new AtomicInteger();
    private final Object lock = new Object();
    private volatile IBinder binder;
    private boolean bound;
    private volatile String recentJar = "";
    private volatile JSONObject recentSite;
    private volatile String recentSiteKey = "";

    public static SourceBridge get() { return INSTANCE; }

    private final ServiceConnection connection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder service) {
            synchronized (lock) {
                binder = service;
                try { service.linkToDeath(() -> disconnected(service), 0); }
                catch (Exception error) { binder = null; }
                lock.notifyAll();
            }
            AppLog.event("SourceProcess", "connected mainPid=" + android.os.Process.myPid());
        }
        @Override public void onServiceDisconnected(ComponentName name) { disconnected(binder); }
        @Override public void onBindingDied(ComponentName name) {
            disconnected(binder);
            synchronized (lock) {
                if (bound) { try { App.get().unbindService(this); } catch (Exception ignored) { } }
                bound = false;
                lock.notifyAll();
            }
        }
        @Override public void onNullBinding(ComponentName name) { onBindingDied(name); }
    };

    private void disconnected(IBinder expected) {
        synchronized (lock) {
            if (expected != null && binder != expected) return;
            if (binder == null) return;
            binder = null;
            lock.notifyAll();
        }
        AppLog.event("SourceProcess", "isolated process disconnected; main/player remain alive");
        App.post(AppLog::refreshSystemExits, 1500);
    }

    private IBinder service() throws Exception {
        if (Looper.myLooper() == Looper.getMainLooper()) throw new SourceUnavailableException("Source RPC on UI thread");
        synchronized (lock) {
            if (!bound) {
                bound = App.get().bindService(new Intent(App.get(), SourceService.class), connection, Context.BIND_AUTO_CREATE);
                if (!bound) throw new SourceUnavailableException("Unable to bind isolated sources");
            }
            long until = SystemClock.elapsedRealtime() + 10_000;
            while (binder == null) {
                long remaining = until - SystemClock.elapsedRealtime();
                if (remaining <= 0) throw new SourceUnavailableException("Isolated source connection timed out");
                lock.wait(Math.min(remaining, 500));
            }
            return binder;
        }
    }

    public void clear() {
        generation.incrementAndGet();
        // Active proxy URLs still need their original descriptors after a catalog reload.
        recentJar = ""; recentSite = null; recentSiteKey = "";
    }

    public JSONObject site(String key, String api, String ext, String jar) {
        try {
            JSONObject site = new JSONObject().put("key", key).put("api", api).put("ext", ext).put("jar", jar);
            sites.put(key, site);
            if (key.equals(recentSiteKey)) recentSite = site;
            registerJar(jar, false);
            return site;
        } catch (Exception error) { throw new IllegalArgumentException(error); }
    }

    public void registerJar(String jar, boolean recent) {
        if (jar == null || jar.isEmpty()) return;
        jars.put(Util.md5(jar), jar);
        if (recent) recentJar = jar;
    }

    public void setRecent(String key, String api, String jar) {
        registerJar(jar, true);
        recentSiteKey = key;
        recentSite = sites.get(key);
    }

    public JSONObject call(JSONObject site, String operation, JSONObject args) throws Exception {
        try (InputStream input = open(site, operation, args)) { return response(input); }
    }

    private JSONObject response(InputStream input) throws Exception {
        JSONObject response = new JSONObject(SourceWire.read(input));
        if (!response.optBoolean("ok")) throw new IOException(response.optString("error", "Source operation failed"));
        ((TimedInput) input).headerComplete();
        return response;
    }

    private InputStream open(JSONObject site, String operation, JSONObject args) throws Exception {
        com.fongmi.android.tv.server.Server.get().start();
        IBinder remote = service();
        int id = requests.incrementAndGet();
        JSONObject request = new JSONObject().put("id", id).put("generation", generation.get()).put("op", operation)
                .put("args", args).put("port", com.github.catvod.Proxy.getPort())
                .put("proxy", com.fongmi.android.tv.Setting.getProxy()).put("doh", com.fongmi.android.tv.Setting.getDoh())
                .put("network", com.fongmi.android.tv.api.config.VodConfig.get().sourceNetwork());
        if (site != null) request.put("site", site);
        Parcel data = Parcel.obtain(), reply = Parcel.obtain();
        ParcelFileDescriptor descriptor = null;
        ParcelFileDescriptor[] inputPipe = ParcelFileDescriptor.createReliablePipe();
        try {
            String json = request.toString();
            if (json.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > SourceWire.MAX_FRAME) throw new IOException("Source request too large");
            data.writeInterfaceToken(TOKEN);
            data.writeInt(id);
            data.writeString(operation);
            data.writeParcelable(inputPipe[0], 0);
            AppLog.event("SourceRpcMain", "request=" + id + " op=" + operation + " site="
                    + (site == null ? "" : site.optString("key")) + " api=" + (site == null ? "" : site.optString("api")));
            if (!remote.transact(CALL, data, reply, 0)) throw new SourceUnavailableException("Source RPC unavailable");
            reply.readException();
            descriptor = reply.readParcelable(ParcelFileDescriptor.class.getClassLoader());
            if (descriptor == null) throw new SourceUnavailableException("Missing source response pipe");
            try (java.io.OutputStream output = new ParcelFileDescriptor.AutoCloseOutputStream(inputPipe[1])) { SourceWire.write(output, json); }
        } catch (android.os.DeadObjectException error) {
            if (descriptor != null) descriptor.close();
            disconnected(remote);
            throw new SourceUnavailableException("Source process exited during " + operation, error);
        } catch (Exception error) {
            if (descriptor != null) descriptor.close();
            cancel(remote, id);
            throw error;
        } finally {
            for (ParcelFileDescriptor fd : inputPipe) try { fd.close(); } catch (Exception ignored) { }
            data.recycle(); reply.recycle();
        }
        return new TimedInput(descriptor, remote, id);
    }

    private static void cancel(IBinder remote, int request) {
        Parcel data = Parcel.obtain();
        try { data.writeInterfaceToken(TOKEN); data.writeInt(request); remote.transact(CANCEL, data, null, IBinder.FLAG_ONEWAY); }
        catch (Exception ignored) { }
        finally { data.recycle(); }
    }

    public Object[] proxy(Map<String, String> params) throws Exception {
        JSONObject site = params.containsKey("siteKey") ? sites.get(params.get("siteKey"))
                : "js".equals(params.get("do")) ? recentSite : null;
        String jar = jars.get(params.getOrDefault("jarKey", ""));
        if (jar == null) jar = recentJar;
        JSONObject args = new JSONObject().put("params", new JSONObject(params)).put("jar", jar);
        InputStream input = open(site, "proxy", args);
        try {
            JSONObject meta = response(input);
            Map<String, String> headers = SourceService.stringMap(meta.optJSONObject("headers"));
            return new Object[]{meta.getInt("status"), meta.optString("mime"), input, headers, meta.optLong("length", -1)};
        } catch (Exception error) { input.close(); throw error; }
    }

    public JSONObject parse(String jar, String operation, JSONObject args) throws Exception {
        args.put("jar", jar == null || jar.isEmpty() ? recentJar : jar);
        return new JSONObject(call(null, operation, args).optString("value", "{}"));
    }

    /** Closing a reliable pipe interrupts a stuck read; cancellation never kills healthy playback. */
    private static final class TimedInput extends FilterInputStream {
        private final ParcelFileDescriptor descriptor;
        private final IBinder remote;
        private final int request;
        private volatile long deadline = SystemClock.elapsedRealtime() + 35_000;
        private volatile boolean header = true;
        private volatile boolean closed;
        TimedInput(ParcelFileDescriptor descriptor, IBinder remote, int request) {
            super(new ParcelFileDescriptor.AutoCloseInputStream(descriptor));
            this.descriptor = descriptor;
            this.remote = remote; this.request = request;
        }
        void headerComplete() { header = false; }
        @Override public int read() throws IOException {
            byte[] one = new byte[1]; int count = read(one, 0, 1); return count < 0 ? -1 : one[0] & 255;
        }
        @Override public int read(byte[] bytes, int offset, int length) throws IOException {
            if (closed) throw new IOException("Source pipe closed");
            if (length == 0) return 0;
            if (!header) deadline = SystemClock.elapsedRealtime() + 35_000;
            StructPollfd poll = new StructPollfd();
            poll.fd = descriptor.getFileDescriptor();
            poll.events = (short) (OsConstants.POLLIN | OsConstants.POLLHUP | OsConstants.POLLERR);
            try {
                // poll bounds both timeout and interruption. A blocking pipe read cannot rely on
                // Java interruption or on closing its descriptor from a different thread.
                while (true) {
                    if (closed) throw new IOException("Source pipe closed");
                    if (Thread.currentThread().isInterrupted() || SystemClock.elapsedRealtime() >= deadline) {
                        close(); throw new java.io.InterruptedIOException("Source operation cancelled or timed out");
                    }
                    if (Os.poll(new StructPollfd[]{poll}, 100) > 0) break;
                }
                int count = super.read(bytes, offset, length);
                if (closed) throw new IOException("Source operation cancelled or timed out");
                if (count == -1) descriptor.checkError();
                return count;
            } catch (android.system.ErrnoException error) { throw new IOException("Source pipe unavailable", error); }
        }
        @Override public synchronized void close() throws IOException {
            if (closed) return;
            closed = true;
            try { super.close(); }
            finally { cancel(remote, request); }
        }
    }
}
