package com.fongmi.android.tv.ui.custom

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.widget.ImageView
import androidx.annotation.DrawableRes
import androidx.appcompat.widget.AppCompatImageView
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.fongmi.android.tv.R
import com.fongmi.android.tv.ui.custom.liquid.LiquidButton
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.drawPlainBackdrop
import com.kyant.backdrop.isRuntimeShaderSupported
import com.kyant.backdrop.effects.runtimeShaderEffect
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedRectangle
import kotlin.math.roundToInt

/**
 * 首页完整顶部画布。
 *
 * 搜索框和所有圆形按钮共享整个顶栏的 LayerBackdrop，因此拖动按钮时，折射和果冻形变可以越过
 * 按钮原本的 36dp 边界，不会再被独立 Android View 的矩形画布裁切。Logo 仍由原生 ImageView
 * 承载，Glide 加载的动画 Drawable 可以继续播放。
 */
class HomeGlassHeaderView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AbstractComposeView(context, attrs, defStyleAttr) {

    fun interface OnQueryChangedListener {
        fun onQueryChanged(query: String)
    }

    fun interface OnSearchFocusChangedListener {
        fun onSearchFocusChanged(hasFocus: Boolean)
    }

    fun interface OnSearchSubmittedListener {
        fun onSearchSubmitted()
    }

    fun interface OnSearchBoundsChangedListener {
        fun onSearchBoundsChanged(left: Int, top: Int, right: Int, bottom: Int)
    }

    private val logoView = AppCompatImageView(context).apply {
        id = View.generateViewId()
        scaleType = ImageView.ScaleType.CENTER_CROP
        setBackgroundResource(R.drawable.shape_action_background)
        setImageResource(R.drawable.ic_app_logo)
        isClickable = true
    }

    private var queryState by mutableStateOf(TextFieldValue(""))
    private var hintState by mutableStateOf(context.getString(R.string.search_keyword))
    private var brandModeState by mutableStateOf(false)
    private var statusBarInsetState by mutableIntStateOf(0)
    private var compactState by mutableStateOf(false)
    private var categoryModeState by mutableStateOf(false)
    fun setCategoryMode(enabled: Boolean) { categoryModeState = enabled }
    private var brandTitleState by mutableStateOf("XY影视")
    private var expandedState by mutableStateOf(false)
    private var searchFocusedState by mutableStateOf(false)
    private var focusRequestedState by mutableStateOf(false)
    private var searchIconState by mutableIntStateOf(R.drawable.ic_action_search)
    private var logoSizeState by mutableIntStateOf(24)
    private var backdropViewState by mutableStateOf<View?>(null)
    private var renderingEnabledState by mutableStateOf(false)

    private var queryChangedListener: OnQueryChangedListener? = null
    private var searchFocusChangedListener: OnSearchFocusChangedListener? = null
    private var searchSubmittedListener: OnSearchSubmittedListener? = null
    private var searchBoundsChangedListener: OnSearchBoundsChangedListener? = null
    private var searchClickListener: View.OnClickListener? = null
    private var searchBackClickListener: View.OnClickListener? = null
    private var keepClickListener: View.OnClickListener? = null
    private var historyClickListener: View.OnClickListener? = null
    private var searchBounds = android.graphics.Rect()

    init {
        setBackgroundColor(android.graphics.Color.TRANSPARENT)
        clipToPadding = false
    }

    fun getLogoView(): AppCompatImageView = logoView

    fun setLogoClickListener(listener: View.OnClickListener?) {
        logoView.setOnClickListener(listener)
    }

    fun setSearchClickListener(listener: View.OnClickListener?) {
        searchClickListener = listener
    }

    fun setSearchBackClickListener(listener: View.OnClickListener?) {
        searchBackClickListener = listener
    }

    fun setKeepClickListener(listener: View.OnClickListener?) {
        keepClickListener = listener
    }

    fun setHistoryClickListener(listener: View.OnClickListener?) {
        historyClickListener = listener
    }

    fun setOnQueryChangedListener(listener: OnQueryChangedListener?) {
        queryChangedListener = listener
    }

    fun setOnSearchFocusChangedListener(listener: OnSearchFocusChangedListener?) {
        searchFocusChangedListener = listener
    }

