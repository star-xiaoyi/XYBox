package com.github.catvod.crawler;

import android.text.TextUtils;

import com.github.catvod.utils.Logger;

public class SpiderDebug {

    private static final String TAG = SpiderDebug.class.getSimpleName();

    public static void log(Throwable th) {
        if (th != null) Logger.e("Error", th);
    }

    public static void log(String msg) {
        if (!TextUtils.isEmpty(msg)) Logger.d(msg);
    }
}
