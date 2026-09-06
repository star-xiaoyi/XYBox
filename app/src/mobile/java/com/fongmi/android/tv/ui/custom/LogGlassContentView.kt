package com.fongmi.android.tv.ui.custom

import android.content.Context
import android.util.AttributeSet
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fongmi.android.tv.R
import com.fongmi.android.tv.ui.custom.liquid.LiquidButton
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import com.kyant.shapes.RoundedRectangle

/** Settings-style viewer for XYBox's bounded persistent diagnostic log. */
class LogGlassContentView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AbstractComposeView(context, attrs, defStyleAttr) {

    fun interface OnActionListener {
        fun onAction(action: Int)
    }

    private data class LogState(
        val text: String = "",
        val summary: String = "",
        val loading: Boolean = true,
        val lineWrap: Boolean = true
    )

    private var state by mutableStateOf(LogState())
    private var clearConfirmation by mutableStateOf(false)
    private var actionListener: OnActionListener? = null

    init {
        setBackgroundColor(android.graphics.Color.TRANSPARENT)
        clipToPadding = false
    }

    fun setOnActionListener(listener: OnActionListener?) {
        actionListener = listener
    }

    fun showLoading() {
        state = state.copy(loading = true)
    }

    fun setLog(text: String?, summary: String?) {
        state = state.copy(text = text.orEmpty(), summary = summary.orEmpty(), loading = false)
    }

    fun isLineWrapEnabled(): Boolean = state.lineWrap

    fun toggleLineWrap(): Boolean {
        state = state.copy(lineWrap = !state.lineWrap)
        return state.lineWrap
    }

