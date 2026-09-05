package com.fongmi.android.tv.ui.custom

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fongmi.android.tv.R
import com.fongmi.android.tv.bean.Filter
import com.fongmi.android.tv.bean.Value
import com.fongmi.android.tv.impl.FilterCallback
import com.fongmi.android.tv.ui.custom.liquid.LiquidButton
import com.fongmi.android.tv.utils.ThemeUtil
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.lens
import com.kyant.shapes.RoundedRectangle

/** Home filter sheet using the same in-page backdrop and control-center buttons as the showcase. */
class LiquidGlassFilterView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AbstractComposeView(context, attrs, defStyleAttr) {

    private var filtersState by mutableStateOf<List<Filter>>(emptyList())
    private var callbackState by mutableStateOf<FilterCallback?>(null)
    private var backdropViewState by mutableStateOf<View?>(null)
    private var visibleState by mutableStateOf(false)
    private var selectedValuesState by mutableStateOf<Map<String, String>>(emptyMap())
    private var visibilityChangedListener: OnVisibilityChangedListener? = null

    fun interface OnVisibilityChangedListener {
        fun onVisibilityChanged(visible: Boolean)
    }

    init {
        setBackgroundColor(android.graphics.Color.TRANSPARENT)
        clipToPadding = false
        isClickable = false
        isFocusable = false
    }

    fun show(filters: List<Filter>?, callback: FilterCallback?, backdropView: View?) {
        filtersState = filters.orEmpty()
        callbackState = callback
        backdropViewState = backdropView
        selectedValuesState = selectionSnapshot(filtersState)
        setVisible(filtersState.isNotEmpty())
    }

    fun dismiss() {
        setVisible(false)
    }

    fun isPanelVisible(): Boolean = visibleState

    fun setOnVisibilityChangedListener(listener: OnVisibilityChangedListener?) {
        visibilityChangedListener = listener
    }

    private fun select(filter: Filter, value: Value) {
        filter.value.forEach { it.setActivated(value) }
        // Value 是普通 Java bean；用新的不可变快照驱动 Compose，保证点击当帧就换色。
        selectedValuesState = selectionSnapshot(filtersState)
        callbackState?.setFilter(filter.key, value)
    }

    private fun selectionSnapshot(filters: List<Filter>): Map<String, String> = buildMap {
        filters.forEach { filter ->
            filter.value.firstOrNull { it.isActivated }?.let { put(filter.key, it.v) }
        }
    }

    private fun setVisible(visible: Boolean) {
        if (visibleState == visible) return
        visibleState = visible
        visibilityChangedListener?.onVisibilityChanged(visible)
    }

    override fun onDetachedFromWindow() {
        if (visibleState) {
            visibleState = false
            visibilityChangedListener?.onVisibilityChanged(false)
        }
        super.onDetachedFromWindow()
    }

