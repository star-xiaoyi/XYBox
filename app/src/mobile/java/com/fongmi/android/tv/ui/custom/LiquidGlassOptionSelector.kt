package com.fongmi.android.tv.ui.custom

import android.content.Context
import android.util.AttributeSet
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.fongmi.android.tv.ui.custom.liquid.LiquidButton
import com.fongmi.android.tv.utils.ThemeUtil
import com.kyant.backdrop.backdrops.rememberCanvasBackdrop

/** 卡片内使用的紧凑液态选项条，用于图片尺寸等少量固定选项。 */
class LiquidGlassOptionSelector @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AbstractComposeView(context, attrs, defStyleAttr) {

    fun interface OnOptionSelectedListener {
        fun onOptionSelected(index: Int)
    }

    private var labelsState by mutableStateOf(emptyList<String>())
    private var selectedIndexState by mutableIntStateOf(0)
    private var listener: OnOptionSelectedListener? = null

    fun setOptions(labels: Array<String>) {
        labelsState = labels.toList()
        selectedIndexState = selectedIndexState.coerceIn(0, labelsState.lastIndex.coerceAtLeast(0))
    }

    fun setSelectedIndex(index: Int) {
        selectedIndexState = index.coerceIn(0, labelsState.lastIndex.coerceAtLeast(0))
    }

    fun setOnOptionSelectedListener(listener: OnOptionSelectedListener?) {
        this.listener = listener
    }

    private fun select(index: Int) {
        if (index == selectedIndexState) return
        selectedIndexState = index
        listener?.onOptionSelected(index)
    }

    @Composable
    override fun Content() {
        val labels = labelsState
        if (labels.isEmpty()) return

        val light = !isSystemInDarkTheme()
        val accent = Color(ContextCompat.getColor(context, ThemeUtil.getAccentColorResource()))
        val textColor = if (light) Color(0xFF1C1C1E) else Color.White
        val surface = if (light) Color.White.copy(alpha = 0.36f)
        else Color(0xFF202024).copy(alpha = 0.42f)
        val selectedTextColor = if (accent.luminance() > 0.55f) Color.Black else Color.White
        val frameNanos = remember { mutableLongStateOf(0L) }
        val backdrop = rememberCanvasBackdrop { }

        Row(
            modifier = Modifier.fillMaxSize().padding(2.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            labels.forEachIndexed { index, label ->
                val selected = index == selectedIndexState
                LiquidButton(
                    onClick = { select(index) },
                    backdrop = backdrop,
                    frameNanos = frameNanos,
                    tint = if (selected) accent else Color.Unspecified,
                    surfaceColor = if (selected) Color.Unspecified else surface,
                    modifier = Modifier.weight(1f).fillMaxHeight()
                ) {
                    BasicText(
                        text = label,
                        maxLines = 1,
                        style = TextStyle(
                            color = if (selected) selectedTextColor else textColor,
                            fontSize = 12.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                        )
                    )
                }
            }
        }
    }
}
