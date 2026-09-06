package com.fongmi.android.tv.ui.custom

import android.content.Context
import android.graphics.Outline
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.unit.dp
import com.fongmi.android.tv.R
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.shapes.RoundedRectangle

/** 搜索联想列表使用与悬浮底栏相同的实时模糊、折射和表面颜色。 */
class LiquidGlassSuggestionPanelView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val glassBackground = SuggestionGlassBackgroundView(context)
    private val cornerRadius = 16f * resources.displayMetrics.density

    init {
        setBackgroundColor(android.graphics.Color.TRANSPARENT)
        // 背景采样层本身是矩形画布，必须连同列表内容一起裁成面板轮廓；
        // 只给玻璃效果传圆角不会裁掉它下面露出的黑色直角。
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                if (view.width > 0 && view.height > 0) {
                    outline.setRoundRect(0, 0, view.width, view.height, cornerRadius)
                }
            }
        }
        clipToOutline = true
        clipChildren = true
        clipToPadding = true
        addView(
            glassBackground,
            0,
            LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        invalidateOutline()
    }

    fun setBackdropView(view: View?) {
        glassBackground.setBackdropView(view)
    }

    fun setRenderingEnabled(enabled: Boolean) {
        glassBackground.setRenderingEnabled(enabled)
    }

    /** 背景层不参与 wrap_content 高度，面板高度只由实际推荐行数决定。 */
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        var desiredWidth = paddingLeft + paddingRight
        var desiredHeight = paddingTop + paddingBottom
        for (index in 1 until childCount) {
            val child = getChildAt(index)
            if (child.visibility == View.GONE) continue
            measureChildWithMargins(child, widthMeasureSpec, 0, heightMeasureSpec, 0)
            val params = child.layoutParams as ViewGroup.MarginLayoutParams
            desiredWidth = maxOf(desiredWidth, child.measuredWidth + params.leftMargin + params.rightMargin)
            desiredHeight = maxOf(desiredHeight, child.measuredHeight + params.topMargin + params.bottomMargin)
        }
        val measuredWidth = resolveSize(desiredWidth, widthMeasureSpec)
        val measuredHeight = resolveSize(desiredHeight, heightMeasureSpec)
        setMeasuredDimension(measuredWidth, measuredHeight)
        glassBackground.measure(
            View.MeasureSpec.makeMeasureSpec(measuredWidth, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(measuredHeight, View.MeasureSpec.EXACTLY)
        )
    }

    private class SuggestionGlassBackgroundView(context: Context) : AbstractComposeView(context) {

        private var backdropViewState by mutableStateOf<View?>(null)
        private var renderingEnabledState by mutableStateOf(false)

        init {
            isClickable = false
            isFocusable = false
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
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
            val panelLocation = remember { IntArray(2) }
            val sourceLocation = remember { IntArray(2) }
            val light = !isSystemInDarkTheme()
            val containerColor = if (light) {
                Color(0xFFF8F8FA).copy(alpha = 0.86f)
            } else {
                Color(0xFF161618).copy(alpha = 0.82f)
            }

            LaunchedEffect(renderingEnabledState, sourceView) {
                while (renderingEnabledState && sourceView != null) {
                    withFrameNanos { frameNanos.longValue = it }
                }
            }

            Box(Modifier.fillMaxSize()) {
                Canvas(Modifier.fillMaxSize().layerBackdrop(backdrop)) {
                    frameNanos.longValue
                    drawRect(Color(context.getColor(R.color.screen_background)))
                    if (sourceView != null && sourceView.isAttachedToWindow) {
                        this@SuggestionGlassBackgroundView.getLocationInWindow(panelLocation)
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
                        .drawBackdrop(
                            backdrop = backdrop,
                            shape = { RoundedRectangle(16.dp) },
                            effects = {
                                vibrancy()
                                blur(8.dp.toPx())
                                lens(24.dp.toPx(), 24.dp.toPx())
                            },
                            onDrawBehind = { frameNanos.longValue },
                            onDrawSurface = { drawRect(containerColor) }
                        )
                )
            }
        }
    }
}
