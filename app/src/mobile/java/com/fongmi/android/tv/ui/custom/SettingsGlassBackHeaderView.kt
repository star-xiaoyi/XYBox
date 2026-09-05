package com.fongmi.android.tv.ui.custom

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fongmi.android.tv.R
import com.fongmi.android.tv.ui.custom.liquid.LiquidButton
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCanvasBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.shapes.RoundedRectangle

/** Shared liquid-glass header for secondary settings pages. */
class SettingsGlassBackHeaderView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AbstractComposeView(context, attrs, defStyleAttr) {

    private var titleState by mutableStateOf("")
    private var backdropViewState by mutableStateOf<View?>(null)
    private var renderingEnabledState by mutableStateOf(false)

    init {
        setBackgroundColor(android.graphics.Color.TRANSPARENT)
        clipToPadding = false
    }

    fun setTitle(title: CharSequence?) {
        titleState = title?.toString().orEmpty()
    }

    fun setBackdropView(view: View?) {
        backdropViewState = view
    }

    fun setRenderingEnabled(enabled: Boolean) {
        renderingEnabledState = enabled
    }

    @Composable
    override fun Content() {
        CurrentHeaderContent()
    }

    @Composable
    private fun CurrentHeaderContent() {
        val light = !isSystemInDarkTheme()
        val background = Color(context.getColor(R.color.screen_background))
        val glass = if (light) Color.White.copy(alpha = 0.38f)
        else Color(0xFF202024).copy(alpha = 0.46f)
        val text = Color(context.getColor(R.color.text_primary))
        val frameNanos = remember { mutableLongStateOf(0L) }
        val backdrop = rememberCanvasBackdrop {
            drawRect(
                Brush.linearGradient(
                    if (light) {
                        listOf(Color(0xFFF7F9FC), Color(0xFFE4E7ED), Color(0xFFFFFBF5))
                    } else {
                        listOf(Color(0xFF20242B), Color(0xFF090A0D), Color(0xFF282229))
                    }
                )
            )
        }

        LaunchedEffect(Unit) {
            while (true) withFrameNanos { frameNanos.longValue = it }
        }

        Box(Modifier.fillMaxSize().background(background)) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LiquidButton(
                    onClick = { performClick() },
                    backdrop = backdrop,
                    frameNanos = frameNanos,
                    surfaceColor = glass,
                    dragResponse = 0.42f,
                    modifier = Modifier.size(40.dp)
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_back),
                        contentDescription = stringResource(R.string.back),
                        colorFilter = ColorFilter.tint(text),
                        modifier = Modifier.size(19.dp)
                    )
                }

                BasicText(
                    text = titleState,
                    modifier = Modifier.padding(start = 12.dp),
                    style = TextStyle(
                        color = text,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }
    }

    @Composable
    private fun LegacyHeaderContent() {
        val light = !isSystemInDarkTheme()
        val container = if (light) Color(0xFFF2F2F7) else Color.Black
        val glass = if (light) Color.White else Color(0xFF1C1C1E)
        val text = if (light) Color(0xFF1C1C1E) else Color.White
        val frameNanos = remember { mutableLongStateOf(0L) }
        val backdrop = rememberLayerBackdrop()
        val headerLocation = remember { IntArray(2) }
        val sourceLocation = remember { IntArray(2) }
        val sourceView = backdropViewState

        LaunchedEffect(renderingEnabledState, sourceView) {
            while (renderingEnabledState && sourceView != null) {
                withFrameNanos { frameNanos.longValue = it }
            }
        }

        Box(Modifier.fillMaxSize().background(container)) {
            Canvas(Modifier.size(0.dp).layerBackdrop(backdrop)) {
                frameNanos.longValue
                if (sourceView != null && sourceView.isAttachedToWindow) {
                    this@SettingsGlassBackHeaderView.getLocationInWindow(headerLocation)
                    sourceView.getLocationInWindow(sourceLocation)
                    drawIntoCanvas { canvas ->
                        val nativeCanvas = canvas.nativeCanvas
                        val saveCount = nativeCanvas.save()
                        nativeCanvas.translate(
                            (sourceLocation[0] - headerLocation[0]).toFloat(),
                            (sourceLocation[1] - headerLocation[1]).toFloat()
                        )
                        sourceView.draw(nativeCanvas)
                        nativeCanvas.restoreToCount(saveCount)
                    }
                }
            }

            Box(
                Modifier
                    .size(0.dp)
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = { RoundedRectangle(0.dp) },
                        effects = {
                            vibrancy()
                            blur(8.dp.toPx())
                            lens(24.dp.toPx(), 24.dp.toPx())
                        },
                        onDrawBehind = { frameNanos.longValue },
                        onDrawSurface = { drawRect(container) }
                    )
            )

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LiquidButton(
                    onClick = { performClick() },
                    backdrop = backdrop,
                    frameNanos = frameNanos,
                    surfaceColor = glass,
                    dragResponse = 0.42f,
                    modifier = Modifier.size(40.dp)
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_back),
                        contentDescription = stringResource(R.string.back),
                        colorFilter = ColorFilter.tint(text),
                        modifier = Modifier.size(19.dp)
                    )
                }

                BasicText(
                    text = titleState,
                    modifier = Modifier.padding(start = 12.dp),
                    style = TextStyle(
                        color = text,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }
    }
}
