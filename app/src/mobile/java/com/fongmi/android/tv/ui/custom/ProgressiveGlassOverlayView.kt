package com.fongmi.android.tv.ui.custom

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.unit.dp
import com.fongmi.android.tv.R
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

/** 只有背景，没有触摸区域；供固定分类栏下方的影片滚入时渐进模糊。 */
class ProgressiveGlassOverlayView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : AbstractComposeView(context, attrs) {
    private var sourceState by mutableStateOf<View?>(null)
    private var revealState by mutableFloatStateOf(0f)
    fun setBackdropView(view: View?) { sourceState = view }
    fun setScrollOffset(offset: Int) {
        revealState = (offset / (24f * resources.displayMetrics.density)).coerceIn(0f, 1f)
    }

    @Composable override fun Content() {
        val source = sourceState
        val frame = remember { mutableLongStateOf(0L) }
        val ownLocation = remember { IntArray(2) }
        val sourceLocation = remember { IntArray(2) }
        val base = Color(context.getColor(R.color.screen_background))
        val glass = base.copy(alpha = if (isSystemInDarkTheme()) 0.82f else 0.86f)
        val backdrop = rememberLayerBackdrop(onDraw = {
            frame.longValue
            drawRect(base)
            if (source != null && source.isAttachedToWindow && source.isShown) {
                getLocationInWindow(ownLocation)
                source.getLocationInWindow(sourceLocation)
                drawIntoCanvas { canvas ->
                    val native = canvas.nativeCanvas
                    val saved = native.save()
                    native.clipRect(0f, 0f, size.width, size.height)
                    native.translate((sourceLocation[0] - ownLocation[0] - source.scrollX).toFloat(),
                        (sourceLocation[1] - ownLocation[1] - source.scrollY).toFloat())
                    source.draw(native)
                    native.restoreToCount(saved)
                }
            }
        })
        LaunchedEffect(source) {
            while (source != null) withFrameNanos { frame.longValue = it }
        }
        Box(Modifier.fillMaxSize().clipToBounds().graphicsLayer { alpha = revealState }) {
            Box(Modifier.matchParentSize().layerBackdrop(backdrop))
            ProgressiveGlassSurface(Modifier.matchParentSize(), backdrop, base, glass, 36.dp, frame)
        }
    }
}
