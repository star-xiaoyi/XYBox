package com.fongmi.android.tv.ui.custom

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
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
    }

    private var selectedIdState by mutableIntStateOf(R.id.vod)
    private var liveVisibleState by mutableStateOf(true)
    private var actionState by mutableIntStateOf(ACTION_NONE)
    private var actionVisibleState by mutableStateOf(false)
    private var accentState by mutableStateOf(Color(0xFFFFCC00))
    private var backdropViewState by mutableStateOf<View?>(null)
    private var renderingEnabledState by mutableStateOf(false)
    private var listener: Listener? = null

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

    @Composable
    override fun Content() {
        val sourceView = backdropViewState
        val frameNanos = remember { mutableLongStateOf(0L) }
        val backdrop = rememberLayerBackdrop()
        val navLocation = remember { IntArray(2) }
        val sourceLocation = remember { IntArray(2) }

        LaunchedEffect(renderingEnabledState, sourceView) {
            while (renderingEnabledState && sourceView != null) {
                withFrameNanos { frameNanos.longValue = it }
            }
        }

        val items = buildList {
            add(NavItem(R.id.vod, R.drawable.ic_nav_vod, R.string.nav_vod))
            if (liveVisibleState) add(NavItem(R.id.live, R.drawable.ic_nav_live, R.string.nav_live))
            add(NavItem(R.id.setting, R.drawable.ic_nav_setting, R.string.nav_setting))
        }
        val light = !isSystemInDarkTheme()
        val contentColor = if (light) Color(0xFF1C1C1E) else Color.White
        val containerColor = if (light) {
            Color(0xFFF8F8FA).copy(alpha = 0.86f)
        } else {
            Color(0xFF161618).copy(alpha = 0.82f)
        }
        val selectedIndex = items.indexOfFirst { it.id == selectedIdState }.coerceAtLeast(0)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            contentAlignment = Alignment.BottomCenter
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .layerBackdrop(backdrop)
            ) {
                frameNanos.longValue
                if (sourceView != null && sourceView.isAttachedToWindow) {
                    this@LiquidGlassNavigationView.getLocationInWindow(navLocation)
                    sourceView.getLocationInWindow(sourceLocation)
                    drawIntoCanvas { canvas ->
                        val nativeCanvas = canvas.nativeCanvas
                        val saveCount = nativeCanvas.save()
                        nativeCanvas.translate(
                            (sourceLocation[0] - navLocation[0]).toFloat(),
                            (sourceLocation[1] - navLocation[1]).toFloat()
                        )
                        sourceView.draw(nativeCanvas)
                        nativeCanvas.restoreToCount(saveCount)
                    }
                }
            }

            Row(
                modifier = Modifier
                    .wrapContentWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LiquidBottomTabs(
                    selectedTabIndex = selectedIndex,
                    onTabSelected = { index ->
                        items.getOrNull(index)?.let { listener?.onGlassNavigationSelected(it.id) }
                    },
                    backdrop = backdrop,
                    frameNanos = frameNanos,
                    tabsCount = items.size,
                    accentColor = accentState,
                    containerColor = containerColor,
                    modifier = Modifier
                        .width(228.dp)
                        .height(52.dp)
                ) {
                    items.forEach { item ->
                        LiquidBottomTab(
                            onClick = { listener?.onGlassNavigationSelected(item.id) },
                            modifier = Modifier.semantics {
                                role = Role.Tab
                                contentDescription = context.getString(item.label)
                            }
                        ) {
                            Image(
                                painter = painterResource(item.icon),
                                contentDescription = null,
                                modifier = Modifier.size(22.dp),
                                colorFilter = ColorFilter.tint(contentColor)
                            )
                        }
                    }
                }

                if (actionVisibleState) {
                    Spacer(Modifier.width(8.dp))
                    LiquidButton(
                        onClick = { listener?.onGlassContextAction() },
                        onLongClick = if (actionState == ACTION_FILTER) {
                            { listener?.onGlassContextLongAction() }
                        } else {
                            null
                        },
                        backdrop = backdrop,
                        frameNanos = frameNanos,
                        surfaceColor = containerColor,
                        modifier = Modifier
                            .size(52.dp)
                            .semantics {
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
