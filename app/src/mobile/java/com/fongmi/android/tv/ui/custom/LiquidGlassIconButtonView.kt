package com.fongmi.android.tv.ui.custom

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.fongmi.android.tv.R
import com.fongmi.android.tv.ui.custom.liquid.LiquidButton
import com.kyant.backdrop.backdrops.rememberCanvasBackdrop
import kotlin.math.max

/**
 * Native-layout compatible liquid glass icon button.
 *
 * It keeps the XML view's original dimensions while sharing the same Backdrop refraction and
 * free-direction drag response as the bottom navigation's round action button.
 */
class LiquidGlassIconButtonView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AbstractComposeView(context, attrs, defStyleAttr) {

    private var iconResourceState by mutableIntStateOf(0)
    private var iconBitmapState by mutableStateOf<ImageBitmap?>(null)
    private var iconTintState by mutableStateOf(Color.Unspecified)
    private var tintEnabledState by mutableStateOf(true)
    private var iconSizePxState by mutableStateOf(20f * resources.displayMetrics.density)

    init {
        setBackgroundColor(android.graphics.Color.TRANSPARENT)
        clipToPadding = false
        val array = context.obtainStyledAttributes(attrs, R.styleable.LiquidGlassIconButtonView)
        iconResourceState = array.getResourceId(R.styleable.LiquidGlassIconButtonView_glassIcon, 0)
        iconSizePxState = array.getDimension(
            R.styleable.LiquidGlassIconButtonView_glassIconSize,
            iconSizePxState
        )
        tintEnabledState = array.getBoolean(
            R.styleable.LiquidGlassIconButtonView_glassIconTintEnabled,
            true
        )
        if (array.hasValue(R.styleable.LiquidGlassIconButtonView_glassIconTint)) {
            iconTintState = Color(array.getColor(
                R.styleable.LiquidGlassIconButtonView_glassIconTint,
                android.graphics.Color.WHITE
            ))
        }
        array.recycle()
    }

    @Suppress("UNUSED_PARAMETER")
    fun setBackdropView(view: View?) {
        // Standalone buttons deliberately do not snapshot an ancestor View; see Content().
    }

    @Suppress("UNUSED_PARAMETER")
    fun setRenderingEnabled(enabled: Boolean) {
        // Kept for XML/Java call-site compatibility with the full-width glass containers.
    }

    fun setIconTintEnabled(enabled: Boolean) {
        tintEnabledState = enabled
    }

    fun setIconSizeDp(size: Float) {
        iconSizePxState = size * resources.displayMetrics.density
    }

    fun setImageResource(@DrawableRes resource: Int) {
        iconBitmapState = null
        iconResourceState = resource
    }

    fun setImageDrawable(drawable: Drawable?) {
        iconResourceState = 0
        iconBitmapState = drawable?.toSafeBitmap()?.asImageBitmap()
    }

    fun setImageBitmap(bitmap: Bitmap?) {
        iconResourceState = 0
        iconBitmapState = bitmap?.asImageBitmap()
    }

    @Composable
    override fun Content() {
        val light = !isSystemInDarkTheme()
        val surface = if (light) Color(0xFFF8F8FA).copy(alpha = 0.82f)
        else Color(0xFF161618).copy(alpha = 0.78f)
        val defaultTint = if (light) Color(0xFF1C1C1E) else Color.White
        val frameNanos = remember { mutableLongStateOf(0L) }
        val backdrop = rememberCanvasBackdrop {
            drawRect(
                Brush.linearGradient(
                    if (light) {
                        listOf(Color(0xFFF9F9FB), Color(0xFFDDE0E6), Color.White)
                    } else {
                        listOf(Color(0xFF27272B), Color(0xFF101012), Color(0xFF303035))
                    }
                )
            )
        }
        val iconSize = with(LocalDensity.current) { iconSizePxState.toDp() }

        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(0.dp)) {
                frameNanos.longValue
                // 独立小按钮不能在自身绘制期间反向 draw() 祖先工具栏：Android 16 会因
                // RenderNode 重入直接崩溃。这里提供中性明暗纹理给液态折射层；覆盖内容的
                // 设置顶栏和底栏仍从独立的兄弟 View 实时取样。
                drawRect(
                    Brush.linearGradient(
                        if (light) {
                            listOf(Color(0xFFF9F9FB), Color(0xFFDDE0E6), Color.White)
                        } else {
                            listOf(Color(0xFF27272B), Color(0xFF101012), Color(0xFF303035))
                        }
                    )
                )
            }

            LiquidButton(
                onClick = { performClick() },
                onLongClick = { performLongClick() },
                backdrop = backdrop,
                frameNanos = frameNanos,
                surfaceColor = surface,
                dragResponse = 0.28f,
                // 外层保留 4dp 动画安全区，实际玻璃圆仍保持 XML 时代的 36dp。
                modifier = Modifier.fillMaxSize().padding(4.dp)
            ) {
                val bitmap = iconBitmapState
                val resource = iconResourceState
                val painter = when {
                    bitmap != null -> BitmapPainter(bitmap)
                    resource != 0 -> painterResource(resource)
                    else -> null
                }
                painter?.let {
                    Image(
                        painter = it,
                        contentDescription = null,
                        modifier = Modifier
                            .size(iconSize)
                            .semantics {
                                this.contentDescription = this@LiquidGlassIconButtonView.contentDescription
                                    ?.toString().orEmpty()
                            },
                        colorFilter = if (tintEnabledState) {
                            ColorFilter.tint(iconTintState.takeIf { color -> color.isSpecified } ?: defaultTint)
                        } else {
                            null
                        }
                    )
                }
            }
        }
    }

    private fun Drawable.toSafeBitmap(): Bitmap {
        val fallback = (20f * resources.displayMetrics.density).toInt().coerceAtLeast(1)
        return toBitmap(max(intrinsicWidth, fallback), max(intrinsicHeight, fallback), Bitmap.Config.ARGB_8888)
    }
}
