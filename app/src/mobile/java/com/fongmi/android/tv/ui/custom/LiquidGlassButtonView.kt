package com.fongmi.android.tv.ui.custom

import android.content.Context
import android.util.AttributeSet
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.unit.dp
import com.fongmi.android.tv.ui.custom.liquid.LiquidButton
import com.kyant.backdrop.backdrops.rememberCanvasBackdrop

/**
 * 可放在传统 XML 布局里的单个液态玻璃按钮表面。
 *
 * 图标由 XML 叠放，表面本身只负责玻璃渲染和触摸形变，因此搜索、返回等按钮可以共用
 * 完全相同的视觉和交互。
 */
class LiquidGlassButtonView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AbstractComposeView(context, attrs, defStyleAttr) {

    init {
        setBackgroundColor(android.graphics.Color.TRANSPARENT)
        clipToPadding = false
    }

    @Composable
    override fun Content() {
        val light = !isSystemInDarkTheme()
        val surface = if (light) Color.White.copy(alpha = 0.36f)
        else Color(0xFF202024).copy(alpha = 0.42f)
        val frameNanos = remember { mutableLongStateOf(0L) }
        val backdrop = rememberCanvasBackdrop { }

        LiquidButton(
            onClick = { performClick() },
            backdrop = backdrop,
            frameNanos = frameNanos,
            surfaceColor = surface,
            modifier = Modifier
                .fillMaxSize()
                // 给按压放大和拖拽形变留出空间，避免被 ComposeView 的方形边界裁切。
                .padding(4.dp)
        ) {}
    }
}
