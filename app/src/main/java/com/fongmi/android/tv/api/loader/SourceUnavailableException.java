package com.fongmi.android.tv.api.loader;

import java.io.IOException;

/** Transport-level failure that says nothing about the site itself: never bench the site for it. */
public final class SourceUnavailableException extends IOException {
    public SourceUnavailableException(String message) { super(message); }
    public SourceUnavailableException(String message, Throwable cause) { super(message, cause); }

    /**
     * Binder flattens unknown exception classes, so service-side failures are matched by message
     * as a fallback. Keep these strings in sync with SourceService and SourceBridge.
     */
    public static boolean isInfrastructure(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof SourceUnavailableException) return true;
            String message = cause.getMessage();
            if (message == null) continue;
            if (message.contains("Isolated source unavailable")) return true;
            if (message.contains("Obsolete source request")) return true;
            if (message.contains("Source process exited during")) return true;
            if (message.contains("Isolated source connection timed out")) return true;
            if (message.contains("Unable to bind isolated sources")) return true;
            if (message.contains("Source RPC unavailable")) return true;
            if (message.contains("Missing source response pipe")) return true;
        }
        return false;
    }
}
