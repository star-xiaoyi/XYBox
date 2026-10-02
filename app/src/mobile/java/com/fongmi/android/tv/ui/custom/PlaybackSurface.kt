package com.fongmi.android.tv.ui.custom

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import com.fongmi.android.tv.utils.PlaybackUi

/** Skip the entire glass renderer in playback; preserve it on other pages. */
internal fun Modifier.playbackSurface(
    context: Context,
    color: Color,
    radius: Dp,
    glass: Modifier.() -> Modifier
): Modifier = if (PlaybackUi.isPlain(context)) {
    clip(RoundedCornerShape(radius)).background(color.copy(alpha = 1f))
} else glass(this)
