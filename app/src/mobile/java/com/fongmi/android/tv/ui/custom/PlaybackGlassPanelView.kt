package com.fongmi.android.tv.ui.custom

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.core.view.doOnNextLayout
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fongmi.android.tv.ui.custom.liquid.LiquidButton
import com.fongmi.android.tv.utils.ThemeUtil
import com.kyant.backdrop.backdrops.rememberCanvasBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.lens
import com.kyant.shapes.RoundedRectangle
import kotlin.math.roundToInt

/**
 * 播放页共用的锚点玻璃菜单。更多菜单、倍速、画面比例、解析和轨道选择都走这里，
 * 位置与大小可以不同，但玻璃材质、按钮形变和选中反馈完全一致。
 */
class PlaybackGlassPanelView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AbstractComposeView(context, attrs, defStyleAttr) {

    fun interface OnItemClickListener {
        fun onItemClick(id: Int)
    }

    private data class PanelItem(val id: Int, val label: String, val icon: Int)

    private var visibleState by mutableStateOf(false)
    private var titleState by mutableStateOf("")
    private var itemsState by mutableStateOf<List<PanelItem>>(emptyList())
    private var selectedState by mutableStateOf<Set<Int>>(emptySet())
    private var panelWidthState by mutableStateOf(220)
    private var requestedHeightState = 260
    private var panelHeightState by mutableStateOf(64)
    private var panelXState by mutableStateOf(12)
    private var panelYState by mutableStateOf(12)
    private var listener: OnItemClickListener? = null
    private var dismissListener: Runnable? = null
    private val hideRunnable = Runnable {
        if (!visibleState) visibility = View.GONE
    }

    init {
        setBackgroundColor(android.graphics.Color.TRANSPARENT)
        clipChildren = false
        clipToPadding = false
        visibility = View.GONE
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
    }

    fun show(
        anchor: View,
        title: String?,
        ids: IntArray,
        labels: Array<String>,
        icons: IntArray?,
        selectedIds: IntArray?,
        widthDp: Int,
        heightDp: Int,
        listener: OnItemClickListener
    ) {
        if (ids.isEmpty() || labels.size != ids.size) return
        removeCallbacks(hideRunnable)
        this.listener = listener
        titleState = title.orEmpty()
        itemsState = ids.indices.map { index ->
            PanelItem(ids[index], labels[index], icons?.getOrNull(index) ?: 0)
        }
        selectedState = selectedIds?.toSet().orEmpty()
        panelWidthState = widthDp.coerceIn(176, 280)
        requestedHeightState = heightDp.coerceIn(160, 320)
        panelHeightState = requestedHeightState
        visibility = View.VISIBLE
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        visibleState = false
        // Activity 旋转时不会重建，当前 width/height 可能仍是上一方向的旧值。
        // 无论是否已有尺寸，都强制等下一轮布局完成后再按锚点定位。
        requestLayout()
        doOnNextLayout {
            place(anchor)
            visibleState = true
        }
    }

    fun dismiss() {
        if (!visibleState) return
        visibleState = false
        listener = null
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        postDelayed(hideRunnable, 150)
        dismissListener?.run()
    }

    fun setOnPanelDismissListener(listener: Runnable?) {
        dismissListener = listener
    }

    fun isPanelVisible(): Boolean = visibleState

    private fun place(anchor: View) {
        val density = resources.displayMetrics.density
        val own = IntArray(2)
        val target = IntArray(2)
        getLocationInWindow(own)
        anchor.getLocationInWindow(target)
        val margin = (12 * density).roundToInt()
        val gap = (8 * density).roundToInt()
        val panelWidth = (panelWidthState * density).roundToInt()
        val panelHeight = (panelHeightState * density).roundToInt()
        val anchorLeft = target[0] - own[0]
        val anchorTop = target[1] - own[1]
        val anchorBottom = anchorTop + anchor.height
        val maxX = (width - panelWidth - margin).coerceAtLeast(margin)
        // 常规情况让按钮落在弹窗水平中心；只有靠近屏幕边缘时才平移弹窗以保证完整显示。
        panelXState = (anchorLeft + anchor.width / 2 - panelWidth / 2).coerceIn(margin, maxX)

        // 大屏沿用统一的固定高度；小屏以屏幕四分之一作为舒适留白，并让内容区内部滚动。
        val comfort = (height * 0.25f).roundToInt().coerceAtLeast(margin)
        val desiredHeight = (requestedHeightState * density).roundToInt()
        val minimumHeight = (160 * density).roundToInt()
        val spaceAbove = anchorTop - gap - comfort
        val spaceBelow = height - comfort - anchorBottom - gap
        val placeAbove = spaceAbove >= minimumHeight || spaceAbove >= spaceBelow
        val preferredSpace = if (placeAbove) spaceAbove else spaceBelow
        val absoluteMaximum = (height - margin * 2).coerceAtLeast(1)
        val actualHeight = desiredHeight.coerceAtMost(preferredSpace.coerceAtLeast(minimumHeight)).coerceAtMost(absoluteMaximum)
        panelHeightState = (actualHeight / density).roundToInt().coerceAtLeast(1)
        val rawY = if (placeAbove) anchorTop - actualHeight - gap else anchorBottom + gap
        panelYState = rawY.coerceIn(margin, (height - actualHeight - margin).coerceAtLeast(margin))
    }

