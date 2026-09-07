package com.fongmi.android.tv.ui.custom

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fongmi.android.tv.R
import com.fongmi.android.tv.ui.custom.liquid.LiquidButton
import com.kyant.backdrop.backdrops.rememberCanvasBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.lens
import com.kyant.shapes.RoundedRectangle

/** 简单删除确认框也使用单张液态画布，正文和两个按钮不再分层。 */
class LiquidGlassConfirmView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AbstractComposeView(context, attrs, defStyleAttr) {

    private var messageState by mutableStateOf("")
    private var cancelClickListener: View.OnClickListener? = null
    private var confirmClickListener: View.OnClickListener? = null

    init {
        setBackgroundColor(android.graphics.Color.TRANSPARENT)
        clipToPadding = false
        elevation = 20f * resources.displayMetrics.density
    }

    fun setMessage(message: CharSequence?) {
        messageState = message?.toString().orEmpty()
    }

    fun setCancelClickListener(listener: View.OnClickListener?) {
        cancelClickListener = listener
    }

    fun setConfirmClickListener(listener: View.OnClickListener?) {
        confirmClickListener = listener
    }

    @Composable
    override fun Content() {
        val light = !isSystemInDarkTheme()
        val panel = if (light) Color(0xFFF8F8FA).copy(alpha = 0.91f) else Color(0xFF18181B).copy(alpha = 0.93f)
        val glass = if (light) Color.White.copy(alpha = 0.62f) else Color(0xFF2C2C30).copy(alpha = 0.72f)
        val text = if (light) Color(0xFF1C1C1E) else Color.White
        val accent = Color(context.getColor(R.color.accent))
        val frameNanos = remember { mutableLongStateOf(0L) }
        val backdrop = rememberCanvasBackdrop {
            drawRect(
                Brush.linearGradient(
                    if (light) listOf(Color.White, Color(0xFFDDE0E6), Color(0xFFF9F9FB))
                    else listOf(Color(0xFF303035), Color(0xFF101012), Color(0xFF27272B))
                )
            )
        }

        Column(
            Modifier
                .fillMaxWidth()
                .padding(5.dp)
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { RoundedRectangle(28.dp) },
                    effects = {
                        colorControls(brightness = 0.10f, saturation = 0.66f)
                        blur(28.dp.toPx())
                        lens(12.dp.toPx(), 28.dp.toPx(), depthEffect = true)
                    },
                    onDrawSurface = { drawRect(panel) }
                )
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            BasicText(
                text = messageState,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                style = TextStyle(text, 16.sp, FontWeight.Medium, textAlign = TextAlign.Center, lineHeight = 23.sp)
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LiquidButton(
                    onClick = { cancelClickListener?.onClick(this@LiquidGlassConfirmView) },
                    backdrop = backdrop,
                    frameNanos = frameNanos,
                    surfaceColor = glass,
                    dragResponse = 0.34f,
                    modifier = Modifier.weight(1f).height(52.dp)
                ) {
                    BasicText(context.getString(R.string.dialog_negative), style = TextStyle(text, 14.sp, FontWeight.SemiBold))
                }
                LiquidButton(
                    onClick = { confirmClickListener?.onClick(this@LiquidGlassConfirmView) },
                    backdrop = backdrop,
                    frameNanos = frameNanos,
                    surfaceColor = glass,
                    dragResponse = 0.34f,
                    modifier = Modifier.weight(1f).height(52.dp)
                ) {
                    BasicText(context.getString(R.string.dialog_positive), style = TextStyle(accent, 14.sp, FontWeight.Bold))
                }
            }
        }
    }
}
