package com.fongmi.android.tv.ui.custom

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
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

    private var titleState by mutableStateOf("")
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

    fun setTitle(@StringRes resource: Int) {
        titleState = context.getString(resource)
    }

    fun setTitle(title: CharSequence?) {
        titleState = title?.toString().orEmpty()
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

    fun setSecondaryActionVisible(visible: Boolean) {
        secondaryVisibleState = visible
    }

    fun setSecondaryActionEnabled(enabled: Boolean) {
        secondaryEnabledState = enabled
    }

    /**
     * 让内容层铺在顶栏后面供玻璃实时采样，并为实际列表补足状态栏、顶栏和导航栏内边距。
     */
    fun attachContent(backdropView: View, scrollContent: View) {
        backdropViewState = backdropView
        renderingEnabledState = true
        val start = scrollContent.paddingLeft
        val end = scrollContent.paddingRight
        val bottom = scrollContent.paddingBottom
        val contentTop = (72 * resources.displayMetrics.density).toInt()
        ViewCompat.setOnApplyWindowInsetsListener(scrollContent) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(start, bars.top + contentTop, end, bars.bottom + bottom)
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
        val glass = if (light) Color(0xFFF8F8FA).copy(alpha = 0.86f)
        else Color(0xFF161618).copy(alpha = 0.82f)
        val text = Color(context.getColor(R.color.text_primary))
        val frameNanos = remember { mutableLongStateOf(0L) }
        val backdrop = rememberLayerBackdrop()
        val toolbarLocation = remember { IntArray(2) }
        val sourceLocation = remember { IntArray(2) }
        val sourceView = backdropViewState

        LaunchedEffect(renderingEnabledState, sourceView) {
            while (renderingEnabledState && sourceView != null) {
                withFrameNanos { frameNanos.longValue = it }
            }
        }

        Box(Modifier.fillMaxWidth()) {
            Canvas(Modifier.matchParentSize().layerBackdrop(backdrop)) {
                frameNanos.longValue
                drawRect(Color(context.getColor(R.color.screen_background)))
                if (sourceView != null && sourceView.isAttachedToWindow) {
                    this@SecondaryGlassToolbarView.getLocationInWindow(toolbarLocation)
                    sourceView.getLocationInWindow(sourceLocation)
                    drawIntoCanvas { canvas ->
                        val nativeCanvas = canvas.nativeCanvas
                        val saveCount = nativeCanvas.save()
                        nativeCanvas.translate(
                            (sourceLocation[0] - toolbarLocation[0]).toFloat(),
                            (sourceLocation[1] - toolbarLocation[1]).toFloat()
                        )
                        sourceView.draw(nativeCanvas)
                        nativeCanvas.restoreToCount(saveCount)
                    }
                }
            }
            Box(
                Modifier
                    .matchParentSize()
                    .drawPlainBackdrop(
                        backdrop = backdrop,
                        shape = { RoundedRectangle(0.dp) },
                        effects = {
                            vibrancy()
                            blur(8.dp.toPx())
                            lens(24.dp.toPx(), 24.dp.toPx())
                        },
                        onDrawBehind = { frameNanos.longValue },
                        onDrawSurface = { drawRect(glass) }
                    )
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .height(64.dp)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ToolbarAction(
                    icon = R.drawable.ic_back,
                    description = stringResource(R.string.back),
                    enabled = true,
                    backdrop = backdrop,
                    frameNanos = frameNanos,
                    glass = glass,
                    tint = text,
                    onClick = { backClickListener?.onClick(this@SecondaryGlassToolbarView) }
                )

                BasicText(
                    text = titleState,
                    modifier = Modifier.weight(1f).padding(start = 12.dp),
                    style = TextStyle(color = text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                )

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
            modifier = Modifier.size(36.dp).alpha(if (enabled) 1f else 0.45f)
        ) {
            Image(
                painter = painterResource(icon),
                contentDescription = description,
                colorFilter = ColorFilter.tint(tint),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
