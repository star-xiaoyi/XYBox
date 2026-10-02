package com.fongmi.android.tv.ai

/** A fresh clock per voice entry, starting only after the robot settles in its first slot. */
internal class VoiceGlowMotion {
    private var startedAt = -1L
    private var settledX = 0f

    fun originX(currentX: Float): Float = if (startedAt < 0L) currentX else settledX

    fun phase(nowNanos: Long, settled: Boolean, currentX: Float): Float {
        if (startedAt < 0L && settled && nowNanos > 0L) {
            startedAt = nowNanos
            settledX = currentX
        }
        if (startedAt < 0L) return 0f
        return ((nowNanos - startedAt).coerceAtLeast(0L) % 2_800_000_000L) / 2_800_000_000f
    }
}