    fun setOnSearchSubmittedListener(listener: OnSearchSubmittedListener?) {
        searchSubmittedListener = listener
    }

    fun setOnSearchBoundsChangedListener(listener: OnSearchBoundsChangedListener?) {
        searchBoundsChangedListener = listener
        if (!searchBounds.isEmpty) {
            listener?.onSearchBoundsChanged(
                searchBounds.left,
                searchBounds.top,
                searchBounds.right,
                searchBounds.bottom
            )
        }
    }

    fun setExpanded(expanded: Boolean) {
        expandedState = expanded
    }

    fun setBrandMode(enabled: Boolean, title: CharSequence? = null) {
        brandModeState = enabled
        if (!title.isNullOrEmpty()) brandTitleState = title.toString()
    }

    fun setCompact(compact: Boolean) {
        compactState = compact
    }

    fun setStatusBarInset(inset: Int) {
        statusBarInsetState = inset.coerceAtLeast(0)
    }

    fun setBackdropView(view: View?) {
        backdropViewState = view
        renderingEnabledState = view != null
    }

    fun setSearchIcon(@DrawableRes resource: Int) {
        searchIconState = resource
    }

    fun setLogoSize(sizeDp: Int) {
        logoSizeState = sizeDp.coerceIn(24, 36)
    }

    fun setHint(hint: CharSequence?) {
        hintState = hint?.toString().orEmpty()
    }

    fun setQuery(query: CharSequence?) {
        val text = query?.toString().orEmpty()
        updateQuery(TextFieldValue(text, TextRange(text.length)))
    }

    fun getQuery(): String = queryState.text

    fun hasSearchFocus(): Boolean = searchFocusedState

    fun requestSearchFocus() {
        focusRequestedState = true
    }

    fun clearSearchFocus() {
        focusRequestedState = false
    }

