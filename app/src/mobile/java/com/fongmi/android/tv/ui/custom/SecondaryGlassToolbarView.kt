package com.fongmi.android.tv.ui.custom

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
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
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.fongmi.android.tv.R
import com.fongmi.android.tv.ui.custom.liquid.LiquidButton
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawPlainBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.shapes.RoundedRectangle

/**
 * 首页二级页面共用顶栏。
 *
 * 与设置二级页相同，状态栏内边距和全部按钮都放在一张完整 Compose 画布中；按钮统一为
 * 首页顶栏的 36dp，不再各自创建带矩形边界的小 Backdrop。
 */
class SecondaryGlassToolbarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AbstractComposeView(context, attrs, defStyleAttr) {

    private var profileStyleState by mutableStateOf(false)
    fun setProfileStyle(enabled: Boolean) { profileStyleState = enabled }
    private var backVisibleState by mutableStateOf(true)
    private var explicitTopInset by mutableStateOf<Int?>(null)
    fun setTopInsetPixels(top: Int) { explicitTopInset = top }
    fun setBackVisible(visible: Boolean) { backVisibleState = visible }
    private var immersiveBottom = false
    private var progressiveSurfaceVisibleState by mutableStateOf(true)

    fun setProgressiveSurfaceVisible(visible: Boolean) { progressiveSurfaceVisibleState = visible }

    fun setImmersiveBottom(enabled: Boolean) { immersiveBottom = enabled }

    private var titleState by mutableStateOf("")
    private var titleClickListenerState by mutableStateOf<View.OnClickListener?>(null)
    private var primaryIconState by mutableIntStateOf(R.drawable.ic_action_sync)
    private var primaryDescriptionState by mutableStateOf("")
    private var primaryVisibleState by mutableStateOf(true)
    private var primaryEnabledState by mutableStateOf(true)
    private var secondaryIconState by mutableIntStateOf(R.drawable.ic_action_delete)
    private var secondaryDescriptionState by mutableStateOf("")
    private var secondaryVisibleState by mutableStateOf(false)
    private var secondaryEnabledState by mutableStateOf(true)
    private var backdropViewState by mutableStateOf<View?>(null)
    private var renderingEnabledState by mutableStateOf(false)
    private var backClickListener: View.OnClickListener? = null
    private var primaryClickListener: View.OnClickListener? = null
    private var secondaryClickListener: View.OnClickListener? = null

    init {
        setBackgroundColor(android.graphics.Color.TRANSPARENT)
        clipToPadding = false
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        clipBounds = android.graphics.Rect(0, 0, w, h)
    }

    fun setTitle(@StringRes resource: Int) {
        titleState = context.getString(resource)
    }

    fun setTitle(title: CharSequence?) {
        titleState = title?.toString().orEmpty()
    }

    fun setTitleClickListener(listener: View.OnClickListener?) {
        titleClickListenerState = listener
    }

    fun setPrimaryAction(@DrawableRes icon: Int, @StringRes description: Int) {
        primaryIconState = icon
        primaryDescriptionState = context.getString(description)
    }

    fun setPrimaryActionIcon(@DrawableRes icon: Int) {
        primaryIconState = icon
    }

    fun setPrimaryActionVisible(visible: Boolean) {
        primaryVisibleState = visible
    }

    fun setPrimaryActionEnabled(enabled: Boolean) {
        primaryEnabledState = enabled
    }

    fun setSecondaryActionIcon(@DrawableRes icon: Int) {
        secondaryIconState = icon
    }
    fun setSecondaryAction(@DrawableRes icon: Int, @StringRes description: Int) {
        secondaryIconState = icon
        secondaryDescriptionState = context.getString(description)
    }

    fun setBackdropView(view: View?) { backdropViewState = view }
    fun setRenderingEnabled(enabled: Boolean) { renderingEnabledState = enabled }

    fun setSecondaryActionVisible(visible: Boolean) {
        secondaryVisibleState = visible
    }

    fun setSecondaryActionEnabled(enabled: Boolean) {
        secondaryEnabledState = enabled
    }

