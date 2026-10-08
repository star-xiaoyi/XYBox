package com.fongmi.android.tv.api.loader;

/** Narrow quarantine for the plugin proven to abort the whole VM during construction. */
public final class SourcePluginPolicy {
    private SourcePluginPolicy() { }

    public static boolean canInstantiate(String api) {
        // 2026-10-08: two system tombstones both identify this exact constructor and
        // DexNative.getSpider's null JNI object. Java try/catch cannot catch SIGABRT.
        // Keep the imported configuration intact; other live/VOD providers are unaffected.
        return !"csp_LiveAiDouYuGuard".equals(api);
    }
}