    @Composable
    override fun Content() {
        val light = !isSystemInDarkTheme()
        val palette = FilterPalette(
            panel = if (light) Color(0xFFF8F8FA).copy(alpha = 0.91f)
            else Color(0xFF18181B).copy(alpha = 0.93f),
            glass = if (light) Color.White.copy(alpha = 0.86f)
            else Color(0xFF242428).copy(alpha = 0.88f),
            text = if (light) Color(0xFF1C1C1E) else Color.White,
            secondary = if (light) Color(0xFF6C6C70) else Color(0xFF98989D),
            accent = Color(context.getColor(ThemeUtil.getAccentColorResource()))
        )
        val backdrop = rememberLayerBackdrop()
        val frameNanos = remember { mutableLongStateOf(0L) }
        val panelLocation = remember { IntArray(2) }
        val sourceLocation = remember { IntArray(2) }
        val sourceView = backdropViewState
        LaunchedEffect(visibleState, sourceView) {
            while (visibleState && sourceView != null) {
                withFrameNanos { frameNanos.longValue = it }
            }
        }

        Box(Modifier.fillMaxSize()) {
            if (visibleState) {
                Canvas(Modifier.fillMaxSize().layerBackdrop(backdrop)) {
                    frameNanos.longValue
                    if (sourceView != null && sourceView.isAttachedToWindow) {
                        this@LiquidGlassFilterView.getLocationInWindow(panelLocation)
                        sourceView.getLocationInWindow(sourceLocation)
                        drawIntoCanvas { canvas ->
                            val nativeCanvas = canvas.nativeCanvas
                            val saveCount = nativeCanvas.save()
                            nativeCanvas.translate(
                                (sourceLocation[0] - panelLocation[0]).toFloat(),
                                (sourceLocation[1] - panelLocation[1]).toFloat()
                            )
                            sourceView.draw(nativeCanvas)
                            nativeCanvas.restoreToCount(saveCount)
                        }
                    }
                }
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = if (light) 0.12f else 0.22f))
                        .combinedClickable(
                            interactionSource = null,
                            indication = null,
                            onClick = ::dismiss
                        )
                )
            }

            FilterSheet(
                visible = visibleState,
                filters = filtersState,
                backdrop = backdrop,
                frameNanos = frameNanos,
                palette = palette,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }

    @Composable
    private fun FilterSheet(
        visible: Boolean,
        filters: List<Filter>,
        backdrop: Backdrop,
        frameNanos: androidx.compose.runtime.LongState,
        palette: FilterPalette,
        modifier: Modifier
    ) {
        AnimatedVisibility(
            visible = visible,
            // 底栏本身是 52dp 高、上下各留 7dp；再留 12dp 视觉间距。
            // 系统手势条高度按设备实时读取，避免固定 104dp 在部分手机上把卡片顶得太高。
            modifier = modifier
                .navigationBarsPadding()
                .padding(start = 12.dp, end = 12.dp, bottom = 78.dp),
            enter = expandVertically(tween(240), expandFrom = Alignment.Bottom) + fadeIn(tween(160)),
            exit = shrinkVertically(tween(190), shrinkTowards = Alignment.Bottom) + fadeOut(tween(120))
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 560.dp)
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = { RoundedRectangle(28.dp) },
                        effects = {
                            colorControls(brightness = 0.10f, saturation = 0.52f)
                            blur(36.dp.toPx())
                            lens(8.dp.toPx(), 24.dp.toPx(), depthEffect = true)
                        },
                        onDrawSurface = { drawRect(palette.panel) }
                    )
                    .combinedClickable(
                        interactionSource = null,
                        indication = null,
                        onClick = {}
                    )
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BasicText(
                        text = stringResource(R.string.vod_filter),
                        modifier = Modifier.weight(1f),
                        style = TextStyle(palette.text, 20.sp, FontWeight.Bold)
                    )
                    LiquidButton(
                        onClick = ::dismiss,
                        backdrop = backdrop,
                        frameNanos = frameNanos,
                        surfaceColor = palette.glass,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Image(
                            painter = painterResource(R.drawable.ic_action_close),
                            contentDescription = stringResource(R.string.action_close_search),
                            colorFilter = ColorFilter.tint(palette.text),
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Column(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    filters.forEach { filter ->
                        FilterGroup(filter, selectedValuesState, backdrop, frameNanos, palette)
                    }
                }
            }
        }
    }

    @Composable
    private fun FilterGroup(
        filter: Filter,
        selectedValues: Map<String, String>,
        backdrop: Backdrop,
        frameNanos: androidx.compose.runtime.LongState,
        palette: FilterPalette
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (filter.name.isNotEmpty()) {
                BasicText(
                    filter.name,
                    style = TextStyle(palette.secondary, 13.sp, FontWeight.SemiBold)
                )
            }
            filter.value.chunked(3).forEach { values ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    values.forEach { value ->
                        val selected = selectedValues[filter.key] == value.v
                        val selectedText = if (palette.accent.luminance() > 0.58f) Color(0xFF1C1C1E) else Color.White
                        LiquidButton(
                            onClick = { select(filter, value) },
                            backdrop = backdrop,
                            frameNanos = frameNanos,
                            surfaceColor = if (selected) palette.accent.copy(alpha = 0.88f) else palette.glass,
                            modifier = Modifier.weight(1f).height(48.dp)
                        ) {
                            BasicText(
                                text = value.n,
                                modifier = Modifier.padding(horizontal = 6.dp),
                                style = TextStyle(
                                    color = if (selected) selectedText else palette.text,
                                    fontSize = 13.sp,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    repeat(3 - values.size) { Spacer(Modifier.weight(1f).height(48.dp)) }
                }
            }
        }
    }
}

private data class FilterPalette(
    val panel: Color,
    val glass: Color,
    val text: Color,
    val secondary: Color,
    val accent: Color
)
