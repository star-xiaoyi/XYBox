package com.fongmi.android.tv.ui.custom

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnNextLayout
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fongmi.android.tv.R
import kotlin.math.roundToInt

/** Inline playback controls: unfold from the trigger, fit the options, scroll only at the cap. */
class PlaybackGlassPanelView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : AbstractComposeView(context, attrs, defStyleAttr) {
    fun interface OnItemClickListener { fun onItemClick(id: Int) }
    fun interface OnVisibilityChangedListener { fun onVisibilityChanged(visible: Boolean) }
    private data class PanelItem(val id: Int, val label: String, val icon: Int, val subtitle: String = "")

    private var visibleState by mutableStateOf(false)
    private var titleState by mutableStateOf("")
    private var itemsState by mutableStateOf<List<PanelItem>>(emptyList())
    private var selectedState by mutableStateOf<Set<Int>>(emptySet())
    private var selectionMenuState by mutableStateOf(false)
    private var sourceMenuState by mutableStateOf(false)
    private var contentEpoch by mutableIntStateOf(0)
    private var bounds by mutableStateOf(PlaybackPanelLayout.Bounds(12f, 12f, 220f, 120f, true, 48f, 46f, 0f, false))
    private var requestedWidth = 220f
    private var maximumHeight = 320f
    private var requestGeneration = 0
    private var pendingOpen = false
    private var currentAnchor: View? = null
    private var listener: OnItemClickListener? = null
    private var dismissListener: Runnable? = null
    private var visibilityChangedListener: OnVisibilityChangedListener? = null
    private val hideRunnable = Runnable { if (!visibleState && !pendingOpen) visibility = View.GONE }

    init {
        setBackgroundColor(android.graphics.Color.TRANSPARENT)
        clipChildren = false
        clipToPadding = false
        visibility = View.GONE
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
    }

    fun show(
        anchor: View, title: String?, ids: IntArray, labels: Array<String>, icons: IntArray?,
        selectedIds: IntArray?, widthDp: Int, heightDp: Int, listener: OnItemClickListener
    ) {
        if (ids.isEmpty() || labels.size != ids.size) return
        val token = ++requestGeneration
        removeCallbacks(hideRunnable)
        this.listener = listener
        currentAnchor = anchor
        titleState = title.orEmpty()
        itemsState = ids.indices.map { PanelItem(ids[it], labels[it], icons?.getOrNull(it) ?: 0) }
        selectedState = selectedIds?.toSet().orEmpty()
        selectionMenuState = selectedIds != null
        sourceMenuState = false
        requestedWidth = widthDp.toFloat()
        maximumHeight = heightDp.toFloat()
        contentEpoch++
        pendingOpen = true
        visibleState = false
        visibility = View.VISIBLE
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        // Rotation and small-window changes are handled after real layout, using window coordinates.
        requestLayout()
        doOnNextLayout {
            if (token != requestGeneration || !pendingOpen) return@doOnNextLayout
            if (!isAttachedToWindow || !anchor.isAttachedToWindow || width <= 0 || height <= 0) {
                dismiss()
                return@doOnNextLayout
            }
            place(anchor)
            pendingOpen = false
            visibleState = true
            visibilityChangedListener?.onVisibilityChanged(true)
        }
    }

    fun dismiss() {
        if (!visibleState && !pendingOpen) return
        ++requestGeneration
        pendingOpen = false
        visibleState = false
        currentAnchor = null
        listener = null
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        removeCallbacks(hideRunnable)
        postDelayed(hideRunnable, 190)
        visibilityChangedListener?.onVisibilityChanged(false)
        dismissListener?.run()
    }

    fun setOnPanelDismissListener(listener: Runnable?) { dismissListener = listener }
    fun setOnVisibilityChangedListener(listener: OnVisibilityChangedListener?) { visibilityChangedListener = listener }
    fun isPanelVisible(): Boolean = visibleState || pendingOpen

    fun updateItems(title: String, ids: IntArray, labels: Array<String>, selected: IntArray, callback: OnItemClickListener) {
        if (!isPanelVisible() || ids.size != labels.size) return
        titleState = title
        itemsState = ids.indices.map { PanelItem(ids[it], labels[it], 0) }
        selectedState = selected.toSet()
        sourceMenuState = false
        listener = callback
        requestLayout()
        doOnNextLayout { currentAnchor?.let { if (isPanelVisible()) place(it) } }
    }

