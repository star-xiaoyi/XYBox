package com.github.catvod.utils;

import android.util.Log;

public class Logger {
    private static final String TAG = "XMBOX";
    private static volatile Sink sink;

    public interface Sink {
        void log(int priority, String tag, String message, Throwable throwable);
    }

    public static void setSink(Sink value) {
        sink = value;
    }

    private static void dispatch(int priority, String msg, Throwable tr) {
        dispatch(priority, TAG, msg, tr);
    }

    private static void dispatch(int priority, String tag, String msg, Throwable tr) {
        Sink value = sink;
        if (value == null) return;
        try {
            value.log(priority, tag, msg, tr);
        } catch (Throwable ignored) {
            // Logging must never be able to break the application path it observes.
        }
    }

    public static void d(String msg) {
        Log.d(TAG, msg);
        dispatch(Log.DEBUG, msg, null);
    }

    public static void d(String tag, String msg) {
        Log.d(tag, msg);
        dispatch(Log.DEBUG, tag, msg, null);
    }

    public static void e(String msg) {
        Log.e(TAG, msg);
        dispatch(Log.ERROR, msg, null);
    }

    public static void e(String tag, String msg) {
        Log.e(tag, msg);
        dispatch(Log.ERROR, tag, msg, null);
    }

    public static void e(String msg, Throwable tr) {
        Log.e(TAG, msg, tr);
        dispatch(Log.ERROR, msg, tr);
    }

    public static void e(String tag, String msg, Throwable tr) {
        Log.e(tag, msg, tr);
        dispatch(Log.ERROR, tag, msg, tr);
    }

    public static void i(String msg) {
        Log.i(TAG, msg);
        dispatch(Log.INFO, msg, null);
    }

    public static void v(String msg) {
        Log.v(TAG, msg);
        dispatch(Log.VERBOSE, msg, null);
    }

    public static void w(String msg) {
        Log.w(TAG, msg);
        dispatch(Log.WARN, msg, null);
    }

    public static void w(String tag, String msg) {
        Log.w(tag, msg);
        dispatch(Log.WARN, tag, msg, null);
    }

    public static void w(String msg, Throwable tr) {
        Log.w(TAG, msg, tr);
        dispatch(Log.WARN, msg, tr);
    }
}
