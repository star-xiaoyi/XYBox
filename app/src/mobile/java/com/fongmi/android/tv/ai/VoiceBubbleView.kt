package com.fongmi.android.tv.ai

import android.content.Context
import android.graphics.*
import android.os.Build
import android.text.TextPaint
import android.text.TextUtils
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import kotlin.math.*

/** Blue recording surface with a blurred upper transition and a dimmed page behind it. */
class VoiceBubbleView(context: Context, source: View) : View(context) {
    private val density = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private var target = 0f
    private var level = 0f
    private var words = ""
    private var cancelled = false
    private var ending = false
    private val bottomInset = ViewCompat.getRootWindowInsets(source)
        ?.getInsets(WindowInsetsCompat.Type.systemBars())?.bottom ?: 0
    // Record before attachment: never sample this overlay into its own backdrop.
    // Hardware recording supports Compose/glass without a bitmap readback.
    private val scene: RenderNode? = if (Build.VERSION.SDK_INT >= 31 && source.isHardwareAccelerated && source.width > 0) {
        try {
            RenderNode("voice-backdrop").apply {
                setPosition(0, 0, source.width, source.height)
                val recording = beginRecording(source.width, source.height)
                try { source.draw(recording) } finally { endRecording() }
                setRenderEffect(RenderEffect.createBlurEffect(18 * density, 18 * density, Shader.TileMode.CLAMP))
            }
        } catch (_: Exception) { null }
    } else null

    fun update(text: String, cancel: Boolean, finalizing: Boolean = false) {
        words = text; cancelled = cancel; ending = finalizing; invalidate()
    }
    fun setLevel(value: Float) { target = value.coerceIn(0f, 1f) }
    override fun onDraw(canvas: Canvas) {
        level += (target - level) * if (target > level) .38f else .12f
        val surfaceHeight = min(360 * density + bottomInset, height * .72f)
        val top = height - surfaceHeight
        val opaqueY = top + surfaceHeight * .55f
        canvas.drawColor(0x59000000)
        if (Build.VERSION.SDK_INT >= 31 && canvas.isHardwareAccelerated && scene != null) {
            val layer = canvas.saveLayer(0f, top, width.toFloat(), height.toFloat(), null)
            canvas.drawRenderNode(scene)
            canvas.drawColor(0x59000000)
            paint.shader = LinearGradient(0f, top, 0f, opaqueY,
                Color.TRANSPARENT, Color.WHITE, Shader.TileMode.CLAMP)
            paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
            canvas.drawRect(0f, top, width.toFloat(), height.toFloat(), paint)
            paint.shader = null; paint.xfermode = null
            canvas.restoreToCount(layer)
        }
        paint.style = Paint.Style.FILL; paint.alpha = 255
        val surfaceColor = if (cancelled) 0xFFE34D59.toInt() else 0xFF1677FF.toInt()
        paint.shader = LinearGradient(0f, top, 0f, opaqueY,
            intArrayOf(surfaceColor and 0x00FFFFFF, (surfaceColor and 0x00FFFFFF) or 0x55000000, surfaceColor),
            floatArrayOf(0f, .45f, 1f), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, top, width.toFloat(), height.toFloat(), paint); paint.shader = null

        val centerY = height - bottomInset - min(64 * density, surfaceHeight * .18f)
        val time = android.os.SystemClock.uptimeMillis() / 150.0
        paint.color = Color.WHITE; paint.strokeCap = Paint.Cap.ROUND; paint.strokeWidth = 2.6f * density
        val count = 39; val step = min(width * .76f / count, 7f * density)
        for (i in 0 until count) {
            val envelope = sin(PI * (i + 1) / (count + 1)).toFloat()
            val activity = (.4 + .6 * abs(sin(time + i * .85))).toFloat()
            val amplitude = density * (2.5f + 35f * level * envelope * activity)
            val x = width / 2f + (i - (count - 1) / 2f) * step
            paint.alpha = (100 + 155 * envelope).toInt()
            canvas.drawLine(x, centerY - amplitude / 2, x, centerY + amplitude / 2, paint)
        }
        paint.alpha = 255
        val label = when {
            cancelled -> "上滑取消"
            words.isNotBlank() -> words
            ending -> "正在确认识别结果…"
            else -> "请说话，松手发送"
        }.replace('\n', ' ')
        textPaint.color = Color.WHITE; textPaint.textSize = 17f * density; textPaint.textAlign = Paint.Align.CENTER
        val visibleText = TextUtils.ellipsize(label, textPaint, max(1f, width - 48 * density), TextUtils.TruncateAt.START)
        val textY = centerY - min(68 * density, surfaceHeight * .20f)
        canvas.drawText(visibleText.toString(), width / 2f, textY, textPaint)
        if (isAttachedToWindow && isShown) postInvalidateOnAnimation()
    }
    override fun onDetachedFromWindow() {
        if (Build.VERSION.SDK_INT >= 29) scene?.discardDisplayList()
        super.onDetachedFromWindow()
    }
}