    @Composable
    override fun Content() {
        val light = !isSystemInDarkTheme()
        val accent = Color(context.getColor(ThemeUtil.getAccentColorResource()))
        val palette = PanelPalette(
            panel = if (light) Color(0xFFF8F8FA).copy(alpha = 0.90f) else Color(0xFF18181B).copy(alpha = 0.93f),
            glass = if (light) Color.White.copy(alpha = 0.82f) else Color(0xFF242428).copy(alpha = 0.86f),
            text = if (light) Color(0xFF1C1C1E) else Color.White,
            secondary = if (light) Color(0xFF6C6C70) else Color(0xFFAAAAB0),
            accent = accent
        )
        val frameNanos = remember { mutableLongStateOf(0L) }
        val backdrop = rememberCanvasBackdrop {
            drawRect(
                Brush.linearGradient(
                    if (light) listOf(Color(0xFFF9F9FB), Color(0xFFDDE0E6), Color.White)
                    else listOf(Color(0xFF2B2B30), Color(0xFF101012), Color(0xFF303035))
                )
            )
        }

        Box(Modifier.fillMaxSize()) {
            if (visibleState) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.08f))
                        .combinedClickable(interactionSource = null, indication = null, onClick = ::dismiss)
                )
            }
            AnimatedVisibility(
                visible = visibleState,
                modifier = Modifier.offset { IntOffset(panelXState, panelYState) },
                enter = fadeIn(tween(120)) + scaleIn(tween(150), initialScale = 0.94f),
                exit = fadeOut(tween(100)) + scaleOut(tween(120), targetScale = 0.96f)
            ) {
                Column(
                    Modifier
                        .width(panelWidthState.dp)
                        .height(panelHeightState.dp)
                        .drawBackdrop(
                            backdrop = backdrop,
                            shape = { RoundedRectangle(24.dp) },
                            effects = {
                                colorControls(brightness = 0.10f, saturation = 0.52f)
                                blur(30.dp.toPx())
                                lens(8.dp.toPx(), 22.dp.toPx(), depthEffect = true)
                            },
                            onDrawSurface = { drawRect(palette.panel) }
                        )
                        .combinedClickable(interactionSource = null, indication = null, onClick = {})
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (titleState.isNotEmpty()) {
                        BasicText(
                            text = titleState,
                            modifier = Modifier.padding(start = 10.dp, top = 5.dp, end = 10.dp, bottom = 5.dp),
                            style = TextStyle(palette.text, 15.sp, FontWeight.Bold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Column(
                        Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        itemsState.forEach { item ->
                            val selected = selectedState.contains(item.id)
                            LiquidButton(
                                onClick = { listener?.onItemClick(item.id) },
                                backdrop = backdrop,
                                frameNanos = frameNanos,
                                surfaceColor = if (selected) palette.accent.copy(alpha = 0.88f) else palette.glass,
                                modifier = Modifier.fillMaxWidth().height(48.dp)
                            ) {
                                Row(
                                    Modifier.fillMaxSize().padding(horizontal = 13.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(11.dp)
                                ) {
                                    if (item.icon != 0) {
                                        Image(
                                            painter = painterResource(item.icon),
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp),
                                            colorFilter = ColorFilter.tint(if (selected) palette.selectedText else palette.secondary)
                                        )
                                    }
                                    BasicText(
                                        text = item.label,
                                        modifier = Modifier.weight(1f),
                                        style = TextStyle(
                                            color = if (selected) palette.selectedText else palette.text,
                                            fontSize = 14.sp,
                                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (selected) {
                                        BasicText("✓", style = TextStyle(palette.selectedText, 16.sp, FontWeight.Bold))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class PanelPalette(
    val panel: Color,
    val glass: Color,
    val text: Color,
    val secondary: Color,
    val accent: Color
) {
    val selectedText: Color
        get() = if (accent.luminance() > 0.58f) Color(0xFF1C1C1E) else Color.White
}
