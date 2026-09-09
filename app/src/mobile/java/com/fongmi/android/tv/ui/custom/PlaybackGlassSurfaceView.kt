package com.fongmi.android.tv.ui.custom

import android.content.Context
import android.util.AttributeSet
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.rememberCanvasBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.lens
import com.kyant.shapes.RoundedRectangle

/** 同一套播放器玻璃材质的无交互底板，供居中投屏窗和侧边选集窗共用。 */
class PlaybackGlassSurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AbstractComposeView(context, attrs, defStyleAttr) {

    init {
        setBackgroundColor(android.graphics.Color.TRANSPARENT)
        isClickable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    @Composable
    override fun Content() {
        val dark = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES
        val surface = if (dark) Color(0xFF18181B).copy(alpha = 0.93f) else Color(0xFFF8F8FA).copy(alpha = 0.90f)
        val backdrop = rememberCanvasBackdrop {
            drawRect(
                Brush.linearGradient(
                    if (dark) listOf(Color(0xFF2B2B30), Color(0xFF101012), Color(0xFF303035))
                    else listOf(Color(0xFFF9F9FB), Color(0xFFDDE0E6), Color.White)
                )
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                // 把模糊和透镜效果也裁进圆角，避免离屏图层在四角留下矩形虚影。
                .clip(RoundedCornerShape(24.dp))
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { RoundedRectangle(24.dp) },
                    effects = {
                        colorControls(brightness = 0.10f, saturation = 0.52f)
                        blur(30.dp.toPx())
                        lens(8.dp.toPx(), 22.dp.toPx(), depthEffect = true)
                    },
                    onDrawSurface = { drawRect(surface) }
                )
        )
    }
}