    fun showSources(anchor: View, title: String, ids: IntArray, labels: Array<String>, subtitles: Array<String>,
                    selected: IntArray, callback: OnItemClickListener) {
        show(anchor, title, ids, labels, null, selected, 320, 320, callback)
        sourceMenuState = true
        itemsState = ids.indices.map { PanelItem(ids[it], labels[it], 0, subtitles.getOrElse(it) { "" }) }
    }

    fun updateSources(title: String, ids: IntArray, labels: Array<String>, subtitles: Array<String>,
                      selected: IntArray, callback: OnItemClickListener) {
        updateItems(title, ids, labels, selected, callback)
        sourceMenuState = true
        itemsState = ids.indices.map { PanelItem(ids[it], labels[it], 0, subtitles.getOrElse(it) { "" }) }
    }

    private fun place(anchor: View) {
        val density = resources.displayMetrics.density
        val own = IntArray(2)
        val target = IntArray(2)
        getLocationInWindow(own)
        anchor.getLocationInWindow(target)
        val bars = ViewCompat.getRootWindowInsets(this)
            ?.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
        val actionCount = if (sourceMenuState) itemsState.count { it.id < 0 } else 0
        val visualRows = itemsState.size - if (actionCount > 1) actionCount - 1 else 0
        bounds = PlaybackPanelLayout.calculate(
            width / density, height / density,
            (target[0] - own[0] + anchor.width / 2f) / density,
            (target[1] - own[1]) / density,
            (target[1] - own[1] + anchor.height) / density,
            ((bars?.top ?: 0) - own[1]).coerceAtLeast(0) / density,
            (bars?.bottom ?: 0) / density,
            requestedWidth, maximumHeight, visualRows,
            resources.configuration.fontScale, titleState.isNotEmpty(), itemsState.any { it.subtitle.isNotEmpty() },
            if (sourceMenuState && actionCount > 0) 1 else 0
        )
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (!visibleState) return
        val token = requestGeneration
        post {
            val anchor = currentAnchor
            if (token == requestGeneration && visibleState && anchor?.isAttachedToWindow == true) place(anchor)
        }
    }

    override fun onDetachedFromWindow() {
        ++requestGeneration
        pendingOpen = false
        visibleState = false
        currentAnchor = null
        listener = null
        removeCallbacks(hideRunnable)
        super.onDetachedFromWindow()
    }

