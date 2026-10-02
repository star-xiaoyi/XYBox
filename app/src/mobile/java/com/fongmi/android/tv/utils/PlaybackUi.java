package com.fongmi.android.tv.utils;

import android.content.Context;
import android.content.ContextWrapper;

import com.fongmi.android.tv.ui.activity.LiveActivity;
import com.fongmi.android.tv.ui.activity.VideoActivity;

/** Playback screens use flat controls, including dialogs with a themed ContextWrapper. */
public final class PlaybackUi {
    private PlaybackUi() { }

    public static boolean isPlain(Context context) {
        while (context != null) {
            if (context instanceof VideoActivity || context instanceof LiveActivity) return true;
            if (!(context instanceof ContextWrapper)) return false;
            Context base = ((ContextWrapper) context).getBaseContext();
            if (base == context) return false;
            context = base;
        }
        return false;
    }
}