    /**
     * 顶栏里的按钮和搜索框正在处理手势时，不允许 SwipeRefreshLayout 或 AppBarLayout
     * 中途截走事件。指针即使拖出顶栏，仍由最初按下的玻璃控件收到完整的抬起/取消序列。
     */
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        // 底部的渐变区仅绘制，不挡住下方内容的点击或滑动。
        if (brandModeState && event.actionMasked == MotionEvent.ACTION_DOWN &&
            (event.y < statusBarInsetState || event.y >= statusBarInsetState + 56 * resources.displayMetrics.density)) return false
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            parent?.requestDisallowInterceptTouchEvent(true)
        }
        val handled = super.dispatchTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            parent?.requestDisallowInterceptTouchEvent(false)
        }
        return handled
    }

    private fun updateQuery(value: TextFieldValue) {
        if (value.text.length > 255) return
        queryState = value
        queryChangedListener?.onQueryChanged(value.text)
    }

    @Composable
    override fun Content() {
        val light = !isSystemInDarkTheme()
        val base = Color(context.getColor(R.color.screen_background))
        val glass = base.copy(alpha = if (light) 0.86f else 0.82f)
        val text = Color(context.getColor(R.color.text_primary))
        val secondary = Color(context.getColor(R.color.text_secondary))
        val frameNanos = remember { mutableLongStateOf(0L) }
        val headerLocation = remember { IntArray(2) }
        val sourceLocation = remember { IntArray(2) }
        val sourceView = backdropViewState
        // 取样只录入离屏图层，不把未经处理的快照再次盖到页面上。
        val backdrop = rememberLayerBackdrop(onDraw = {
            frameNanos.longValue
            drawRect(base)
            if (sourceView != null && sourceView.isAttachedToWindow && sourceView.isShown) {
                this@HomeGlassHeaderView.getLocationInWindow(headerLocation)
                sourceView.getLocationInWindow(sourceLocation)
                drawIntoCanvas { canvas ->
                    val native = canvas.nativeCanvas
                    val saved = native.save()
                    native.clipRect(0f, 0f, size.width, size.height)
                    native.translate(
                        (sourceLocation[0] - headerLocation[0] - sourceView.scrollX).toFloat(),
                        (sourceLocation[1] - headerLocation[1] - sourceView.scrollY).toFloat()
                    )
                    sourceView.draw(native)
                    native.restoreToCount(saved)
                }
            }
        })
        val focusRequester = remember { FocusRequester() }
        val focusManager = LocalFocusManager.current
        val keyboard = LocalSoftwareKeyboardController.current
        LaunchedEffect(focusRequestedState) {
            if (focusRequestedState) {
                focusRequester.requestFocus()
                keyboard?.show()
            } else {
                focusManager.clearFocus(force = true)
                keyboard?.hide()
            }
        }
        LaunchedEffect(renderingEnabledState, sourceView) {
            while (renderingEnabledState && sourceView != null) {
                withFrameNanos { frameNanos.longValue = it }
            }
        }

        val statusPadding = with(LocalDensity.current) {
            (if (brandModeState) statusBarInsetState else 0).toDp()
        }
        val headerHeight = statusPadding + if (categoryModeState) 140.dp else if (!brandModeState) 56.dp else if (compactState) 60.dp else 76.dp
        val fadeStartDp = statusPadding + if (categoryModeState) 96.dp else if (compactState) 24.dp else 28.dp
        Box(Modifier.fillMaxWidth().height(headerHeight).clipToBounds()) {
            Box(Modifier.matchParentSize().layerBackdrop(backdrop))
            // 与实验室一致：同一底层取样经过模糊后，用 smoothstep 平滑淡出。
            // 底色也在 shader 内一起混合，不另画一块会在末端形成分界的背景。
            ProgressiveGlassSurface(Modifier.matchParentSize(), backdrop, base, glass,
                fadeStartDp, frameNanos, brandModeState)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = statusPadding)
                    .height(56.dp)
                    .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (brandModeState) {
                    BasicText(
                        text = brandTitleState,
                        style = TextStyle(color = text, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold),
                        maxLines = 1
                    )
                } else if (expandedState) {
                    GlassAction(
                        icon = R.drawable.ic_back,
                        description = stringResource(R.string.back),
                        backdrop = backdrop,
                        frameNanos = frameNanos,
                        glass = glass,
                        tint = text,
                        onClick = { searchBackClickListener?.onClick(this@HomeGlassHeaderView) }
                    )
                } else {
                    AndroidView(
                        factory = { logoView },
                        modifier = Modifier.size(logoSizeState.dp)
                    )
                }

                Spacer(Modifier.width(12.dp))
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                  GlassSearchField(
                    modifier = Modifier.widthIn(max = 240.dp).fillMaxWidth().height(36.dp),
                    value = queryState,
                    hint = hintState,
                    backdrop = backdrop,
                    glass = glass,
                    text = text,
                    secondary = secondary,
                    focusRequester = focusRequester,
                    onValueChange = ::updateQuery,
                    onFocusChanged = { focused ->
                        if (searchFocusedState != focused) {
                            searchFocusedState = focused
                            if (focused) focusRequestedState = true
                            searchFocusChangedListener?.onSearchFocusChanged(focused)
                        }
                    },
                    onSubmit = { searchSubmittedListener?.onSearchSubmitted() }
                  )
                }
                Spacer(Modifier.width(12.dp))

                if (brandModeState) {
                    val searching = expandedState || searchFocusedState
                    GlassAction(
                        icon = if (searching) searchIconState else R.drawable.ic_action_history,
                        description = stringResource(if (searching) R.string.search_keyword else R.string.app_history),
                        backdrop = backdrop,
                        frameNanos = frameNanos,
                        glass = glass,
                        tint = text,
                        onClick = {
                            if (searching) searchClickListener?.onClick(this@HomeGlassHeaderView)
                            else historyClickListener?.onClick(this@HomeGlassHeaderView)
                        }
                    )
                } else {
                    GlassAction(
                        icon = searchIconState,
                        description = stringResource(R.string.search_keyword),
                        backdrop = backdrop,
                        frameNanos = frameNanos,
                        glass = glass,
                        tint = text,
                        onClick = { searchClickListener?.onClick(this@HomeGlassHeaderView) }
                    )
                }

                if (!brandModeState && !expandedState) {
                    Spacer(Modifier.width(8.dp))
                    GlassAction(
                        icon = R.drawable.ic_action_keep,
                        description = stringResource(R.string.app_keep),
                        backdrop = backdrop,
                        frameNanos = frameNanos,
                        glass = glass,
                        tint = text,
                        onClick = { keepClickListener?.onClick(this@HomeGlassHeaderView) }
                    )
                    Spacer(Modifier.width(8.dp))
                    GlassAction(
                        icon = R.drawable.ic_action_history,
                        description = stringResource(R.string.app_history),
                        backdrop = backdrop,
                        frameNanos = frameNanos,
                        glass = glass,
                        tint = text,
                        onClick = { historyClickListener?.onClick(this@HomeGlassHeaderView) }
                    )
                }
            }
        }
    }

    @Composable
    private fun GlassSearchField(
        modifier: Modifier,
        value: TextFieldValue,
        hint: String,
        backdrop: Backdrop,
        glass: Color,
        text: Color,
        secondary: Color,
        focusRequester: FocusRequester,
        onValueChange: (TextFieldValue) -> Unit,
        onFocusChanged: (Boolean) -> Unit,
        onSubmit: () -> Unit
    ) {
        val surface = if (brandModeState) {
            Modifier.drawBackdrop(
                backdrop = backdrop,
                shape = { Capsule() },
                effects = { blur(8.dp.toPx()) },
                onDrawSurface = { drawRect(glass) }
            )
        } else {
            Modifier.drawBackdrop(
                backdrop = backdrop,
                shape = { Capsule() },
                effects = {
                    vibrancy()
                    blur(3.dp.toPx())
                    lens(12.dp.toPx(), 24.dp.toPx(), depthEffect = true)
                },
                onDrawSurface = { drawRect(glass) }
            )
        }
        Box(
            modifier
                .onGloballyPositioned { updateSearchBounds(it.boundsInWindow()) }
                .then(surface)
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .clipToBounds()
                    .focusRequester(focusRequester)
                    .onFocusChanged { onFocusChanged(it.isFocused) },
                textStyle = TextStyle(color = text, fontSize = 14.sp, fontWeight = FontWeight.Normal),
                cursorBrush = SolidColor(text),
                singleLine = true,
                maxLines = 1,
                minLines = 1,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
                decorationBox = { innerTextField ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (value.text.isEmpty()) {
                            BasicText(
                                text = hint,
                                style = TextStyle(color = secondary, fontSize = 14.sp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        innerTextField()
                    }
                }
            )
        }
    }

    private fun updateSearchBounds(bounds: Rect) {
        val next = android.graphics.Rect(
            bounds.left.roundToInt(),
            bounds.top.roundToInt(),
            bounds.right.roundToInt(),
            bounds.bottom.roundToInt()
        )
        if (searchBounds == next) return
        searchBounds = next
        searchBoundsChangedListener?.onSearchBoundsChanged(next.left, next.top, next.right, next.bottom)
    }

    @Composable
    private fun GlassAction(
        @DrawableRes icon: Int,
        description: String,
        backdrop: Backdrop,
        frameNanos: androidx.compose.runtime.LongState,
        glass: Color,
        tint: Color,
        onClick: () -> Unit
    ) {
        if (brandModeState) {
            Box(
                Modifier.size(36.dp)
                    .drawBackdrop(
                        backdrop = backdrop, shape = { Capsule() },
                        effects = { blur(8.dp.toPx()) },
                        onDrawSurface = { drawRect(glass) }
                    )
                    .clickable(role = Role.Button, onClick = onClick),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(icon), contentDescription = description,
                    colorFilter = ColorFilter.tint(tint), modifier = Modifier.size(20.dp)
                )
            }
            return
        }
        LiquidButton(
            onClick = onClick,
            backdrop = backdrop,
            frameNanos = frameNanos,
            surfaceColor = glass,
            dragResponse = 0.42f,
            modifier = Modifier.size(36.dp)
        ) {
            Crossfade(
                targetState = icon,
                animationSpec = tween(140),
                label = "homeHeaderActionIcon"
            ) { resource ->
                Image(
                    painter = painterResource(resource),
                    contentDescription = description,
                    colorFilter = ColorFilter.tint(tint),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
