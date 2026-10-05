package com.fongmi.android.tv.search;

import java.util.List;

/** Lists are empty between loading a detail and activating its first episode. */
public final class PlaybackSelection {
    private PlaybackSelection() {}
    public static <T> T item(List<T> items, int position) {
        return position >= 0 && position < items.size() ? items.get(position) : null;
    }
}
