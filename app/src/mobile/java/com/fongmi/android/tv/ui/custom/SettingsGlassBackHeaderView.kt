package com.fongmi.android.tv.ui.custom

import android.content.Context
import android.util.AttributeSet
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fongmi.android.tv.R
import com.fongmi.android.tv.ui.custom.liquid.LiquidButton
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

/** Shared liquid-glass header for secondary settings pages. */
class SettingsGlassBackHeaderView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AbstractComposeView(context, attrs, defStyleAttr) {

    private var titleState by mutableStateOf("")

    init {
        setBackgroundColor(android.graphics.Color.TRANSPARENT)
        clipToPadding = false
    }

    fun setTitle(title: CharSequence?) {
        titleState = title?.toString().orEmpty()
    }

    @Composable
    override fun Content() {
        val light = !isSystemInDarkTheme()
        val background = if (light) Color(0xFFF2F2F7) else Color.Black
        val glass = if (light) Color.White.copy(alpha = 0.36f)
        else Color(0xFF18181B).copy(alpha = 0.42f)
        val text = if (light) Color(0xFF1C1C1E) else Color.White
        val frameNanos = remember { mutableLongStateOf(0L) }
        val backdrop = rememberLayerBackdrop()

        LaunchedEffect(Unit) {
            while (true) withFrameNanos { frameNanos.longValue = it }
        }

        Box(Modifier.fillMaxSize()) {
            Canvas(Modifier.fillMaxSize().layerBackdrop(backdrop)) {
                // 二级页内容从顶部栏下方继续绘制，半透明表面形成轻磨砂叠层。
                drawRect(background.copy(alpha = if (light) 0.84f else 0.78f))
            }

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LiquidButton(
                    onClick = { performClick() },
                    backdrop = backdrop,
                    frameNanos = frameNanos,
                    surfaceColor = glass,
                    dragResponse = 0.42f,
                    modifier = Modifier.size(40.dp)
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_back),
                        contentDescription = stringResource(R.string.back),
                        colorFilter = ColorFilter.tint(text),
                        modifier = Modifier.size(19.dp)
                    )
                }

                BasicText(
                    text = titleState,
                    modifier = Modifier.padding(start = 12.dp),
                    style = TextStyle(
                        color = text,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }
    }
}