    /**
     * 让内容层铺在顶栏后面供玻璃实时采样，并为实际列表补足状态栏、顶栏和导航栏内边距。
     */
    @JvmOverloads
    fun attachContent(backdropView: View, scrollContent: View, insetTop: Boolean = true) {
        backdropViewState = backdropView
        renderingEnabledState = true
        val start = scrollContent.paddingLeft
        val end = scrollContent.paddingRight
        val bottom = scrollContent.paddingBottom
        val contentTop = (72 * resources.displayMetrics.density).toInt()
        ViewCompat.setOnApplyWindowInsetsListener(scrollContent) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            if (profileStyleState) explicitTopInset = bars.top
            view.setPadding(start, if (insetTop) bars.top + contentTop else 0, end, (if (immersiveBottom) 0 else bars.bottom) + bottom)
            // 同时限定 Android View 的实际高度，避免取样画布因父布局约束占满屏幕。
            val headerHeight = bars.top + (72 * resources.displayMetrics.density + 0.5f).toInt()
            layoutParams?.let { params ->
                if (params.height != headerHeight) {
                    params.height = headerHeight
                    layoutParams = params
                }
            }
            insets
        }
        ViewCompat.requestApplyInsets(scrollContent)
    }

    fun setBackClickListener(listener: View.OnClickListener?) {
        backClickListener = listener
    }

    fun setPrimaryActionClickListener(listener: View.OnClickListener?) {
        primaryClickListener = listener
    }

    fun setSecondaryActionClickListener(listener: View.OnClickListener?) {
        secondaryClickListener = listener
    }

    @Composable
    override fun Content() {
        val light = !isSystemInDarkTheme()
        val background = Color(context.getColor(R.color.screen_background))
        val glass = background.copy(alpha = if (light) 0.86f else 0.82f)
        val text = Color(context.getColor(R.color.text_primary))
        val frameNanos = remember { mutableLongStateOf(0L) }
        val toolbarLocation = remember { IntArray(2) }
        val sourceLocation = remember { IntArray(2) }
        val sourceView = backdropViewState
        // 页面取样只录入离屏图层，不能把整页的背景直接画到顶栏外。
        val backdrop = rememberLayerBackdrop(onDraw = {
            frameNanos.longValue
            drawRect(background)
            if (sourceView != null && sourceView.isAttachedToWindow && sourceView.isShown) {
                this@SecondaryGlassToolbarView.getLocationInWindow(toolbarLocation)
                sourceView.getLocationInWindow(sourceLocation)
                drawIntoCanvas { canvas ->
                    val native = canvas.nativeCanvas
                    val saved = native.save()
                    native.clipRect(0f, 0f, size.width, size.height)
                    native.translate(
                        (sourceLocation[0] - toolbarLocation[0] - sourceView.scrollX).toFloat(),
                        (sourceLocation[1] - toolbarLocation[1] - sourceView.scrollY).toFloat()
                    )
                    sourceView.draw(native)
                    native.restoreToCount(saved)
                }
            }
        })

        LaunchedEffect(renderingEnabledState, sourceView) {
            while (renderingEnabledState && sourceView != null) {
                withFrameNanos { frameNanos.longValue = it }
            }
        }

        val statusPadding = explicitTopInset?.let { (it / resources.displayMetrics.density).dp }
            ?: WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val base = background
        Box(Modifier.fillMaxWidth().height(statusPadding + 72.dp).clipToBounds()) {
            Box(Modifier.matchParentSize().layerBackdrop(backdrop))
            if (progressiveSurfaceVisibleState) {
                ProgressiveGlassSurface(Modifier.matchParentSize(), backdrop, base, glass, statusPadding + 28.dp, frameNanos)
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = statusPadding)
                    .height(MainPageHeaderStyle.rowHeight)
                    .padding(horizontal = MainPageHeaderStyle.horizontalPadding),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (backVisibleState) ToolbarAction(
                    icon = R.drawable.ic_back,
                    description = stringResource(R.string.back),
                    enabled = true,
                    backdrop = backdrop,
                    frameNanos = frameNanos,
                    glass = glass,
                    tint = text,
                    onClick = { backClickListener?.onClick(this@SecondaryGlassToolbarView) }
                )

                Box(Modifier.weight(1f).padding(start = if (profileStyleState && !backVisibleState) 0.dp else 12.dp)) {
                    Row(
                        Modifier.clickable(enabled = titleClickListenerState != null, role = Role.Button) {
                            titleClickListenerState?.onClick(this@SecondaryGlassToolbarView)
                        }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BasicText(
                            text = titleState,
                            modifier = Modifier.weight(1f, fill = false),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = TextStyle(color = text,
                                fontSize = if (profileStyleState) MainPageHeaderStyle.titleSize else 22.sp,
                                fontWeight = if (profileStyleState) MainPageHeaderStyle.titleWeight else FontWeight.Bold)
                        )
                        if (titleClickListenerState != null) {
                            Image(
                                painter = painterResource(R.drawable.ic_arrow_right),
                                contentDescription = null,
                                colorFilter = ColorFilter.tint(text),
                                modifier = Modifier.padding(start = 6.dp).size(16.dp).rotate(90f)
                            )
                        }
                    }
                }

                if (primaryVisibleState) {
                    ToolbarAction(
                        icon = primaryIconState,
                        description = primaryDescriptionState,
                        enabled = primaryEnabledState,
                        backdrop = backdrop,
                        frameNanos = frameNanos,
                        glass = glass,
                        tint = text,
                        onClick = { primaryClickListener?.onClick(this@SecondaryGlassToolbarView) }
                    )
                }
                if (secondaryVisibleState) {
                    Spacer(Modifier.width(8.dp))
                    ToolbarAction(
                        icon = secondaryIconState,
                        description = secondaryDescriptionState,
                        enabled = secondaryEnabledState,
                        backdrop = backdrop,
                        frameNanos = frameNanos,
                        glass = glass,
                        tint = text,
                        onClick = { secondaryClickListener?.onClick(this@SecondaryGlassToolbarView) }
                    )
                }
            }
        }
    }

    @Composable
    private fun ToolbarAction(
        @DrawableRes icon: Int,
        description: String,
        enabled: Boolean,
        backdrop: Backdrop,
        frameNanos: androidx.compose.runtime.LongState,
        glass: Color,
        tint: Color,
        onClick: () -> Unit
    ) {
        LiquidButton(
            onClick = { if (enabled) onClick() },
            backdrop = backdrop,
            frameNanos = frameNanos,
            surfaceColor = glass,
            dragResponse = 0.42f,
            modifier = Modifier.size(MainPageHeaderStyle.buttonSize).alpha(if (enabled) 1f else 0.45f)
        ) {
            Image(
                painter = painterResource(icon),
                contentDescription = description,
                colorFilter = ColorFilter.tint(tint),
                modifier = Modifier.size(MainPageHeaderStyle.iconSize)
            )
        }
    }
}
