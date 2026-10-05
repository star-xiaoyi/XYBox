package com.fongmi.android.tv.bean;

import java.util.Map;
import java.util.TreeMap;

/** Only explicit source/site switches, keyed by configuration-address identity rather than database id. */
public final class SyncSourcePreference {
    public boolean enabled;
    public long updatedAt;

    public SyncSourcePreference() { }
    public SyncSourcePreference(boolean enabled, long updatedAt) { this.enabled = enabled; this.updatedAt = updatedAt; }

    public static boolean validKey(String key) {
        return key != null && key.length() <= 512 && (key.matches("source:xy:[0-9a-f]{32}:")
                || key.matches("site:xy:[0-9a-f]{32}:.+") || key.matches("priority:xy:[0-9a-f]{32}:.+"));
    }

    public static Map<String, SyncSourcePreference> merge(Map<String, SyncSourcePreference> remote,
                                                         Map<String, SyncSourcePreference> local) {
        Map<String, SyncSourcePreference> merged = new TreeMap<>();
        add(merged, remote);
        add(merged, local);
        return merged;
    }

    private static void add(Map<String, SyncSourcePreference> output, Map<String, SyncSourcePreference> input) {
        if (input == null) return;
        for (Map.Entry<String, SyncSourcePreference> entry : input.entrySet()) {
            SyncSourcePreference value = entry.getValue(), previous = output.get(entry.getKey());
            if (!validKey(entry.getKey()) || value == null || value.updatedAt <= 0) continue;
            if (previous == null || value.updatedAt > previous.updatedAt)
                output.put(entry.getKey(), new SyncSourcePreference(value.enabled, value.updatedAt));
        }
    }
}