    @Composable
    override fun Content() {
        val light = !isSystemInDarkTheme()
        val panel = if (light) Color(0xFFF1F3F6) else Color(0xFF191C22)
        val header = if (light) Color(0xFFE5EBF2) else Color(0xFF242A35)
        val itemSurface = if (light) Color.White else Color(0xFF2B303A)
        val text = if (light) Color(0xFF1F2937) else Color(0xFFF4F6FA)
        val secondary = if (light) Color(0xFF596579) else Color(0xFFB3BECD)
        val selectedFill = Color(context.getColor(R.color.playback_selected_surface))
        val selectedText = Color(context.getColor(R.color.playback_selected_text))
        val density = resources.displayMetrics.density
        val from = if (bounds.above) Alignment.Bottom else Alignment.Top
        val edge = if (bounds.above) Alignment.BottomStart else Alignment.TopStart

        Box(Modifier.fillMaxSize()) {
            if (visibleState) {
                // A transparent dismiss target: opening a control doesn't darken the whole video.
                Box(Modifier.fillMaxSize().clickable(interactionSource = null, indication = null, onClick = ::dismiss))
            }
            // Keep the anchor edge fixed as AnimatedVisibility changes its measured height.
            Box(Modifier.offset { IntOffset((bounds.x * density).roundToInt(), (bounds.y * density).roundToInt()) }
                .width(bounds.width.dp).height(bounds.height.dp)) {
                AnimatedVisibility(
                    visible = visibleState,
                    modifier = Modifier.align(edge),
                    enter = expandVertically(tween(220, easing = FastOutSlowInEasing), expandFrom = from) + fadeIn(tween(120)),
                    exit = shrinkVertically(tween(160, easing = FastOutSlowInEasing), shrinkTowards = from) + fadeOut(tween(100))
                ) {
                    Column(Modifier.width(bounds.width.dp).height(bounds.height.dp)
                        .shadow(6.dp, RoundedCornerShape(18.dp))
                        .clip(RoundedCornerShape(18.dp)).background(panel)
                        .clickable(interactionSource = null, indication = null, onClick = {})
                        .padding(10.dp)
                        .semantics { paneTitle = titleState }) {
                        if (bounds.headerHeight > 0f) {
                            Row(Modifier.fillMaxWidth().height(bounds.headerHeight.dp)
                                .clip(RoundedCornerShape(10.dp)).background(header).padding(start = 12.dp, end = 4.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    BasicText(titleState, Modifier.semantics { heading() },
                                        style = TextStyle(text, 14.sp, FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    val current = itemsState.firstOrNull { it.id in selectedState }?.label
                                    BasicText(current?.let { "当前 · $it" } ?: "共 ${itemsState.size} 项",
                                        style = TextStyle(secondary, 11.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                DialogCloseButton(::dismiss, itemSurface, text)
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                        key(contentEpoch) {
                            Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                if (sourceMenuState) {
                                    val current = itemsState.filter { it.id in selectedState }
                                    val actions = itemsState.filter { it.id < 0 }
                                    val sources = itemsState.filter { it.id !in selectedState && it.id >= 0 }
                                    current.forEach { PanelRow(it, bounds.rowHeight, itemSurface, text, secondary, selectedFill, selectedText) }
                                    if (actions.isNotEmpty()) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        actions.take(2).forEach { SourceAction(it, Modifier.weight(1f).height(40.dp), itemSurface, text) }
                                        if (actions.size == 1) Spacer(Modifier.weight(1f))
                                    }
                                    sources.forEach { PanelRow(it, bounds.rowHeight, itemSurface, text, secondary, selectedFill, selectedText) }
                                } else {
                                    itemsState.forEach { PanelRow(it, bounds.rowHeight, itemSurface, text, secondary, selectedFill, selectedText) }
                                }
                            }
                        }
                        if (bounds.footerHeight > 0f) {
                            Box(Modifier.fillMaxWidth().height(bounds.footerHeight.dp), contentAlignment = Alignment.BottomCenter) {
                                BasicText("滑动查看更多", style = TextStyle(secondary, 10.sp))
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun PanelRow(item: PanelItem, rowHeight: Float, surface: Color, primary: Color, secondary: Color,
                         selectedFill: Color, selectedText: Color) {
        val selected = item.id in selectedState
        val foreground = if (selected) selectedText else primary
        Row(Modifier.fillMaxWidth().height(rowHeight.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) selectedFill else surface)
            .clickable(role = Role.Button) { listener?.onItemClick(item.id) }
            .semantics { if (selected) stateDescription = "已选中" }
            .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (item.icon != 0) Image(painterResource(item.icon), null, Modifier.size(18.dp),
                colorFilter = ColorFilter.tint(if (selected) selectedText else secondary))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                BasicText(item.label,
                    style = TextStyle(foreground, if (item.label.contains('\n')) 12.sp else 14.sp,
                        if (selected) FontWeight.SemiBold else FontWeight.Medium),
                    maxLines = if (item.label.contains('\n')) 2 else 1, overflow = TextOverflow.Ellipsis)
                if (item.subtitle.isNotEmpty()) BasicText(item.subtitle,
                    style = TextStyle(if (selected) selectedText else secondary, 11.sp),
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            BasicText(if (selected) "✓" else if (selectionMenuState && item.id >= 0) "○" else "›",
                style = TextStyle(if (selected) selectedText else secondary, 18.sp, FontWeight.Medium))
        }
    }

    @Composable
    private fun SourceAction(item: PanelItem, modifier: Modifier, surface: Color, primary: Color) {
        Box(modifier.clip(RoundedCornerShape(10.dp)).background(surface)
            .clickable(role = Role.Button) { listener?.onItemClick(item.id) }
            .padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
            BasicText(item.label, style = TextStyle(primary, 12.sp, FontWeight.SemiBold, textAlign = TextAlign.Center),
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
