package com.fongmi.android.tv.ui.custom

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.MotionEvent
import android.graphics.Matrix
import android.graphics.RectF
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import android.widget.ImageView
import com.fongmi.android.tv.ai.AiOrbDrawable
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.fongmi.android.tv.R
import com.fongmi.android.tv.ui.custom.liquid.LiquidBottomTab
import com.fongmi.android.tv.ui.custom.liquid.LiquidBottomTabs
import com.fongmi.android.tv.ui.custom.liquid.LiquidButton
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

/** Hosts the Backdrop catalog's LiquidBottomTabs component inside the legacy View activity. */
class LiquidGlassNavigationView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AbstractComposeView(context, attrs, defStyleAttr) {

    interface Listener {
        fun onGlassNavigationSelected(itemId: Int)
        fun onGlassContextAction()
        fun onGlassContextLongAction()
        fun onGlassAiTouch(view: View, event: MotionEvent): Boolean
        fun onGlassVoiceCancel()
        fun onGlassVoiceOpen()
    }

    private var selectedIdState by mutableIntStateOf(R.id.recommend)
    private var liveVisibleState by mutableStateOf(true)
    private var actionState by mutableIntStateOf(ACTION_NONE)
    private var actionVisibleState by mutableStateOf(false)
    private var accentState by mutableStateOf(Color(0xFFFFCC00))
    private var backdropViewState by mutableStateOf<View?>(null)
    private var renderingEnabledState by mutableStateOf(false)
    private var listener: Listener? = null
    private var aiModeState by mutableIntStateOf(0)
    private var voiceActiveState by mutableStateOf(false)
    private var voiceProgressState by mutableFloatStateOf(0f)
    private var voiceStatusState by mutableStateOf("")
    fun setVoiceState(active: Boolean, progress: Float, status: String) {
        voiceActiveState = active
        voiceProgressState = progress.coerceIn(0f, 1f)
        voiceStatusState = status
        if (!active) aiGesture = false
    }
    private var bottomInsetState by mutableIntStateOf(0)
    fun setBottomInsetPixels(bottom: Int) { bottomInsetState = bottom.coerceAtLeast(0) }
    fun setAiMode(mode: Int) { aiModeState = mode }
    private val aiBounds = RectF()
    private val sourceTransform = Matrix()
    private val navigationTransform = Matrix()
    private val samplingTransform = Matrix()
    private var aiGesture = false
    private var voiceGestureEnabled = true

