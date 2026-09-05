package com.fongmi.android.tv.ui.custom

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.widget.Checkable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.fongmi.android.tv.ui.custom.liquid.LiquidToggle
import com.fongmi.android.tv.utils.ThemeUtil
import com.kyant.backdrop.backdrops.rememberCanvasBackdrop

/** 把开源项目的可拖动 LiquidToggle 接入传统 XML/ViewBinding 页面。 */
class LiquidGlassSwitch @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AbstractComposeView(context, attrs, defStyleAttr), Checkable {

    private var checkedState by mutableStateOf(false)

    override fun isChecked(): Boolean = checkedState

    override fun setChecked(checked: Boolean) {
        checkedState = checked
    }

    override fun toggle() {
        setChecked(!checkedState)
    }

    override fun performClick(): Boolean {
        toggle()
        return dispatchClick()
    }

    private fun dispatchClick(): Boolean = super.performClick()

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            parent?.requestDisallowInterceptTouchEvent(true)
        }
        val handled = super.dispatchTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            parent?.requestDisallowInterceptTouchEvent(false)
        }
        return handled
    }

    @Composable
    override fun Content() {
        val light = !isSystemInDarkTheme()
        val accent = Color(ContextCompat.getColor(context, ThemeUtil.getAccentColorResource()))
        val backdrop = rememberCanvasBackdrop { }

        LiquidToggle(
            selected = { checkedState },
            onSelect = { selected ->
                if (checkedState != selected) {
                    checkedState = selected
                    dispatchClick()
                }
            },
            backdrop = backdrop,
            accentColor = accent,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
        )
    }
}
