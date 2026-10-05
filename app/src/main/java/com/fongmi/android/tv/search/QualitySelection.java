package com.fongmi.android.tv.search;

import java.util.*;

/** Snapshot selection: throughput first; resolution only breaks ties among sustainable streams. */
public final class QualitySelection {
    private QualitySelection() {}
    public static int smoothness(long bytesPerSecond, int bitrate, boolean currentHealthy) {
        if (currentHealthy) return 0;
        if (bytesPerSecond <= 0) return 2;
        if (bitrate <= 0) return 1;
        return bytesPerSecond * 8d >= bitrate * 1.5d ? 0 : 3;
    }
    private static int grade(QualityOption item, String current, boolean healthy) {
        return smoothness(item.speed, item.bitrate, healthy && item.identity().equals(current));
    }
    public static List<QualityOption> tiers(List<QualityOption> values, String current, boolean healthy) {
        Map<Integer, QualityOption> grouped = new TreeMap<>(Collections.reverseOrder());
        for (QualityOption item : values) {
            if (!item.verified || item.rank() <= 0 || !item.isAvailable()) continue;
            QualityOption old = grouped.get(item.tierHeight());
            if (old == null || compareSameTier(item, old, current, healthy) < 0) grouped.put(item.tierHeight(), item);
        }
        return new ArrayList<>(grouped.values());
    }
    private static int compareSameTier(QualityOption a, QualityOption b, String current, boolean healthy) {
        int grade = Integer.compare(grade(a, current, healthy), grade(b, current, healthy));
        if (grade != 0) return grade;
        int speed = Long.compare(b.speed, a.speed);
        return speed != 0 ? speed : a.identity().compareTo(b.identity());
    }
    public static QualityOption best(List<QualityOption> values, String current, boolean healthy) {
        if (healthy) for (QualityOption item : values)
            if (item.verified && item.rank() > 0 && item.canAuto() && item.identity().equals(current)) return item;
        QualityOption best = null;
        for (QualityOption item : values) {
            if (!item.verified || item.rank() <= 0 || !item.canAuto()) continue;
            if (best == null || grade(item, current, healthy) < grade(best, current, healthy)
                    || grade(item, current, healthy) == grade(best, current, healthy)
                    && (item.rank() > best.rank() || item.rank() == best.rank() && item.speed > best.speed)) best = item;
        }
        return best;
    }
}
