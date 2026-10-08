package com.fongmi.android.tv.utils;

import android.content.Context;
import android.content.SharedPreferences;

import com.fongmi.android.tv.App;

/**
 * One-shot guard for native decoder crashes: after a tombstone identifies a decoder-class crash
 * in the main process, the film that was playing gets a single software-decode session.
 * The user's decode setting is never rewritten.
 */
public final class DecodeGuard {
    private static final long EXPIRY_MS = 7 * 24 * 3600_000L;
    private static final long CRASH_WINDOW_MS = 6 * 3600_000L;
    private static final String[] DECODER_SYMBOLS = {
            "ACodec", "CCodec", "Codec2", "MediaCodec", "OMX.", "libstagefright", "media.codec"};

    private DecodeGuard() { }

    private static SharedPreferences prefs() {
        return App.get().getSharedPreferences("decode-guard", Context.MODE_PRIVATE);
    }

    /** Called when a playback session starts, so a later crash can name the film that was playing. */
    public static void notePlaying(String key, String filmId) {
        prefs().edit().putString("last_key", key).putString("last_film", filmId == null ? "" : filmId)
                .putLong("last_at", System.currentTimeMillis()).apply();
    }

    /** The player and its decoders live in the main process; worker crashes are handled elsewhere. */
    public static boolean isDecoderCrash(String processName, String trace) {
        if (processName == null || trace == null || !App.get().getPackageName().equals(processName)) return false;
        for (String symbol : DECODER_SYMBOLS) if (trace.contains(symbol)) return true;
        return false;
    }

    public static void recordCrash(long crashAt) {
        SharedPreferences prefs = prefs();
        long lastAt = prefs.getLong("last_at", 0);
        // No playback context close to the crash: nothing safe to blame.
        if (lastAt <= 0 || crashAt < lastAt || crashAt - lastAt > CRASH_WINDOW_MS) return;
        prefs.edit().putString("pending_key", prefs.getString("last_key", ""))
                .putString("pending_film", prefs.getString("last_film", ""))
                .putLong("pending_at", crashAt).apply();
    }

    /** True exactly once for the film that crashed; other films stay on the user's setting. */
    public static boolean consume(String key, String filmId, long now) {
        SharedPreferences prefs = prefs();
        long pendingAt = prefs.getLong("pending_at", 0);
        if (pendingAt <= 0) return false;
        if (now - pendingAt > EXPIRY_MS || now < pendingAt) {
            clear(prefs);
            return false;
        }
        String pendingKey = prefs.getString("pending_key", ""), pendingFilm = prefs.getString("pending_film", "");
        boolean match = pendingFilm != null && !pendingFilm.isEmpty() && pendingFilm.equals(filmId)
                || pendingKey != null && !pendingKey.isEmpty() && pendingKey.equals(key);
        if (match) clear(prefs);
        return match;
    }

    private static void clear(SharedPreferences prefs) {
        prefs.edit().remove("pending_key").remove("pending_film").remove("pending_at").apply();
    }
}
