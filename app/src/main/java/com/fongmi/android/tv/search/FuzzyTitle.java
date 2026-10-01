package com.fongmi.android.tv.search;

import java.util.ArrayList;
import java.util.List;

/** Bounded fallback terms and edit-distance ranking; never used as proof for automatic playback. */
public final class FuzzyTitle {
    public static List<String> queries(String text) {
        List<String> result = new ArrayList<>();
        String value = text.replaceAll("[\\s\\p{P}\\p{S}]", "");
        if (value.length() < 4 || value.length() > 30) return result;
        int split = value.lastIndexOf('的');
        String tail = split >= 1 && value.length() - split >= 3 ? value.substring(split + 1) : value.substring(value.length() - 3);
        if (!tail.equals(value) && tail.length() >= 2) result.add(tail);
        String head = value.substring(0, Math.min(3, value.length()));
        if (!result.contains(head) && !head.equals(value)) result.add(head);
        return result;
    }
    public static double similarity(String a, String b) {
        if (a.isEmpty() || b.isEmpty()) return 0;
        int[] previous = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) previous[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            int[] row = new int[b.length() + 1]; row[0] = i;
            for (int j = 1; j <= b.length(); j++) row[j] = Math.min(Math.min(row[j - 1] + 1, previous[j] + 1), previous[j - 1] + (a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1));
            previous = row;
        }
        return 1.0 - (double) previous[b.length()] / Math.max(a.length(), b.length());
    }
    private FuzzyTitle() { }
}
