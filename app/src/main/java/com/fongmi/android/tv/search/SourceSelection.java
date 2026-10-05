package com.fongmi.android.tv.search;

import java.util.*;

/** Source choice compares sustainable throughput and HTTP latency, independently of quality tiers. */
public final class SourceSelection {
    public static final long FRESH_MS = 120_000;
    private SourceSelection() {}
    public static boolean fresh(long at, long now) { return at > 0 && now >= at && now - at <= FRESH_MS; }
    public static int grade(long speed, int bitrate, long at, long now) {
        return fresh(at, now) ? QualitySelection.smoothness(speed, bitrate, false) : 2;
    }
    private static long delay(long latency, long at, long now) {
        return fresh(at, now) && latency >= 0 ? latency : Long.MAX_VALUE;
    }
    public static int compare(long speedA, int bitrateA, long latencyA, long atA,
                              long speedB, int bitrateB, long latencyB, long atB, long now) {
        int grade = Integer.compare(grade(speedA, bitrateA, atA, now), grade(speedB, bitrateB, atB, now));
        if (grade != 0) return grade;
        // Capacity for the stream matters more than a fast reply that cannot sustain playback.
        double reserveA = bitrateA > 0 ? Math.min(4d, speedA * 8d / bitrateA) : 0;
        double reserveB = bitrateB > 0 ? Math.min(4d, speedB * 8d / bitrateB) : 0;
        int reserve = Double.compare(reserveB, reserveA);
        if (grade == 0 && reserve != 0) return reserve;
        int latency = Long.compare(delay(latencyA, atA, now), delay(latencyB, atB, now));
        return latency != 0 ? latency : Long.compare(speedB, speedA);
    }
    public static int compare(QualityOption a, QualityOption b, long now) {
        int metrics = compare(a.speed, a.bitrate, a.latencyMs, a.measuredAt,
                b.speed, b.bitrate, b.latencyMs, b.measuredAt, now);
        if (metrics != 0) return metrics;
        int recent = Long.compare(b.measuredAt, a.measuredAt);
        return recent != 0 ? recent : a.identity().compareTo(b.identity());
    }
    public static List<QualityOption> routes(List<QualityOption> values, long now) {
        Map<String, QualityOption> routes = new LinkedHashMap<>();
        for (QualityOption item : values) {
            if (!item.verified || !item.canAuto()) continue;
            QualityOption old = routes.get(item.route());
            if (old == null || compare(item, old, now) < 0) routes.put(item.route(), item);
        }
        List<QualityOption> result = new ArrayList<>(routes.values());
        result.sort((a, b) -> compare(a, b, now));
        return result;
    }
}
