package com.fongmi.android.tv.ui.custom

import android.content.Context
import android.util.AttributeSet
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fongmi.android.tv.R
import com.fongmi.android.tv.ui.custom.liquid.LiquidButton
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.shapes.Capsule

/**
 * 设置页完整顶部画布。
 *
 * 圆按钮不能放在一个仅和按钮同大的独立 CanvasBackdrop 中，否则折射层的矩形边界会被看见。
 * 这里与展示台保持同样的结构：背景、搜索框和圆按钮共享一个 LayerBackdrop。
 */
class SettingsGlassHeaderView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AbstractComposeView(context, attrs, defStyleAttr) {

    fun interface OnQueryChangedListener {
        fun onQueryChanged(query: String)
    }

    fun interface OnSearchStateChangedListener {
        fun onSearchStateChanged(active: Boolean)
    }

    private var searchActiveState by mutableStateOf(false)
    private var queryState by mutableStateOf("")
    private var queryChangedListener: OnQueryChangedListener? = null
    private var searchStateChangedListener: OnSearchStateChangedListener? = null

    init {
        setBackgroundColor(android.graphics.Color.TRANSPARENT)
        clipToPadding = false
    }

    fun setOnQueryChangedListener(listener: OnQueryChangedListener?) {
        queryChangedListener = listener
    }

    fun setOnSearchStateChangedListener(listener: OnSearchStateChangedListener?) {
        searchStateChangedListener = listener
    }

    fun getQuery(): String = queryState

    fun isSearchActive(): Boolean = searchActiveState

    fun toggleSearch() {
        setSearchActive(!searchActiveState)
    }

    fun closeSearch() {
        setSearchActive(false)
    }

    private fun setSearchActive(active: Boolean) {
        if (searchActiveState == active) return
        searchActiveState = active
        searchStateChangedListener?.onSearchStateChanged(active)
        if (!active) updateQuery("")
    }

    private fun updateQuery(query: String) {
        queryState = query
        queryChangedListener?.onQueryChanged(query)
    }

    @Composable
    override fun Content() {
        val light = !isSystemInDarkTheme()
        val background = if (light) Color(0xFFF2F2F7) else Color.Black
        val glass = if (light) Color.White.copy(alpha = 0.36f)
        else Color(0xFF18181B).copy(alpha = 0.42f)
        val text = if (light) Color(0xFF1C1C1E) else Color.White
        val secondary = if (light) Color(0xFF6C6C70) else Color(0xFF98989D)
        val frameNanos = remember { mutableLongStateOf(0L) }
        val backdrop = rememberLayerBackdrop()
        val focusRequester = remember { FocusRequester() }
        val focusManager = LocalFocusManager.current
        val keyboard = LocalSoftwareKeyboardController.current

        LaunchedEffect(Unit) {
            while (true) withFrameNanos { frameNanos.longValue = it }
        }
        LaunchedEffect(searchActiveState) {
            if (searchActiveState) {
                focusRequester.requestFocus()
                keyboard?.show()
            } else {
                focusManager.clearFocus(force = true)
                keyboard?.hide()
            }
        }

        Box(Modifier.fillMaxSize()) {
            Canvas(Modifier.fillMaxSize().layerBackdrop(backdrop)) {
                // 顶部栏覆盖在滚动内容之上，仅铺半透明磨砂底，让内容轻微透出。
                drawRect(background.copy(alpha = if (light) 0.84f else 0.78f))
            }

            Row(
                Modifier.fillMaxSize().padding(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HeaderMainArea(
                    modifier = Modifier.weight(1f).height(52.dp),
                    active = searchActiveState,
                    query = queryState,
                    onQueryChanged = ::updateQuery,
                    backdrop = backdrop,
                    glass = glass,
                    text = text,
                    secondary = secondary,
                    focusRequester = focusRequester,
                    onKeyboardAction = { keyboard?.hide() }
                )

                Box(Modifier.size(8.dp))

                // 与展示台“圆形按钮”示例完全相同：共享 backdrop、52dp 正方形、Capsule。
                LiquidButton(
                    onClick = ::toggleSearch,
                    backdrop = backdrop,
                    frameNanos = frameNanos,
                    surfaceColor = glass,
                    dragResponse = 0.42f,
                    modifier = Modifier.size(44.dp)
                ) {
                    Crossfade(
                        targetState = searchActiveState,
                        animationSpec = tween(160),
                        label = "settingsSearchIcon"
                    ) { active ->
                        Image(
                            painter = painterResource(if (active) R.drawable.ic_action_close else R.drawable.ic_action_search),
                            contentDescription = stringResource(
                                if (active) R.string.action_close_search else R.string.setting_search_hint
                            ),
                            colorFilter = ColorFilter.tint(text),
                            modifier = Modifier.size(21.dp)
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun HeaderMainArea(
        modifier: Modifier,
        active: Boolean,
        query: String,
        onQueryChanged: (String) -> Unit,
        backdrop: com.kyant.backdrop.Backdrop,
        glass: Color,
        text: Color,
        secondary: Color,
        focusRequester: FocusRequester,
        onKeyboardAction: () -> Unit
    ) {
        val searchProgress by animateFloatAsState(
            targetValue = if (active) 1f else 0f,
            animationSpec = tween(240),
            label = "settingsSearchField"
        )
        Box(modifier, contentAlignment = Alignment.CenterStart) {
            AnimatedVisibility(
                visible = !active,
                enter = fadeIn(tween(180)),
                exit = fadeOut(tween(120))
            ) {
                BasicText(
                    text = stringResource(R.string.nav_setting),
                    style = TextStyle(color = text, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                )
            }

            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = searchProgress
                        scaleX = 0.82f + 0.18f * searchProgress
                        transformOrigin = TransformOrigin(1f, 0.5f)
                    }
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = { Capsule() },
                        effects = {
                            vibrancy()
                            blur(2.dp.toPx())
                            lens(12.dp.toPx(), 24.dp.toPx())
                        },
                        onDrawSurface = { drawRect(glass) }
                    )
                    .padding(horizontal = 18.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChanged,
                    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                    enabled = active,
                    textStyle = TextStyle(color = text, fontSize = 16.sp, fontWeight = FontWeight.Medium),
                    cursorBrush = SolidColor(text),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onKeyboardAction() }),
                    decorationBox = { innerTextField ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (query.isEmpty()) {
                                BasicText(
                                    text = stringResource(R.string.setting_search_hint),
                                    style = TextStyle(color = secondary, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                                )
                            }
                            innerTextField()
                        }
                    }
                )
            }
        }
    }
}