    fun setVoiceGestureEnabled(enabled: Boolean) {
        if (voiceGestureEnabled == enabled) return
        voiceGestureEnabled = enabled
        aiGesture = false
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        // During processing, only the visible status controls handle navigation touches.
        if (voiceActiveState) return super.dispatchTouchEvent(event)
        if (!voiceGestureEnabled) {
            if (event.actionMasked == MotionEvent.ACTION_DOWN) parent?.requestDisallowInterceptTouchEvent(true)
            val handled = super.dispatchTouchEvent(event)
            if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL)
                parent?.requestDisallowInterceptTouchEvent(false)
            return handled
        }
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            val location = IntArray(2)
            getLocationInWindow(location)
            aiGesture = aiBounds.contains(event.x + location[0], event.y + location[1])
        }
        if (aiGesture) {
            val handled = listener?.onGlassAiTouch(this, event) ?: false
            if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) aiGesture = false
            return handled
        }
        return super.dispatchTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        listener?.onGlassNavigationSelected(R.id.vod)
        return true
    }

    init {
        isClickable = false
        isFocusable = false
    }

    fun setListener(listener: Listener?) {
        this.listener = listener
    }

    fun setSelectedItemId(itemId: Int) {
        selectedIdState = itemId
    }

    fun setLiveVisible(visible: Boolean) {
        liveVisibleState = visible
    }

    fun setAction(action: Int, visible: Boolean) {
        actionState = action
        actionVisibleState = visible
    }

    fun setAccentColor(color: Int) {
        accentState = Color(color)
    }

    fun setBackdropView(view: View?) {
        backdropViewState = view
    }

    fun setRenderingEnabled(enabled: Boolean) {
        renderingEnabledState = enabled
    }

    // Build both transforms in the same View root. Window-coordinate deltas already contain
    // the AI card's scale, so translating a local canvas by those deltas applies it twice.
    private fun localToRoot(view: View, result: Matrix) {
        val parent = view.parent as? View
        if (parent != null) {
            localToRoot(parent, result)
            result.preTranslate(-parent.scrollX.toFloat(), -parent.scrollY.toFloat())
        }
        result.preTranslate(view.left.toFloat(), view.top.toFloat())
        if (!view.matrix.isIdentity) result.preConcat(view.matrix)
    }

    private fun sourceToNavigation(source: View): Boolean {
        sourceTransform.reset()
        navigationTransform.reset()
        localToRoot(source, sourceTransform)
        localToRoot(this, navigationTransform)
        if (!navigationTransform.invert(samplingTransform)) return false
        samplingTransform.preConcat(sourceTransform)
        return true
    }

    @Composable
    override fun Content() {
        val sourceView = backdropViewState
        val frameNanos = remember { mutableLongStateOf(0L) }
        val backdrop = rememberLayerBackdrop(onDraw = {
            frameNanos.longValue
            if (sourceView != null && sourceView.isAttachedToWindow && sourceView.isShown &&
                sourceToNavigation(sourceView)) {
                drawIntoCanvas { canvas ->
                    val native = canvas.nativeCanvas
                    val saved = native.save()
                    try {
                        native.clipRect(0f, 0f, size.width, size.height)
                        native.concat(samplingTransform)
                        sourceView.draw(native)
                    } finally {
                        native.restoreToCount(saved)
                    }
                }
            }
        })

        LaunchedEffect(renderingEnabledState, sourceView) {
            while (renderingEnabledState && sourceView != null) {
                withFrameNanos { frameNanos.longValue = it }
            }
        }

        val items = buildList {
            add(NavItem(R.id.recommend, R.drawable.ic_nav_recommend, R.string.nav_recommend))
            add(NavItem(R.id.vod, R.drawable.ic_nav_discover, R.string.ai_find))
            if (liveVisibleState) add(NavItem(R.id.live, R.drawable.ic_nav_live, R.string.nav_live))
            add(NavItem(R.id.setting, R.drawable.ic_nav_profile, R.string.nav_profile))
        }
        val light = !isSystemInDarkTheme()
        val contentColor = if (light) Color(0xFF1C1C1E) else Color.White
        val containerColor = if (light) {
            Color(0xFFF8F8FA).copy(alpha = 0.86f)
        } else {
            Color(0xFF161618).copy(alpha = 0.82f)
        }
        val selectedIndex = items.indexOfFirst { it.id == selectedIdState }.coerceAtLeast(0)
        // 宽屏下动作键靠右单独摆放：它的底部距离是“导航栏 + 7dp”，
        // 右侧却只有 12dp。补上两者的差值，让含小白条安全区的两边视觉距离一致。
        val navigationBarBottomPadding = with(LocalDensity.current) { bottomInsetState.toDp() }
        val wideActionEndPadding = (navigationBarBottomPadding + 7.dp - 12.dp).coerceAtLeast(0.dp)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            contentAlignment = Alignment.BottomCenter
        ) {
            // layerBackdrop draws its content on screen AND records it. Keep visible content
            // empty; the source page is recorded only by onDraw, never as a rectangular copy.
            Box(Modifier.matchParentSize().clipToBounds().layerBackdrop(backdrop))

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = navigationBarBottomPadding)
                    .padding(horizontal = 12.dp, vertical = 7.dp)
            ) {
                val tabsWidth = (items.size * 76).dp.coerceAtMost(maxWidth)
                val tabs: @Composable (Modifier) -> Unit = { tabsModifier ->
                    LiquidBottomTabs(
                        selectedTabIndex = selectedIndex,
                        onTabSelected = { index ->
                            if (!voiceActiveState) items.getOrNull(index)?.let { listener?.onGlassNavigationSelected(it.id) }
                        },
                        backdrop = backdrop,
                        frameNanos = frameNanos,
                        tabsCount = items.size,
                        accentColor = accentState,
                        containerColor = containerColor,
                        modifier = tabsModifier,
                        interactionEnabled = !voiceActiveState,
                        selectionAlpha = (1f - voiceProgressState * 3f).coerceIn(0f, 1f)
                    ) {
                        NavigationContents(items, contentColor)
                    }
                }
                val action: @Composable (Modifier) -> Unit = { actionModifier ->
                    if (actionVisibleState) {
                        LiquidButton(
                            onClick = { if (!voiceActiveState) listener?.onGlassContextAction() },
                            onLongClick = if (actionState == ACTION_FILTER) {
                                { if (!voiceActiveState) listener?.onGlassContextLongAction() }
                            } else {
                                null
                            },
                            backdrop = backdrop,
                            frameNanos = frameNanos,
                            surfaceColor = containerColor,
                            modifier = actionModifier.graphicsLayer { alpha = 1f - voiceProgressState }.semantics {
                                role = Role.Button
                                contentDescription = context.getString(actionDescription(actionState))
                            }
                        ) {
                            Image(
                                painter = painterResource(actionIcon(actionState)),
                                contentDescription = null,
                                modifier = Modifier.size(22.dp),
                                colorFilter = ColorFilter.tint(contentColor)
                            )
                        }
                    }
                }

                if (maxWidth < 576.dp) {
                    // 手机维持 beta5 的视觉重心：胶囊和圆钮作为一个整体居中，不在左侧留下空洞。
                    Row(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .wrapContentWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        tabs(Modifier.width(tabsWidth).height(52.dp))
                        if (actionVisibleState) Spacer(Modifier.width(8.dp))
                        action(Modifier.size(52.dp))
                    }
                } else {
                    // 600dp 及以上的平板/横向长屏：胶囊居中，功能键固定在右手侧。
                    tabs(Modifier.align(Alignment.Center).width(tabsWidth).height(52.dp))
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = wideActionEndPadding)
                    ) {
                        action(Modifier.size(52.dp))
                    }
                }
            }
        }
    }

    @Composable
    private fun NavigationContents(items: List<NavItem>, contentColor: Color) {
        // The glass component records this content a second time for sampling. Do not
        // retain an ImageView from either pass or copy its drawable into a screen overlay.
        BoxWithConstraints(Modifier.fillMaxSize().clipToBounds()) {
            val slotWidth = maxWidth / items.size
            val progress = voiceProgressState
            val navigationAlpha = (1f - progress * 2f).coerceIn(0f, 1f)
            val statusAlpha = ((progress - .25f) / .75f).coerceIn(0f, 1f)
            // Covers the robot and status area; the separate close button consumes its own tap.
            Row(Modifier.fillMaxSize()
                .then(if (voiceActiveState) Modifier.clearAndSetSemantics { } else Modifier)) {
                items.forEach { item ->
                    LiquidBottomTab(
                        onClick = { if (!voiceActiveState) listener?.onGlassNavigationSelected(item.id) },
                        modifier = Modifier.onGloballyPositioned { coordinates ->
                            if (item.id == R.id.vod) {
                                val bounds = coordinates.boundsInWindow()
                                aiBounds.set(bounds.left, bounds.top, bounds.right, bounds.bottom)
                            }
                        }.semantics {
                            role = Role.Tab
                            contentDescription = context.getString(item.label)
                        }
                    ) {
                        if (item.id == R.id.vod) Spacer(Modifier.size(24.dp))
                        else Image(
                            painter = painterResource(item.icon), contentDescription = null,
                            modifier = Modifier.size(22.dp).graphicsLayer { alpha = navigationAlpha },
                            colorFilter = ColorFilter.tint(contentColor)
                        )
                    }
                }
            }
            val aiIndex = items.indexOfFirst { it.id == R.id.vod }.coerceAtLeast(0)
            // One persistent robot per glass render pass; only its position changes.
            AndroidView(
                factory = { ImageView(it).apply { setImageDrawable(AiOrbDrawable()) } },
                modifier = Modifier.align(Alignment.CenterStart)
                    .offset(x = slotWidth * (aiIndex * (1f - progress) + .5f) - 12.dp)
                    .size(24.dp),
                update = { (it.drawable as AiOrbDrawable).apply {
                    setTint(contentColor.toArgb())
                    setMode(if (voiceActiveState) 2 else aiModeState)
                    setBodyMotionEnabled(!voiceActiveState)
                    setRunning(renderingEnabledState)
                } }, onReset = null,
                onRelease = { (it.drawable as AiOrbDrawable).setRunning(false) }
            )
            if (voiceActiveState) {
                Box(Modifier.fillMaxSize().padding(end = 44.dp)
                    .clickable(role = Role.Button) { listener?.onGlassVoiceOpen() }
                    .semantics { contentDescription = "打开 AI 对话查看找片进度" })
                Row(
                    Modifier.fillMaxSize().padding(start = slotWidth, end = 4.dp)
                        .graphicsLayer { alpha = statusAlpha },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BasicText(
                        text = voiceStatusState,
                        modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
                        style = TextStyle(color = contentColor, fontSize = 13.sp, fontWeight = FontWeight.Medium),
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Box(
                        Modifier.width(40.dp).fillMaxHeight()
                            .clickable(enabled = progress > .5f, role = Role.Button) { listener?.onGlassVoiceCancel() }
                            .semantics { contentDescription = "取消语音找片" },
                        contentAlignment = Alignment.Center
                    ) {
                        Image(painterResource(R.drawable.ic_action_close), contentDescription = null,
                            modifier = Modifier.size(18.dp), colorFilter = ColorFilter.tint(contentColor))
                    }
                }
            }
        }
    }

    private fun actionIcon(action: Int): Int = when (action) {
        ACTION_FILTER -> R.drawable.ic_fab_filter
        ACTION_LINK -> R.drawable.ic_fab_link
        ACTION_TOP -> R.drawable.ic_fab_top
        ACTION_CLOSE -> R.drawable.ic_action_close
        else -> R.drawable.ic_action_search
    }

    private fun actionDescription(action: Int): Int = when (action) {
        ACTION_FILTER -> R.string.vod_filter
        ACTION_LINK -> R.string.action_change_source
        ACTION_TOP -> R.string.action_back_to_top
        ACTION_CLOSE -> R.string.action_close_search
        else -> R.string.setting_search_hint
    }

    private data class NavItem(val id: Int, val icon: Int, val label: Int)

    companion object {
        const val ACTION_NONE = 0
        const val ACTION_FILTER = 1
        const val ACTION_LINK = 2
        const val ACTION_TOP = 3
        const val ACTION_SEARCH = 4
        const val ACTION_CLOSE = 5
    }
}
