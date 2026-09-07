package com.fongmi.android.tv.ui.custom

import android.content.Context
import android.util.AttributeSet
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.fongmi.android.tv.ui.custom.liquid.LiquidSlider
import com.fongmi.android.tv.utils.ThemeUtil
import com.kyant.backdrop.backdrops.rememberCanvasBackdrop
import kotlin.math.round

/** Java/XML 可直接使用的设置页同款液态滑块。 */
class LiquidGlassSliderView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AbstractComposeView(context, attrs, defStyleAttr) {

    fun interface OnValueChangeListener {
        fun onValueChanged(value: Float)
    }

    private var valueState by mutableFloatStateOf(0f)
    private var minState by mutableFloatStateOf(0f)
    private var maxState by mutableFloatStateOf(1f)
    private var stepState by mutableFloatStateOf(0f)
    private var listener: OnValueChangeListener? = null

    init {
        setBackgroundColor(android.graphics.Color.TRANSPARENT)
    }

    fun setRange(min: Float, max: Float, step: Float) {
        minState = min
        maxState = max.coerceAtLeast(min)
        stepState = step.coerceAtLeast(0f)
        valueState = snap(valueState)
    }

    fun setValue(value: Float) {
        valueState = snap(value)
    }

    fun getValue(): Float = valueState

    fun setOnValueChangeListener(listener: OnValueChangeListener?) {
        this.listener = listener
    }

    private fun update(value: Float) {
        val snapped = snap(value)
        if (snapped == valueState) return
        valueState = snapped
        listener?.onValueChanged(snapped)
    }

    private fun snap(value: Float): Float {
        val bounded = value.coerceIn(minState, maxState)
        if (stepState <= 0f) return bounded
        return (minState + round((bounded - minState) / stepState) * stepState)
            .coerceIn(minState, maxState)
    }

    @Composable
    override fun Content() {
        val backdrop = rememberCanvasBackdrop { }
        val accent = Color(ContextCompat.getColor(context, ThemeUtil.getAccentColorResource()))
        Box(
            modifier = Modifier.fillMaxSize().padding(horizontal = 2.dp),
            contentAlignment = Alignment.Center
        ) {
            LiquidSlider(
                value = { valueState },
                onValueChange = ::update,
                valueRange = minState..maxState,
                visibilityThreshold = if (stepState > 0f) stepState / 20f else 0.001f,
                backdrop = backdrop,
                accentColor = accent,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
