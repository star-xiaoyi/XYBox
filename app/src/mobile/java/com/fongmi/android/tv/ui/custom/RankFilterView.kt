package com.fongmi.android.tv.ui.custom

import android.content.Context
import android.graphics.Canvas
import android.graphics.Path
import android.util.AttributeSet
import androidx.recyclerview.widget.RecyclerView

/** Clip scrolling chips to the same top corners as shape_rank_filter_surface. */
class RankFilterView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : RecyclerView(context, attrs, defStyleAttr) {
    private val surfaceClip = Path()

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val radius = minOf(24f * resources.displayMetrics.density, w / 2f, h / 2f)
        surfaceClip.reset()
        surfaceClip.addRoundRect(
            0f, 0f, w.toFloat(), h.toFloat(),
            floatArrayOf(radius, radius, radius, radius, 0f, 0f, 0f, 0f),
            Path.Direction.CW
        )
    }

    override fun draw(canvas: Canvas) {
        val saved = canvas.save()
        try {
            canvas.clipPath(surfaceClip)
            super.draw(canvas)
        } finally {
            canvas.restoreToCount(saved)
        }
    }
}