    @Composable
    override fun Content() {
        val light = !isSystemInDarkTheme()
        val palette = LogPalette(
            background = if (light) Color(0xFFF2F2F7) else Color.Black,
            backdropStart = if (light) Color(0xFFF0F5FB) else Color(0xFF070A10),
            backdropEnd = if (light) Color(0xFFF7F3EE) else Color(0xFF100D12),
            card = if (light) Color.White.copy(alpha = 0.76f) else Color(0xFF18181B).copy(alpha = 0.78f),
            glass = if (light) Color.White.copy(alpha = 0.36f) else Color(0xFF18181B).copy(alpha = 0.42f),
            text = if (light) Color(0xFF1C1C1E) else Color.White,
            secondary = if (light) Color(0xFF6C6C70) else Color(0xFF98989D)
        )
        val backdrop = rememberLayerBackdrop()
        val frameNanos = remember { mutableLongStateOf(0L) }

        LaunchedEffect(Unit) {
            while (true) withFrameNanos { frameNanos.longValue = it }
        }

        Box(Modifier.fillMaxSize().background(palette.background)) {
            Canvas(Modifier.fillMaxSize().layerBackdrop(backdrop)) {
                drawRect(Brush.linearGradient(listOf(palette.backdropStart, palette.backdropEnd)))
            }
            LogPage(backdrop, frameNanos, palette)
            if (clearConfirmation) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.14f))
                        .combinedClickable(
                            interactionSource = null,
                            indication = null,
                            onClick = { clearConfirmation = false }
                        )
                )
            }
            ClearConfirmation(
                visible = clearConfirmation,
                backdrop = backdrop,
                frameNanos = frameNanos,
                palette = palette,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }

    @Composable
    private fun LogPage(
        backdrop: Backdrop,
        frameNanos: androidx.compose.runtime.LongState,
        palette: LogPalette
    ) {
        val verticalScroll = rememberScrollState()
        val horizontalScroll = rememberScrollState()
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(start = 16.dp, top = 72.dp, end = 16.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(palette.card, androidx.compose.foundation.shape.RoundedCornerShape(18.dp))
                    .padding(horizontal = 15.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                BasicText(
                    stringResource(R.string.setting_log_summary),
                    style = TextStyle(palette.text, 15.sp, FontWeight.SemiBold)
                )
                BasicText(
                    if (state.loading) stringResource(R.string.log_loading) else state.summary,
                    style = TextStyle(palette.secondary, 12.sp, FontWeight.Medium)
                )
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LogActionButton(stringResource(R.string.log_refresh), ACTION_REFRESH, backdrop, frameNanos, palette, Modifier.weight(1f))
                LogActionButton(stringResource(R.string.log_copy), ACTION_COPY, backdrop, frameNanos, palette, Modifier.weight(1f))
                LogActionButton(stringResource(R.string.log_share), ACTION_SHARE, backdrop, frameNanos, palette, Modifier.weight(1f))
                LiquidButton(
                    onClick = { clearConfirmation = true },
                    backdrop = backdrop,
                    frameNanos = frameNanos,
                    surfaceColor = palette.glass,
                    modifier = Modifier.weight(1f).height(42.dp)
                ) {
                    BasicText(stringResource(R.string.log_clear), style = TextStyle(palette.text, 13.sp, FontWeight.SemiBold))
                }
            }

            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = { RoundedRectangle(18.dp) },
                        effects = {
                            colorControls(brightness = 0.06f, saturation = 1.16f)
                            blur(8.dp.toPx())
                            lens(12.dp.toPx(), 28.dp.toPx(), depthEffect = true)
                        },
                        highlight = { Highlight.Plain },
                        onDrawSurface = { drawRect(palette.glass) }
                    )
                    .padding(13.dp)
            ) {
                SelectionContainer {
                    BasicText(
                        text = when {
                            state.loading -> stringResource(R.string.log_loading)
                            state.text.isEmpty() -> stringResource(R.string.log_empty)
                            else -> state.text
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(verticalScroll)
                            .then(if (state.lineWrap) Modifier else Modifier.horizontalScroll(horizontalScroll)),
                        style = TextStyle(
                            color = palette.text,
                            fontSize = 11.sp,
                            lineHeight = 16.sp,
                            fontFamily = FontFamily.Monospace
                        ),
                        softWrap = state.lineWrap
                    )
                }
            }
        }
    }

    @Composable
    private fun LogActionButton(
        text: String,
        action: Int,
        backdrop: Backdrop,
        frameNanos: androidx.compose.runtime.LongState,
        palette: LogPalette,
        modifier: Modifier
    ) {
        LiquidButton(
            onClick = { dispatch(action) },
            backdrop = backdrop,
            frameNanos = frameNanos,
            surfaceColor = palette.glass,
            modifier = modifier.height(42.dp)
        ) {
            BasicText(text, style = TextStyle(palette.text, 13.sp, FontWeight.SemiBold))
        }
    }

    @Composable
    private fun ClearConfirmation(
        visible: Boolean,
        backdrop: Backdrop,
        frameNanos: androidx.compose.runtime.LongState,
        palette: LogPalette,
        modifier: Modifier
    ) {
        AnimatedVisibility(
            visible = visible,
            modifier = modifier.padding(start = 12.dp, end = 12.dp, bottom = 28.dp),
            enter = expandVertically(tween(220), expandFrom = Alignment.Bottom) + fadeIn(tween(150)),
            exit = shrinkVertically(tween(180), shrinkTowards = Alignment.Bottom) + fadeOut(tween(120))
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = { RoundedRectangle(28.dp) },
                        effects = {
                            colorControls(brightness = 0.08f, saturation = 1.28f)
                            blur(12.dp.toPx())
                            lens(18.dp.toPx(), 36.dp.toPx(), depthEffect = true)
                        },
                        highlight = { Highlight.Plain },
                        onDrawSurface = { drawRect(palette.glass) }
                    )
                    .combinedClickable(interactionSource = null, indication = null, onClick = {})
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                BasicText(stringResource(R.string.log_clear_confirm), style = TextStyle(palette.text, 16.sp, FontWeight.Bold))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LogActionButton(stringResource(R.string.log_cancel), ACTION_CANCEL, backdrop, frameNanos, palette, Modifier.weight(1f))
                    LogActionButton(stringResource(R.string.log_clear), ACTION_CLEAR, backdrop, frameNanos, palette, Modifier.weight(1f))
                }
            }
        }
    }

    private fun dispatch(action: Int) {
        if (action == ACTION_CANCEL) clearConfirmation = false
        else {
            if (action == ACTION_CLEAR) clearConfirmation = false
            actionListener?.onAction(action)
        }
    }

    companion object {
        const val ACTION_REFRESH = 1
        const val ACTION_COPY = 2
        const val ACTION_SHARE = 3
        const val ACTION_CLEAR = 4
        private const val ACTION_CANCEL = 5
    }
}

private data class LogPalette(
    val background: Color,
    val backdropStart: Color,
    val backdropEnd: Color,
    val card: Color,
    val glass: Color,
    val text: Color,
    val secondary: Color
)
